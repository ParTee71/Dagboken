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
import se.partee71.dagboken.data.migration.AktivitetJson
import se.partee71.dagboken.data.migration.BackupJson
import se.partee71.dagboken.data.migration.BackupMapper
import se.partee71.dagboken.data.migration.FavoritJson
import se.partee71.dagboken.data.migration.MedicinJson
import se.partee71.dagboken.data.migration.ReceptJson
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase

/**
 * Verifies the full import pipeline: BackupMapper → repo-import → DAO → read back.
 */
@RunWith(AndroidJUnit4::class)
class MigrationRoundTripTest {

    private lateinit var db: AppDatabase
    private lateinit var aktivRepo: AktiviteterRepository
    private lateinit var medicRepo: MedicinerRepository

    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(s: String)    = runCatching { json.decodeFromString<List<String>>(s) }.getOrDefault(emptyList())
    private fun decodeInt(s: String) = runCatching { json.decodeFromString<List<Int>>(s) }.getOrDefault(emptyList())

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        aktivRepo = AktiviteterRepository(db.aktivitetDao())
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

    @Test fun full_import_preserves_all_entity_counts() = runTest {
        val backup = testBackup()
        aktivRepo.importAll(BackupMapper.toAktiviteter(backup))
        medicRepo.importMediciner(BackupMapper.toMediciner(backup))
        medicRepo.importRecept(BackupMapper.toRecept(backup))
        medicRepo.importFavoriter(BackupMapper.toFavoriter(backup))

        assertEquals(2, db.aktivitetDao().count())
        assertEquals(1, db.medicinDao().count())
    }

    // ─── upsert idempotency ───────────────────────────────────────────────────

    @Test fun importing_same_backup_twice_does_not_create_duplicate_entries() = runTest {
        val backup = testBackup()
        repeat(2) {
            aktivRepo.importAll(BackupMapper.toAktiviteter(backup))
            medicRepo.importMediciner(BackupMapper.toMediciner(backup))
        }
        assertEquals(2, db.aktivitetDao().count())
        assertEquals(1, db.medicinDao().count())
    }
}
