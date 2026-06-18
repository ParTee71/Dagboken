package se.partee71.dagboken.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.room.daos.AktivitetDao
import se.partee71.dagboken.data.room.entities.AktivitetEntity

@RunWith(AndroidJUnit4::class)
class AktivitetDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AktivitetDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.aktivitetDao()
    }

    @After fun tearDown() { db.close() }

    private fun entity(
        id: String = "a1",
        datum: String = "2026-01-15",
        tid: String = "09:00",
        aktivitet: String = "Promenad",
        type: String = "aktivitet",
    ) = AktivitetEntity(
        id = id, timestamp = "${datum}T${tid}:00.000Z", datum = datum, tid = tid,
        aktivitet = aktivitet, energy = 5, stress = 3, somatiska = 0,
        symptom = "", aterhamtande = false, energitjuv = false, type = type,
    )

    @Test fun upsertAndGetById() = runTest {
        dao.upsert(entity())
        assertNotNull(dao.getById("a1"))
    }

    @Test fun getAllFlow_emitsInserted() = runTest {
        dao.getAllFlow().test {
            assertEquals(0, awaitItem().size)
            dao.upsert(entity())
            assertEquals(1, awaitItem().size)
            cancel()
        }
    }

    @Test fun getByDate_returnsCorrectDate() = runTest {
        dao.upsert(entity(id = "a1", datum = "2026-01-15"))
        dao.upsert(entity(id = "a2", datum = "2026-01-16"))
        val result = dao.getByDate("2026-01-15")
        assertEquals(1, result.size)
        assertEquals("a1", result[0].id)
    }

    @Test fun delete_removesEntity() = runTest {
        val e = entity()
        dao.upsert(e)
        dao.delete(e)
        assertNull(dao.getById("a1"))
    }

    @Test fun upsert_updatesExisting() = runTest {
        dao.upsert(entity(aktivitet = "Promenad"))
        dao.upsert(entity(aktivitet = "Jobb"))
        val e = dao.getById("a1")!!
        assertEquals("Jobb", e.aktivitet)
    }

    @Test fun getRecent_returnsLimitedByType() = runTest {
        dao.upsert(entity(id = "s1", type = "screening"))
        dao.upsert(entity(id = "s2", type = "screening"))
        dao.upsert(entity(id = "a1", type = "aktivitet"))
        val result = dao.getRecent("screening", 1)
        assertEquals(1, result.size)
        assertEquals("screening", result[0].type)
    }

    @Test fun count_returnsCorrectCount() = runTest {
        assertEquals(0, dao.count())
        dao.upsert(entity(id = "a1"))
        dao.upsert(entity(id = "a2"))
        assertEquals(2, dao.count())
    }

    @Test fun deleteAll_clearsTable() = runTest {
        dao.upsert(entity(id = "a1"))
        dao.upsert(entity(id = "a2"))
        dao.deleteAll()
        assertEquals(0, dao.count())
    }

    @Test fun getFromDateFlow_excludesOlderEntries() = runTest {
        dao.upsert(entity(id = "old", datum = "2020-01-01"))
        dao.upsert(entity(id = "new", datum = "2026-06-01"))
        dao.getFromDateFlow("2026-01-01").test {
            val items = awaitItem()
            assertTrue(items.any { it.id == "new" })
            assertTrue(items.none { it.id == "old" })
            cancel()
        }
    }

    @Test fun getScreeningToday_returnsOnlyScreeningForDate() = runTest {
        dao.upsert(entity(id = "s1", datum = "2026-06-18", type = "screening"))
        dao.upsert(entity(id = "a1", datum = "2026-06-18", type = "aktivitet"))
        dao.upsert(entity(id = "s2", datum = "2026-06-17", type = "screening"))
        val result = dao.getScreeningToday("2026-06-18")
        assertEquals(1, result.size)
        assertEquals("s1", result[0].id)
        assertEquals("screening", result[0].type)
    }

    @Test fun getScreeningToday_returnsEmptyWhenNoneForDate() = runTest {
        dao.upsert(entity(id = "s1", datum = "2026-06-17", type = "screening"))
        val result = dao.getScreeningToday("2026-06-18")
        assertTrue(result.isEmpty())
    }

    @Test fun getScreeningToday_orderedByTidAscending() = runTest {
        dao.upsert(entity(id = "s2", datum = "2026-06-18", tid = "14:00", type = "screening"))
        dao.upsert(entity(id = "s1", datum = "2026-06-18", tid = "08:00", type = "screening"))
        val result = dao.getScreeningToday("2026-06-18")
        assertEquals(2, result.size)
        assertEquals("s1", result[0].id)
        assertEquals("s2", result[1].id)
    }
}
