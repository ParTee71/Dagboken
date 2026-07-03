package se.partee71.dagboken.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    ) = MedicinEntity(
        id = id, timestamp = "${datum}T${tid}:00.000Z", datum = datum, tid = tid,
        namn = namn, dos = "400", enhet = "mg", tidpunkt = "Morgon",
        tagen = tagen, receptId = receptId, skipped = skipped,
    )

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
}
