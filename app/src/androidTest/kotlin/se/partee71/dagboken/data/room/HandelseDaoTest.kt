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
import se.partee71.dagboken.data.room.daos.HandelseDao
import se.partee71.dagboken.data.room.entities.HandelseEntity

@RunWith(AndroidJUnit4::class)
class HandelseDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: HandelseDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.handelseDao()
    }

    @After fun tearDown() { db.close() }

    private fun entity(
        id: String = "h1",
        datum: String = "2026-06-21",
        tid: String = "10:00",
        typ: String = "Yrsel",
        svarighetsgrad: Int = 5,
        varaktighetMinuter: Int = 30,
    ) = HandelseEntity(
        id = id, timestamp = "${datum}T${tid}:00.000Z",
        datum = datum, tid = tid, typ = typ,
        svarighetsgrad = svarighetsgrad, varaktighetMinuter = varaktighetMinuter,
        triggers = "", atgarder = "", anteckning = "",
    )

    // ─── basic CRUD ───────────────────────────────────────────────────────────

    @Test fun upsertAndGetById() = runTest {
        dao.upsert(entity())
        assertNotNull(dao.getById("h1"))
    }

    @Test fun getById_returnsNullForMissingId() = runTest {
        assertNull(dao.getById("nonexistent"))
    }

    @Test fun delete_removesEntity() = runTest {
        val e = entity()
        dao.upsert(e)
        dao.delete(e)
        assertNull(dao.getById("h1"))
    }

    @Test fun upsert_updatesExistingEntityById() = runTest {
        dao.upsert(entity(typ = "Yrsel"))
        dao.upsert(entity(typ = "Blodtrycksfall"))
        val result = dao.getById("h1")!!
        assertEquals("Blodtrycksfall", result.typ)
    }

    // ─── count / deleteAll ────────────────────────────────────────────────────

    @Test fun count_returnsCorrectCount() = runTest {
        assertEquals(0, dao.count())
        dao.upsert(entity(id = "h1"))
        dao.upsert(entity(id = "h2"))
        assertEquals(2, dao.count())
    }

    @Test fun deleteAll_clearsTable() = runTest {
        dao.upsert(entity(id = "h1"))
        dao.upsert(entity(id = "h2"))
        dao.deleteAll()
        assertEquals(0, dao.count())
    }

    // ─── getAllFlow ───────────────────────────────────────────────────────────

    @Test fun getAllFlow_emitsEmptyListInitially() = runTest {
        dao.getAllFlow().test {
            assertEquals(0, awaitItem().size)
            cancel()
        }
    }

    @Test fun getAllFlow_emitsUpdatedListOnInsert() = runTest {
        dao.getAllFlow().test {
            assertEquals(0, awaitItem().size)
            dao.upsert(entity())
            assertEquals(1, awaitItem().size)
            cancel()
        }
    }

    @Test fun getAllFlow_orderedByDatumDescThenTidDesc() = runTest {
        dao.upsert(entity(id = "a", datum = "2026-06-20", tid = "09:00"))
        dao.upsert(entity(id = "b", datum = "2026-06-21", tid = "08:00"))
        dao.upsert(entity(id = "c", datum = "2026-06-21", tid = "14:00"))

        dao.getAllFlow().test {
            val items = awaitItem()
            // Newest date first, then latest time first within same date
            assertEquals("c", items[0].id)
            assertEquals("b", items[1].id)
            assertEquals("a", items[2].id)
            cancel()
        }
    }

    // ─── getFromDateFlow ──────────────────────────────────────────────────────

    @Test fun getFromDateFlow_excludesEntriesBeforeCutoff() = runTest {
        dao.upsert(entity(id = "old", datum = "2020-01-01"))
        dao.upsert(entity(id = "new", datum = "2026-06-21"))

        dao.getFromDateFlow("2026-01-01").test {
            val items = awaitItem()
            assertTrue(items.any { it.id == "new" })
            assertTrue(items.none { it.id == "old" })
            cancel()
        }
    }

    @Test fun getFromDateFlow_includesEntryExactlyOnCutoffDate() = runTest {
        dao.upsert(entity(id = "boundary", datum = "2026-01-01"))

        dao.getFromDateFlow("2026-01-01").test {
            val items = awaitItem()
            assertTrue(items.any { it.id == "boundary" })
            cancel()
        }
    }

    // ─── upsertAll ────────────────────────────────────────────────────────────

    @Test fun upsertAll_insertsMultipleEntities() = runTest {
        val entities = listOf(
            entity(id = "h1", typ = "Yrsel"),
            entity(id = "h2", typ = "Blodtrycksfall"),
            entity(id = "h3", typ = "Andnöd"),
        )
        dao.upsertAll(entities)
        assertEquals(3, dao.count())
    }

    @Test fun upsertAll_updatesExistingOnConflict() = runTest {
        dao.upsert(entity(id = "h1", typ = "Yrsel"))
        dao.upsertAll(listOf(entity(id = "h1", typ = "Blodtrycksfall")))
        assertEquals("Blodtrycksfall", dao.getById("h1")!!.typ)
    }
}
