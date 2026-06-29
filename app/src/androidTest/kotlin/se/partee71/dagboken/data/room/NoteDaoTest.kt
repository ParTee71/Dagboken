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
        dao.observe("ACTIVITY", "a1").test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun upsert_insertsRow() = runTest {
        dao.upsert(NoteEntity(target = "ACTIVITY", entityId = "a1", text = "hello"))
        dao.observe("ACTIVITY", "a1").test {
            assertEquals("hello", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun upsert_secondCallUpdatesText() = runTest {
        dao.upsert(NoteEntity(target = "ACTIVITY", entityId = "a1", text = "v1"))
        dao.upsert(NoteEntity(target = "ACTIVITY", entityId = "a1", text = "v2"))
        dao.observe("ACTIVITY", "a1").test {
            assertEquals("v2", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun delete_removesCorrectRow() = runTest {
        dao.upsert(NoteEntity(target = "MEDICATION", entityId = "m1", text = "note"))
        dao.upsert(NoteEntity(target = "MEDICATION", entityId = "m2", text = "other"))
        dao.delete("MEDICATION", "m1")
        dao.observe("MEDICATION", "m1").test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        dao.observe("MEDICATION", "m2").test {
            assertEquals("other", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun upsert_differentTargetsSameEntityId_areIndependent() = runTest {
        dao.upsert(NoteEntity(target = "ACTIVITY",  entityId = "x", text = "activity note"))
        dao.upsert(NoteEntity(target = "SCREENING", entityId = "x", text = "screening note"))
        dao.observe("ACTIVITY", "x").test {
            assertEquals("activity note", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        dao.observe("SCREENING", "x").test {
            assertEquals("screening note", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
