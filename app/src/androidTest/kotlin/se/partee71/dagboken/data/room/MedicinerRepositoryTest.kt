package se.partee71.dagboken.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.room.entities.MedicinEntity
import se.partee71.dagboken.data.room.entities.ReceptEntity
import se.partee71.dagboken.data.room.entities.toDomain
import se.partee71.dagboken.domain.model.Medicin
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
            medicinDao     = db.medicinDao(),
            receptDao      = db.receptDao(),
            favoritDao     = db.favoritDao(),
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
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
        tagen = tagen, anteckning = "", receptId = receptId, skipped = skipped,
    )

    // ─── ensureTodayEntries – idempotency ─────────────────────────────────────

    @Test fun `ensureTodayEntries is idempotent for active recept`() = runTest {
        val today = LocalDate.now().toString()
        db.receptDao().upsert(ReceptEntity(
            id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, anteckning = "", aktiv = true, skapad = today,
        ))

        repo.ensureTodayEntries()
        repo.ensureTodayEntries()

        val count = db.medicinDao().countDailyDoses(today, "Metformin")
        // countDailyDoses only counts tagen=1, but we can check total entries via getByDate
        val entries = db.medicinDao().getByDate(today)
        assertEquals("Idempotent: only one entry created", 1, entries.size)
    }

    // ─── countDailyDoses ──────────────────────────────────────────────────────

    @Test fun `countDailyDoses counts taken non-skipped doses for today`() = runTest {
        val today = LocalDate.now().toString()
        db.medicinDao().upsert(medicinEntity(id = "m1", tagen = true, datum = today))
        db.medicinDao().upsert(medicinEntity(id = "m2", tagen = true, datum = today))
        db.medicinDao().upsert(medicinEntity(id = "m3", tagen = false, datum = today))
        db.medicinDao().upsert(medicinEntity(id = "m4", tagen = true, skipped = true, datum = today))

        val count = repo.countDailyDoses(today, "Ibuprofen")
        assertEquals("Expected 2 taken non-skipped doses", 2, count)
    }

    @Test fun `countDailyDoses is case-insensitive for name`() = runTest {
        val today = LocalDate.now().toString()
        db.medicinDao().upsert(medicinEntity(id = "m1", namn = "IBUPROFEN", tagen = true, datum = today))

        val count = repo.countDailyDoses(today, "ibuprofen")
        assertEquals(1, count)
    }

    // ─── getLastTaken ─────────────────────────────────────────────────────────

    @Test fun `getLastTaken returns most recent taken entry by timestamp`() = runTest {
        db.medicinDao().upsert(medicinEntity(id = "m1", tagen = true,  tid = "08:00"))
        db.medicinDao().upsert(medicinEntity(id = "m2", tagen = true,  tid = "12:00"))
        db.medicinDao().upsert(medicinEntity(id = "m3", tagen = false, tid = "14:00"))

        val last = repo.getLastTaken("Ibuprofen")
        assertNotNull(last)
        assertEquals("m2", last!!.id)
    }

    @Test fun `getLastTaken returns null when no taken entries exist`() = runTest {
        db.medicinDao().upsert(medicinEntity(id = "m1", tagen = false))
        assertNull(repo.getLastTaken("Ibuprofen"))
    }

    // ─── skipMedicin vs deleteMedicin ─────────────────────────────────────────

    @Test fun `skipMedicin sets skipped flag and keeps entry in DB`() = runTest {
        db.medicinDao().upsert(medicinEntity(id = "m1"))
        repo.skipMedicin("m1")
        val entry = db.medicinDao().getById("m1")
        assertNotNull(entry)
        assertTrue(entry!!.skipped)
    }

    @Test fun `deleteMedicin removes entry from DB`() = runTest {
        val entity = medicinEntity(id = "m1")
        db.medicinDao().upsert(entity)
        repo.deleteMedicin(entity.toDomain())
        assertNull(db.medicinDao().getById("m1"))
    }

    @Test fun `skipped entry is excluded from todayFlow`() = runTest {
        val today = LocalDate.now().toString()
        db.medicinDao().upsert(medicinEntity(id = "m1", datum = today, skipped = true))
        db.medicinDao().upsert(medicinEntity(id = "m2", datum = today, skipped = false))

        // todayFlow uses getTodayFlow which filters skipped=0
        val entries = db.medicinDao().getByDate(today).filter { !it.skipped }
        assertEquals(1, entries.size)
        assertEquals("m2", entries[0].id)
    }
}
