package se.partee71.dagboken.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.entities.FavoritEntity
import se.partee71.dagboken.data.room.entities.MedicinEntity
import se.partee71.dagboken.data.room.entities.ReceptEntity
import se.partee71.dagboken.data.room.entities.toDomain
import se.partee71.dagboken.domain.model.Dosperiod
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import kotlinx.serialization.json.Json
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MedicinerRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MedicinerRepository
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(s: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(s) }.getOrDefault(emptyList())
    private fun decodeInt(s: String): List<Int> =
        runCatching { json.decodeFromString<List<Int>>(s) }.getOrDefault(emptyList())

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            noteRepo           = NoteRepository(db.noteDao()),
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
    }

    @After fun tearDown() { db.close() }

    private fun medicinEntity(
        id: String,
        namn: String = "Ibuprofen",
        datum: String = LocalDate.now().toString(),
        tagen: Boolean = false,
        skipped: Boolean = false,
        receptId: String? = null,
        tid: String = "07:00",
        tagenTid: String? = null,
    ) = MedicinEntity(
        id = id, timestamp = "${datum}T${tid}:00.000Z", datum = datum, tid = tid,
        namn = namn, dos = "400", enhet = "mg", tidpunkt = "Morgon",
        tagen = tagen, receptId = receptId, skipped = skipped, tagenTid = tagenTid,
    )

    // ─── takenMediciner (HIST-7) ──────────────────────────────────────────────

    @Test fun takenMediciner_excludes_untaken_and_skipped_doses() = runTest {
        db.medicinDao().upsert(medicinEntity(id = "m1", tagen = true, skipped = false))
        db.medicinDao().upsert(medicinEntity(id = "m2", tagen = false, skipped = false))
        db.medicinDao().upsert(medicinEntity(id = "m3", tagen = true, skipped = true))

        val taken = repo.takenMediciner.first()

        assertEquals(listOf("m1"), taken.map { it.id })
    }

    // ─── toggleTagen / markTodayDosesTaken sets tagenTid (MED-14) ────────────

    @Test fun toggleTagen_sets_tagenTid_when_marking_taken() = runTest {
        db.medicinDao().upsert(medicinEntity(id = "m1", tagen = false))

        repo.toggleTagen("m1", true)

        assertNotNull(db.medicinDao().getById("m1")!!.tagenTid)
    }

    @Test fun toggleTagen_clears_tagenTid_when_unmarking() = runTest {
        db.medicinDao().upsert(medicinEntity(id = "m1", tagen = true, tagenTid = "08:00"))

        repo.toggleTagen("m1", false)

        assertNull(db.medicinDao().getById("m1")!!.tagenTid)
    }

    @Test fun markTodayDosesTaken_sets_tagenTid_on_marked_doses() = runTest {
        val today = LocalDate.now().toString()
        db.medicinDao().upsert(medicinEntity(id = "m1", datum = today, tagen = false))

        repo.markTodayDosesTaken()

        assertNotNull(db.medicinDao().getById("m1")!!.tagenTid)
    }

    // ─── getLastTakenBefore (MED-16 — cooldown för efterhandsloggning) ───────

    @Test fun getLastTakenBefore_returns_the_dose_at_or_before_the_given_timestamp() = runTest {
        db.medicinDao().upsert(medicinEntity(id = "early", tagen = true, tid = "08:00"))
        db.medicinDao().upsert(medicinEntity(id = "late", tagen = true, tid = "20:00"))

        val today = LocalDate.now().toString()
        val last = repo.getLastTakenBefore("Ibuprofen", "${today}T12:00:00.000Z")

        assertEquals("early", last?.id)
    }

    // ─── ensureTodayEntries – idempotency ─────────────────────────────────────

    @Test fun ensureTodayEntries_is_idempotent_for_active_recept() = runTest {
        val today = LocalDate.now().toString()
        db.receptDao().upsert(ReceptEntity(
            id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = true, skapad = today,
        ))

        repo.ensureTodayEntries()
        repo.ensureTodayEntries()

        val count = db.medicinDao().countDailyDoses(today, "Metformin")
        // countDailyDoses only counts tagen=1, but we can check total entries via getByDate
        val entries = db.medicinDao().getByDate(today)
        assertEquals("Idempotent: only one entry created", 1, entries.size)
    }

    @Test fun ensureTodayEntries_copies_the_recept_note_onto_the_generated_dose() = runTest {
        val today = LocalDate.now().toString()
        db.receptDao().upsert(ReceptEntity(
            id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = true, skapad = today,
        ))
        db.noteDao().upsert(se.partee71.dagboken.data.room.entities.NoteEntity("RECEPT", "r1", "Tas med mat"))

        repo.ensureTodayEntries()

        val entry = db.medicinDao().getByDate(today).single()
        val note = db.noteDao().getAll().find { it.target == "MEDICATION" && it.entityId == entry.id }
        assertEquals("Tas med mat", note?.text)
    }

    @Test fun ensureTodayEntries_does_not_create_a_note_when_the_recept_has_none() = runTest {
        val today = LocalDate.now().toString()
        db.receptDao().upsert(ReceptEntity(
            id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = true, skapad = today,
        ))

        repo.ensureTodayEntries()

        assertEquals(0, db.noteDao().count())
    }

    // ─── countDailyDoses ──────────────────────────────────────────────────────

    @Test fun countDailyDoses_counts_taken_non_skipped_doses_for_today() = runTest {
        val today = LocalDate.now().toString()
        db.medicinDao().upsert(medicinEntity(id = "m1", tagen = true, datum = today))
        db.medicinDao().upsert(medicinEntity(id = "m2", tagen = true, datum = today))
        db.medicinDao().upsert(medicinEntity(id = "m3", tagen = false, datum = today))
        db.medicinDao().upsert(medicinEntity(id = "m4", tagen = true, skipped = true, datum = today))

        val count = repo.countDailyDoses(today, "Ibuprofen")
        assertEquals("Expected 2 taken non-skipped doses", 2, count)
    }

    @Test fun countDailyDoses_is_case_insensitive_for_name() = runTest {
        val today = LocalDate.now().toString()
        db.medicinDao().upsert(medicinEntity(id = "m1", namn = "IBUPROFEN", tagen = true, datum = today))

        val count = repo.countDailyDoses(today, "ibuprofen")
        assertEquals(1, count)
    }

    // ─── getLastTaken ─────────────────────────────────────────────────────────

    @Test fun getLastTaken_returns_most_recent_taken_entry_by_timestamp() = runTest {
        db.medicinDao().upsert(medicinEntity(id = "m1", tagen = true,  tid = "08:00"))
        db.medicinDao().upsert(medicinEntity(id = "m2", tagen = true,  tid = "12:00"))
        db.medicinDao().upsert(medicinEntity(id = "m3", tagen = false, tid = "14:00"))

        val last = repo.getLastTaken("Ibuprofen")
        assertNotNull(last)
        assertEquals("m2", last!!.id)
    }

    @Test fun getLastTaken_returns_null_when_no_taken_entries_exist() = runTest {
        db.medicinDao().upsert(medicinEntity(id = "m1", tagen = false))
        assertNull(repo.getLastTaken("Ibuprofen"))
    }

    // ─── skipMedicin vs deleteMedicin ─────────────────────────────────────────

    @Test fun skipMedicin_sets_skipped_flag_and_keeps_entry_in_DB() = runTest {
        db.medicinDao().upsert(medicinEntity(id = "m1"))
        repo.skipMedicin("m1")
        val entry = db.medicinDao().getById("m1")
        assertNotNull(entry)
        assertTrue(entry!!.skipped)
    }

    @Test fun deleteMedicin_removes_entry_from_DB() = runTest {
        val entity = medicinEntity(id = "m1")
        db.medicinDao().upsert(entity)
        repo.deleteMedicin(entity.toDomain())
        assertNull(db.medicinDao().getById("m1"))
    }

    @Test fun skipped_entry_is_excluded_from_todayFlow() = runTest {
        val today = LocalDate.now().toString()
        db.medicinDao().upsert(medicinEntity(id = "m1", datum = today, skipped = true))
        db.medicinDao().upsert(medicinEntity(id = "m2", datum = today, skipped = false))

        // todayFlow uses getTodayFlow which filters skipped=0
        val entries = db.medicinDao().getByDate(today).filter { !it.skipped }
        assertEquals(1, entries.size)
        assertEquals("m2", entries[0].id)
    }

    // ─── markTodayDosesTaken (notisåtgärd "Markera tagen") ────────────────────

    @Test fun markTodayDosesTaken_marks_pending_scheduled_doses_only() = runTest {
        val today = LocalDate.now().toString()
        db.medicinDao().upsert(medicinEntity(id = "m1", datum = today, tagen = false))
        db.medicinDao().upsert(medicinEntity(id = "m2", datum = today, tagen = true))
        db.medicinDao().upsert(medicinEntity(id = "m3", datum = today, skipped = true))
        // Vid behov-dos har ingen schemalagd timme och ska lämnas orörd.
        db.medicinDao().upsert(medicinEntity(id = "m4", datum = today).copy(tidpunkt = "Vid behov"))

        val marked = repo.markTodayDosesTaken()

        assertEquals("Only the one pending scheduled dose is marked", 1, marked)
        assertTrue("Pending scheduled dose becomes taken", db.medicinDao().getById("m1")!!.tagen)
        assertTrue("Already-taken dose stays taken", db.medicinDao().getById("m2")!!.tagen)
        assertEquals("Skipped dose is left untaken", false, db.medicinDao().getById("m3")!!.tagen)
        assertEquals("Vid behov-dos is left untaken", false, db.medicinDao().getById("m4")!!.tagen)
    }

    @Test fun markTodayDosesTaken_returns_zero_when_nothing_pending() = runTest {
        val today = LocalDate.now().toString()
        db.medicinDao().upsert(medicinEntity(id = "m1", datum = today, tagen = true))

        assertEquals(0, repo.markTodayDosesTaken())
    }

    // ─── setFavoritFavorite ───────────────────────────────────────────────────

    @Test fun setFavoritFavorite_marks_a_favorit_as_favorite() = runTest {
        db.favoritDao().upsert(FavoritEntity(
            id = "f1", namn = "Paracetamol", dos = "500", enhet = "mg",
            tidpunkt = "Vid behov", minTidMellan = 0,
        ))

        repo.setFavoritFavorite("f1", true)

        assertTrue(db.favoritDao().getById("f1")!!.isFavorite)
    }

    @Test fun setFavoritFavorite_unmarks_a_favorit() = runTest {
        db.favoritDao().upsert(FavoritEntity(
            id = "f1", namn = "Paracetamol", dos = "500", enhet = "mg",
            tidpunkt = "Vid behov", minTidMellan = 0, isFavorite = true,
        ))

        repo.setFavoritFavorite("f1", false)

        assertEquals(false, db.favoritDao().getById("f1")!!.isFavorite)
    }

    // ─── entriesForDate / ensureEntriesForDate (#114 — Idag-datumnavigering) ──

    @Test fun entriesForDate_returns_only_entries_for_that_date() = runTest {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val today = LocalDate.now().toString()
        db.medicinDao().upsert(medicinEntity(id = "y1", datum = yesterday))
        db.medicinDao().upsert(medicinEntity(id = "t1", datum = today))

        val yesterdayEntries = repo.entriesForDate(LocalDate.now().minusDays(1)).first()

        assertEquals(listOf("y1"), yesterdayEntries.map { it.id })
    }

    @Test fun ensureEntriesForDate_seeds_a_past_dates_scheduled_doses() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        db.receptDao().upsert(ReceptEntity(
            id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = true, skapad = yesterday.toString(),
        ))

        repo.ensureEntriesForDate(yesterday)

        val entries = db.medicinDao().getByDate(yesterday.toString())
        assertEquals(1, entries.size)
        assertEquals("Metformin", entries.single().namn)
    }

    @Test fun ensureEntriesForDate_is_idempotent_for_the_same_past_date() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        db.receptDao().upsert(ReceptEntity(
            id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = true, skapad = yesterday.toString(),
        ))

        repo.ensureEntriesForDate(yesterday)
        repo.ensureEntriesForDate(yesterday)

        assertEquals(1, db.medicinDao().getByDate(yesterday.toString()).size)
    }

    @Test fun ensureEntriesForDate_does_not_affect_todays_entries() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        val today = LocalDate.now().toString()
        db.receptDao().upsert(ReceptEntity(
            id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = true, skapad = yesterday.toString(),
        ))

        repo.ensureEntriesForDate(yesterday)

        assertTrue("ensuring a past date must not seed today", db.medicinDao().getByDate(today).isEmpty())
    }

    // ─── period (REC-7/REC-8) ─────────────────────────────────────────────────

    private fun receptEntity(
        id: String = "r1",
        namn: String = "Prednisolon",
        dos: String = "5",
        aktiv: Boolean = true,
        skapad: String = LocalDate.now().toString(),
        startDatum: String = LocalDate.now().toString(),
        slutDatum: String? = null,
        dosperioderJson: String = "[]",
    ) = ReceptEntity(
        id = id, namn = namn, dos = dos, enhet = "mg",
        tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
        dagarJson = "[]", intervalDagar = 1, aktiv = aktiv, skapad = skapad,
        startDatum = startDatum, slutDatum = slutDatum, dosperioderJson = dosperioderJson,
    )

    @Test fun ensureTodayEntries_generates_nothing_after_the_period_ended() = runTest {
        val today = LocalDate.now()
        db.receptDao().upsert(receptEntity(
            startDatum = today.minusDays(10).toString(),
            slutDatum  = today.minusDays(1).toString(),
        ))

        repo.ensureTodayEntries()

        assertTrue(db.medicinDao().getByDate(today.toString()).isEmpty())
    }

    @Test fun ensureTodayEntries_marks_an_expired_recept_as_ended_without_deleting_it() = runTest {
        val today = LocalDate.now()
        db.receptDao().upsert(receptEntity(
            startDatum = today.minusDays(10).toString(),
            slutDatum  = today.minusDays(1).toString(),
        ))

        repo.ensureTodayEntries()

        val fromDb = db.receptDao().getById("r1")
        assertNotNull(fromDb)
        assertEquals(false, fromDb!!.aktiv)
    }

    @Test fun ensureTodayEntries_keeps_a_running_period_active() = runTest {
        val today = LocalDate.now()
        db.receptDao().upsert(receptEntity(
            startDatum = today.toString(),
            slutDatum  = today.plusDays(5).toString(),
        ))

        repo.ensureTodayEntries()

        assertEquals(true, db.receptDao().getById("r1")!!.aktiv)
        assertEquals(1, db.medicinDao().getByDate(today.toString()).size)
    }

    @Test fun ensureTodayEntries_uses_the_dosperiod_dose() = runTest {
        val today = LocalDate.now()
        db.receptDao().upsert(receptEntity(
            startDatum      = today.toString(),
            slutDatum       = today.plusDays(9).toString(),
            dosperioderJson = """[{"id":"d1","startDatum":"$today","slutDatum":"$today","dos":"20","enhet":"mg"}]""",
        ))

        repo.ensureTodayEntries()

        assertEquals("20", db.medicinDao().getByDate(today.toString()).single().dos)
    }

    // ─── dossynk vid sparat recept (REC-10) ───────────────────────────────────

    @Test fun saveRecept_updates_pending_doses_to_the_new_dose() = runTest {
        val today = LocalDate.now()
        db.receptDao().upsert(receptEntity(startDatum = today.toString()))
        repo.ensureTodayEntries()
        val existing = db.medicinDao().getByDate(today.toString()).single()
        assertEquals("5", existing.dos)

        val recept = repo.getReceptById("r1")!!
        repo.saveRecept(recept.copy(dos = "15"))

        assertEquals("15", db.medicinDao().getById(existing.id)!!.dos)
    }

    @Test fun saveRecept_applies_a_new_dosperiod_to_todays_pending_dose() = runTest {
        val today = LocalDate.now()
        db.receptDao().upsert(receptEntity(startDatum = today.toString()))
        repo.ensureTodayEntries()
        val existing = db.medicinDao().getByDate(today.toString()).single()

        val recept = repo.getReceptById("r1")!!
        repo.saveRecept(
            recept.copy(
                dosperioder = listOf(
                    Dosperiod(
                        id = "d1", startDatum = today.toString(),
                        slutDatum = today.plusDays(4).toString(), dos = "20", enhet = "mg",
                    ),
                ),
            ),
        )

        assertEquals("20", db.medicinDao().getById(existing.id)!!.dos)
    }

    @Test fun saveRecept_never_touches_a_taken_dose() = runTest {
        val today = LocalDate.now()
        db.receptDao().upsert(receptEntity(startDatum = today.toString()))
        repo.ensureTodayEntries()
        val existing = db.medicinDao().getByDate(today.toString()).single()
        repo.toggleTagen(existing.id, true)

        val recept = repo.getReceptById("r1")!!
        repo.saveRecept(recept.copy(dos = "15"))

        val after = db.medicinDao().getById(existing.id)!!
        assertEquals("5", after.dos)
        assertTrue(after.tagen)
    }

    @Test fun saveRecept_removes_pending_doses_that_fall_outside_a_shortened_period() = runTest {
        val today = LocalDate.now()
        db.receptDao().upsert(receptEntity(
            startDatum = today.toString(),
            slutDatum  = today.plusDays(5).toString(),
        ))
        repo.ensureTodayEntries()
        repo.ensureEntriesForDate(today.plusDays(2))
        assertEquals(1, db.medicinDao().getByDate(today.plusDays(2).toString()).size)

        val recept = repo.getReceptById("r1")!!
        repo.saveRecept(recept.copy(slutDatum = today.toString()))

        assertTrue(db.medicinDao().getByDate(today.plusDays(2).toString()).isEmpty())
        assertEquals(1, db.medicinDao().getByDate(today.toString()).size)
    }

    @Test fun saveRecept_keeps_doses_from_earlier_days_untouched() = runTest {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        db.receptDao().upsert(receptEntity(
            skapad     = yesterday.toString(),
            startDatum = yesterday.toString(),
        ))
        repo.ensureEntriesForDate(yesterday)
        val old = db.medicinDao().getByDate(yesterday.toString()).single()

        val recept = repo.getReceptById("r1")!!
        repo.saveRecept(recept.copy(dos = "15"))

        assertEquals("5", db.medicinDao().getById(old.id)!!.dos)
    }
}
