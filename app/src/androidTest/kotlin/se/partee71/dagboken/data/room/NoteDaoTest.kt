package se.partee71.dagboken.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.room.daos.NoteDao
import se.partee71.dagboken.data.room.entities.NoteEntity

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NoteDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.noteDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun observe_returnsNullWhenNoRow() = runTest {
        dao.observe("DAY", "2026-06-24").test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun upsert_insertsRow() = runTest {
        dao.upsert(NoteEntity(targetType = "DAY", targetId = "2026-06-24", text = "hello"))
        val row = dao.getByKey("DAY", "2026-06-24")
        assertEquals("hello", row?.text)
    }

    @Test fun upsert_secondCallUpdatesText() = runTest {
        dao.upsert(NoteEntity(targetType = "DAY", targetId = "2026-06-24", text = "v1"))
        dao.upsert(NoteEntity(targetType = "DAY", targetId = "2026-06-24", text = "v2"))
        assertEquals("v2", dao.getByKey("DAY", "2026-06-24")?.text)
        assertEquals(1, dao.count())
    }

    @Test fun delete_removesCorrectRow() = runTest {
        dao.upsert(NoteEntity(targetType = "EVENT", targetId = "e1", text = "note"))
        dao.upsert(NoteEntity(targetType = "EVENT", targetId = "e2", text = "other"))
        dao.delete("EVENT", "e1")
        assertNull(dao.getByKey("EVENT", "e1"))
        assertEquals("other", dao.getByKey("EVENT", "e2")?.text)
    }

    @Test fun observeAll_returnsOnlyMatchingType() = runTest {
        dao.upsert(NoteEntity(targetType = "ACTIVITY", targetId = "a1", text = "act"))
        dao.upsert(NoteEntity(targetType = "DAY",      targetId = "d1", text = "day"))
        dao.observeAll("ACTIVITY").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("act", items.first().text)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
