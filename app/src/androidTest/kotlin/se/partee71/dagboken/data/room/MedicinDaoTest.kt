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
import se.partee71.dagboken.data.room.daos.MedicinDao
import se.partee71.dagboken.data.room.entities.MedicinEntity

@RunWith(AndroidJUnit4::class)
class MedicinDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MedicinDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.medicinDao()
    }

    @After fun tearDown() { db.close() }

    private fun entity(
        id: String = "m1",
        datum: String = "2026-01-15",
        namn: String = "Ibuprofen",
        tagen: Boolean = false,
        skipped: Boolean = false,
        receptId: String? = null,
        timestamp: String = "2026-01-15T09:00:00.000Z",
        tagenTid: String? = null,
    ) = MedicinEntity(
        id = id, timestamp = timestamp, datum = datum, tid = "09:00",
        namn = namn, dos = "400", enhet = "mg", tidpunkt = "Morgon",
        tagen = tagen, receptId = receptId, skipped = skipped, tagenTid = tagenTid,
    )

    @Test fun upsertAndGetById() = runTest {
        dao.upsert(entity())
        assertNotNull(dao.getById("m1"))
    }

    @Test fun getTodayFlow_returnsOnlyTodayNonSkipped() = runTest {
        dao.upsert(entity(id = "m1", datum = "2026-01-15", skipped = false))
        dao.upsert(entity(id = "m2", datum = "2026-01-15", skipped = true))
        dao.upsert(entity(id = "m3", datum = "2026-01-14"))
        dao.getTodayFlow("2026-01-15").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("m1", items[0].id)
            cancel()
        }
    }

    @Test fun updateTagen_toggles() = runTest {
        dao.upsert(entity(tagen = false))
        dao.updateTagen("m1", true)
        val updated = dao.getById("m1")!!
        assertTrue(updated.tagen)
    }

    @Test fun updateTagen_sets_tagenTid_when_marked_taken() = runTest {
        dao.upsert(entity(tagen = false))
        dao.updateTagen("m1", true, "14:32")
        assertEquals("14:32", dao.getById("m1")!!.tagenTid)
    }

    @Test fun updateTagen_clears_tagenTid_when_unmarked() = runTest {
        dao.upsert(entity(tagen = true, tagenTid = "14:32"))
        dao.updateTagen("m1", false, null)
        assertNull(dao.getById("m1")!!.tagenTid)
    }

    @Test fun getTakenFlow_returnsOnlyTakenNonSkipped() = runTest {
        dao.upsert(entity(id = "m1", tagen = true, skipped = false))
        dao.upsert(entity(id = "m2", tagen = false, skipped = false))
        dao.upsert(entity(id = "m3", tagen = true, skipped = true))
        dao.getTakenFlow().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("m1", items[0].id)
            cancel()
        }
    }

    @Test fun getLastTakenBefore_returnsLatestTakenAtOrBeforeReference() = runTest {
        dao.upsert(entity(id = "early", tagen = true, timestamp = "2026-01-15T08:00:00.000Z"))
        dao.upsert(entity(id = "late", tagen = true, timestamp = "2026-01-15T20:00:00.000Z"))
        val result = dao.getLastTakenBefore("Ibuprofen", "2026-01-15T12:00:00.000Z")
        assertEquals("early", result?.id)
    }

    @Test fun getLastTakenBefore_returnsNullWhenAllDosesAreAfterReference() = runTest {
        dao.upsert(entity(id = "m1", tagen = true, timestamp = "2026-01-15T20:00:00.000Z"))
        val result = dao.getLastTakenBefore("Ibuprofen", "2026-01-15T08:00:00.000Z")
        assertNull(result)
    }

    @Test fun markSkipped_setsSkipped() = runTest {
        dao.upsert(entity(id = "m1", skipped = false))
        dao.markSkipped("m1")
        val updated = dao.getById("m1")!!
        assertTrue(updated.skipped)
    }

    @Test fun getLastTaken_returnsLatestByTimestamp() = runTest {
        dao.upsert(entity(id = "old", namn = "Ibuprofen", tagen = true, timestamp = "2026-01-14T09:00:00.000Z"))
        dao.upsert(entity(id = "new", namn = "Ibuprofen", tagen = true, timestamp = "2026-01-15T09:00:00.000Z"))
        val result = dao.getLastTaken("Ibuprofen")
        assertEquals("new", result?.id)
    }

    @Test fun getLastTaken_caseInsensitive() = runTest {
        dao.upsert(entity(id = "m1", namn = "IBUPROFEN", tagen = true, timestamp = "2026-01-15T09:00:00.000Z"))
        val result = dao.getLastTaken("ibuprofen")
        assertNotNull(result)
    }

    @Test fun countDailyDoses_countsOnlyTakenNonSkipped() = runTest {
        dao.upsert(entity(id = "m1", namn = "Ibuprofen", tagen = true,  skipped = false))
        dao.upsert(entity(id = "m2", namn = "Ibuprofen", tagen = false, skipped = false))
        dao.upsert(entity(id = "m3", namn = "Ibuprofen", tagen = true,  skipped = true))
        val count = dao.countDailyDoses("2026-01-15", "Ibuprofen")
        assertEquals(1, count)
    }

    @Test fun delete_removesEntity() = runTest {
        val e = entity()
        dao.upsert(e)
        dao.delete(e)
        assertNull(dao.getById("m1"))
    }

    @Test fun count_increasesOnInsert() = runTest {
        assertEquals(0, dao.count())
        dao.upsert(entity("m1"))
        dao.upsert(entity("m2"))
        assertEquals(2, dao.count())
    }
}
