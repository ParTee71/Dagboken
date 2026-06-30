package se.partee71.dagboken.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.Dispatchers
import se.partee71.dagboken.data.migration.AktivitetJson
import se.partee71.dagboken.data.migration.BackupJson
import se.partee71.dagboken.data.migration.BackupMapper
import se.partee71.dagboken.data.migration.FavoritJson
import se.partee71.dagboken.data.migration.HandelseJson
import se.partee71.dagboken.data.migration.MedicinJson
import se.partee71.dagboken.data.migration.NoteJson
import se.partee71.dagboken.data.migration.ReceptJson
import se.partee71.dagboken.data.migration.ScreeningEventConfigJson
import se.partee71.dagboken.data.migration.SjukdomsEpisodJson
import se.partee71.dagboken.data.migration.SjukdomsIncheckningJson
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase

/**
 * Verifies the full import pipeline: BackupMapper → repo-import → DAO → read back.
 */
@RunWith(AndroidJUnit4::class)
class MigrationRoundTripTest {

    private lateinit var db: AppDatabase
    private lateinit var aktivRepo: AktiviteterRepository
    private lateinit var medicRepo: MedicinerRepository
    private lateinit var handelserRepo: HandelserRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var sjukdomarRepo: SjukdomarRepository

    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(s: String)    = runCatching { json.decodeFromString<List<String>>(s) }.getOrDefault(emptyList())
    private fun decodeInt(s: String) = runCatching { json.decodeFromString<List<Int>>(s) }.getOrDefault(emptyList())

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        aktivRepo = AktiviteterRepository(db.aktivitetDao())
        handelserRepo = HandelserRepository(db.handelseDao())
        noteRepo = NoteRepository(db.noteDao())
        sjukdomarRepo = SjukdomarRepository(db.sjukdomsEpisodDao(), db.sjukdomsIncheckningDao(), Dispatchers.IO)
        medicRepo = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = json,
        )
    }

    @After fun tearDown() { db.close() }

    private fun testBackup() = BackupJson(
        version    = 1,
        createdAt  = "2026-01-15T09:00:00",
        aktiviteter = listOf(
            AktivitetJson(
                id = "a1", timestamp = "2026-01-15T09:00:00.000Z", datum = "2026-01-15",
                tid = "09:00", aktivitet = "Promenad", energy = 5, stress = 3,
                type = "aktivitet",
            ),
            AktivitetJson(
                id = "s1", timestamp = "2026-01-15T08:00:00.000Z", datum = "2026-01-15",
                tid = "08:00", aktivitet = "Morgonscreening", energy = 7, stress = 2,
                type = "screening",
            ),
        ),
        mediciner = listOf(
            MedicinJson(
                id = "m1", timestamp = "2026-01-15T07:00:00.000Z", datum = "2026-01-15",
                tid = "07:00", namn = "Metformin", dos = "500", enhet = "mg",
                tidpunkt = "Morgon", tagen = true,
            ),
        ),
        medicinRecipes = listOf(
            ReceptJson(
                id = "r1", namn = "Vitamin D", dos = "1", enhet = "st",
                tidpunkter = listOf("Morgon"), upprepning = "dagligen",
                aktiv = true, skapad = "2026-01-01",
            ),
        ),
        medicinFavoriter = listOf(
            FavoritJson(
                id = "f1", namn = "Paracetamol", dos = "500", enhet = "mg",
                tidpunkt = "Vid behov", minTidMellan = 4,
            ),
        ),
        sjukdomsepisoder = listOf(
            SjukdomsEpisodJson(
                id = "e1", typ = "migrän", startDatum = "2026-01-10",
                slutDatum = "2026-01-12", anteckning = "Tung period",
                timestamp = 1_700_000_000_000L,
            ),
        ),
        sjukdomsIncheckningar = listOf(
            SjukdomsIncheckningJson(
                id = "i1", episodId = "e1", datum = "2026-01-10", tid = "10:00",
                svarighetsgrad = 8, symptom = "Yrsel:3", somatiska = 2,
                anteckning = "Tog medicin", timestamp = 1_700_000_111_000L,
            ),
            SjukdomsIncheckningJson(
                id = "i2", episodId = "e1", datum = "2026-01-11", tid = "08:00",
                svarighetsgrad = 5, symptom = "", somatiska = 0,
                timestamp = 1_700_000_222_000L,
            ),
        ),
        handelser = listOf(
            HandelseJson(
                id = "h1", timestamp = "2026-01-15T14:30:00.000Z", datum = "2026-01-15",
                tid = "14:30", typ = "huvudvärk", svarighetsgrad = 6,
                varaktighetMinuter = 90, triggers = "[\"stress\"]",
                atgarder = "[\"vila\"]", anteckning = "Kom efter möte",
            ),
            HandelseJson(
                id = "h2", timestamp = "2026-01-16T10:00:00.000Z", datum = "2026-01-16",
                tid = "10:00", typ = "migrän", svarighetsgrad = 9,
                varaktighetMinuter = 240, triggers = "[]", atgarder = "[]",
            ),
        ),
        notes = listOf(
            NoteJson(target = "ACTIVITY", entityId = "a1", text = "Mådde bra efteråt"),
            NoteJson(target = "MEDICATION", entityId = "m1", text = "Tog med mat"),
        ),
        screeningEventConfigs = listOf(
            ScreeningEventConfigJson(enabled = true, time = "08:00"),
            ScreeningEventConfigJson(enabled = false, time = "12:00"),
        ),
        sheetsConfig = "https://docs.google.com/spreadsheets/d/test123",
    )

    // ─── round-trip ───────────────────────────────────────────────────────────

    @Test fun aktiviteter_survive_mapper_and_import_and_can_be_read_back() = runTest {
        val backup = testBackup()
        val aktiviteter = BackupMapper.toAktiviteter(backup)
        aktivRepo.importAll(aktiviteter)

        val fromDb = db.aktivitetDao().getById("a1")
        assertNotNull(fromDb)
        assertEquals("Promenad", fromDb!!.aktivitet)
        assertEquals(5, fromDb.energy)
    }

    @Test fun screening_aktivitet_round_trips_with_correct_type() = runTest {
        val backup = testBackup()
        aktivRepo.importAll(BackupMapper.toAktiviteter(backup))

        val screening = db.aktivitetDao().getById("s1")
        assertNotNull(screening)
        assertEquals("screening", screening!!.type)
    }

    @Test fun mediciner_survive_mapper_and_import_and_can_be_read_back() = runTest {
        val backup = testBackup()
        medicRepo.importMediciner(BackupMapper.toMediciner(backup))

        val fromDb = db.medicinDao().getById("m1")
        assertNotNull(fromDb)
        assertEquals("Metformin", fromDb!!.namn)
        assertTrue(fromDb.tagen)
    }

    @Test fun recept_survive_mapper_and_import_and_can_be_read_back() = runTest {
        val backup = testBackup()
        medicRepo.importRecept(BackupMapper.toRecept(backup))

        val fromDb = db.receptDao().getById("r1")
        assertNotNull(fromDb)
        assertEquals("Vitamin D", fromDb!!.namn)
        assertTrue(fromDb.aktiv)
    }

    @Test fun favoriter_survive_mapper_and_import_and_can_be_read_back() = runTest {
        val backup = testBackup()
        medicRepo.importFavoriter(BackupMapper.toFavoriter(backup))

        val fromDb = db.favoritDao().getById("f1")
        assertNotNull(fromDb)
        assertEquals("Paracetamol", fromDb!!.namn)
        assertEquals(4, fromDb.minTidMellan)
    }

    @Test fun sjukdomsepisoder_survive_mapper_and_import_and_can_be_read_back() = runTest {
        val backup = testBackup()
        sjukdomarRepo.importEpisoder(BackupMapper.toSjukdomsEpisoder(backup))

        val fromDb = db.sjukdomsEpisodDao().getById("e1")
        assertNotNull(fromDb)
        assertEquals("migrän", fromDb!!.typ)
        assertEquals("2026-01-10", fromDb.startDatum)
        assertEquals("2026-01-12", fromDb.slutDatum)
        assertEquals("Tung period", fromDb.anteckning)
        assertEquals(1_700_000_000_000L, fromDb.timestamp)
    }

    @Test fun sjukdomsincheckningar_survive_mapper_and_import_and_can_be_read_back() = runTest {
        val backup = testBackup()
        sjukdomarRepo.importEpisoder(BackupMapper.toSjukdomsEpisoder(backup))
        sjukdomarRepo.importIncheckningar(BackupMapper.toSjukdomsIncheckningar(backup))

        val fromDb = db.sjukdomsIncheckningDao().getById("i1")
        assertNotNull(fromDb)
        assertEquals("e1", fromDb!!.episodId)
        assertEquals(8, fromDb.svarighetsgrad)
        assertEquals("Yrsel:3", fromDb.symptom)
        assertEquals(2, fromDb.somatiska)
        assertEquals("Tog medicin", fromDb.anteckning)
        assertEquals(1_700_000_111_000L, fromDb.timestamp)
    }

    @Test fun notes_survive_mapper_and_import_and_can_be_read_back() = runTest {
        val backup = testBackup()
        noteRepo.importAll(BackupMapper.toNotes(backup))

        val text = db.noteDao().getAll().find { it.target == "ACTIVITY" && it.entityId == "a1" }?.text
        assertEquals("Mådde bra efteråt", text)
    }

    @Test fun notes_with_blank_text_are_filtered_out_during_import() = runTest {
        val backup = testBackup().copy(
            notes = listOf(
                NoteJson(target = "ACTIVITY", entityId = "x1", text = ""),
                NoteJson(target = "ACTIVITY", entityId = "x2", text = "  "),
                NoteJson(target = "ACTIVITY", entityId = "x3", text = "Giltig"),
            )
        )
        noteRepo.importAll(BackupMapper.toNotes(backup))
        assertEquals(1, db.noteDao().count())
    }

    @Test fun handelser_survive_mapper_and_import_and_can_be_read_back() = runTest {
        val backup = testBackup()
        handelserRepo.importAll(BackupMapper.toHandelser(backup))

        val fromDb = db.handelseDao().getById("h1")
        assertNotNull(fromDb)
        assertEquals("huvudvärk", fromDb!!.typ)
        assertEquals(6, fromDb.svarighetsgrad)
        assertEquals(90, fromDb.varaktighetMinuter)
        assertEquals("Kom efter möte", fromDb.anteckning)
    }

    @Test fun handelser_all_fields_survive_round_trip() = runTest {
        val backup = testBackup()
        handelserRepo.importAll(BackupMapper.toHandelser(backup))

        val fromDb = db.handelseDao().getById("h1")
        assertNotNull(fromDb)
        assertEquals("2026-01-15T14:30:00.000Z", fromDb!!.timestamp)
        assertEquals("2026-01-15", fromDb.datum)
        assertEquals("14:30", fromDb.tid)
        assertEquals("[\"stress\"]", fromDb.triggers)
        assertEquals("[\"vila\"]", fromDb.atgarder)
    }

    @Test fun full_import_preserves_all_entity_counts() = runTest {
        val backup = testBackup()
        aktivRepo.importAll(BackupMapper.toAktiviteter(backup))
        medicRepo.importMediciner(BackupMapper.toMediciner(backup))
        medicRepo.importRecept(BackupMapper.toRecept(backup))
        medicRepo.importFavoriter(BackupMapper.toFavoriter(backup))
        sjukdomarRepo.importEpisoder(BackupMapper.toSjukdomsEpisoder(backup))
        sjukdomarRepo.importIncheckningar(BackupMapper.toSjukdomsIncheckningar(backup))
        handelserRepo.importAll(BackupMapper.toHandelser(backup))
        noteRepo.importAll(BackupMapper.toNotes(backup))

        assertEquals(2, db.aktivitetDao().count())
        assertEquals(1, db.medicinDao().count())
        assertEquals(1, db.sjukdomsEpisodDao().count())
        assertEquals(2, db.sjukdomsIncheckningDao().count())
        assertEquals(2, db.handelseDao().count())
        assertEquals(2, db.noteDao().count())
    }

    // ─── upsert idempotency ───────────────────────────────────────────────────

    @Test fun importing_same_backup_twice_does_not_create_duplicate_entries() = runTest {
        val backup = testBackup()
        repeat(2) {
            aktivRepo.importAll(BackupMapper.toAktiviteter(backup))
            medicRepo.importMediciner(BackupMapper.toMediciner(backup))
            sjukdomarRepo.importEpisoder(BackupMapper.toSjukdomsEpisoder(backup))
            sjukdomarRepo.importIncheckningar(BackupMapper.toSjukdomsIncheckningar(backup))
            handelserRepo.importAll(BackupMapper.toHandelser(backup))
            noteRepo.importAll(BackupMapper.toNotes(backup))
        }
        assertEquals(2, db.aktivitetDao().count())
        assertEquals(1, db.medicinDao().count())
        assertEquals(1, db.sjukdomsEpisodDao().count())
        assertEquals(2, db.sjukdomsIncheckningDao().count())
        assertEquals(2, db.handelseDao().count())
        assertEquals(2, db.noteDao().count())
    }

    @Test fun backup_without_handelser_field_imports_without_error() = runTest {
        // Simulates restoring an old backup (before händelser existed in backup format)
        val legacyBackup = testBackup().copy(handelser = emptyList())
        handelserRepo.importAll(BackupMapper.toHandelser(legacyBackup))
        assertEquals(0, db.handelseDao().count())
    }

    @Test fun backup_without_optional_fields_imports_without_error() = runTest {
        // Simulates restoring a legacy backup missing notes/screeningEventConfigs/sheetsConfig
        val legacyBackup = testBackup().copy(
            notes = emptyList(),
            screeningEventConfigs = null,
            sheetsConfig = null,
        )
        noteRepo.importAll(BackupMapper.toNotes(legacyBackup))
        assertEquals(0, db.noteDao().count())
    }
}
