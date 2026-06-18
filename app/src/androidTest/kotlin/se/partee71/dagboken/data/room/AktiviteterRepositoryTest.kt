package se.partee71.dagboken.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.room.entities.AktivitetEntity
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AktiviteterRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: AktiviteterRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = AktiviteterRepository(db.aktivitetDao())
    }

    @After fun tearDown() { db.close() }

    private fun entity(
        id: String,
        datum: String = LocalDate.now().toString(),
        tid: String = "09:00",
        type: String = "aktivitet",
    ) = AktivitetEntity(
        id = id, timestamp = "${datum}T${tid}:00.000Z", datum = datum, tid = tid,
        aktivitet = "Promenad", energy = 5, stress = 3, somatiska = 0,
        symptom = "", aterhamtande = false, energitjuv = false, type = type,
    )

    // ─── getScreeningToday ────────────────────────────────────────────────────

    @Test fun `getScreeningToday returns only screening entries for today`() = runTest {
        val today = LocalDate.now().toString()
        db.aktivitetDao().upsert(entity("s1", datum = today, type = "screening"))
        db.aktivitetDao().upsert(entity("a1", datum = today, type = "aktivitet"))
        db.aktivitetDao().upsert(entity("s2", datum = LocalDate.now().minusDays(1).toString(), type = "screening"))

        val result = repo.getScreeningToday()
        assertEquals(1, result.size)
        assertEquals("s1", result[0].id)
    }

    @Test fun `getScreeningToday returns empty when no screening logged today`() = runTest {
        val yesterday = LocalDate.now().minusDays(1).toString()
        db.aktivitetDao().upsert(entity("s1", datum = yesterday, type = "screening"))

        val result = repo.getScreeningToday()
        assertTrue(result.isEmpty())
    }

    // ─── getRecent ────────────────────────────────────────────────────────────

    @Test fun `getRecent returns at most limit entries of the given type`() = runTest {
        val today = LocalDate.now().toString()
        repeat(5) { db.aktivitetDao().upsert(entity("s$it", datum = today, type = "screening")) }

        val result = repo.getRecent("screening", 3)
        assertEquals(3, result.size)
        assertTrue(result.all { it.type == "screening" })
    }

    @Test fun `getRecent does not return entries of other type`() = runTest {
        val today = LocalDate.now().toString()
        db.aktivitetDao().upsert(entity("a1", datum = today, type = "aktivitet"))

        val result = repo.getRecent("screening", 10)
        assertTrue(result.isEmpty())
    }

    // ─── screeningFromDate ────────────────────────────────────────────────────

    @Test fun `screeningFromDate flow emits only screening entries on or after cutoff`() = runTest {
        val cutoff = LocalDate.now().minusDays(7).toString()
        val old    = LocalDate.now().minusDays(10).toString()
        val recent = LocalDate.now().minusDays(3).toString()

        db.aktivitetDao().upsert(entity("old",    datum = old,    type = "screening"))
        db.aktivitetDao().upsert(entity("recent", datum = recent, type = "screening"))
        db.aktivitetDao().upsert(entity("a1",     datum = recent, type = "aktivitet"))

        repo.screeningFromDate(7).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("recent", items[0].id)
            cancel()
        }
    }

    // ─── hasScreeningToday ────────────────────────────────────────────────────

    @Test fun `hasScreeningToday returns true when screening logged today`() = runTest {
        val today = LocalDate.now().toString()
        db.aktivitetDao().upsert(entity("s1", datum = today, type = "screening"))
        assertTrue(repo.hasScreeningToday())
    }

    @Test fun `hasScreeningToday returns false when no screening today`() = runTest {
        assertTrue(!repo.hasScreeningToday())
    }
}
