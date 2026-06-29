package se.partee71.dagboken.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.NoteTarget

@RunWith(AndroidJUnit4::class)
class NoteRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: NoteRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = NoteRepository(db.noteDao())
    }

    @After fun tearDown() { db.close() }

    @Test fun observe_emitsEmptyStringWhenNoRow() = runTest {
        repo.observe(NoteTarget.ACTIVITY, "act-none").test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun save_persistsTextAndObserveReceivesIt() = runTest {
        repo.save(NoteTarget.ACTIVITY, "act1", "great session")
        repo.observe(NoteTarget.ACTIVITY, "act1").test {
            assertEquals("great session", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun save_blankTextDeletesRow() = runTest {
        repo.save(NoteTarget.MEDICATION, "m1", "some note")
        repo.save(NoteTarget.MEDICATION, "m1", "   ")
        repo.observe(NoteTarget.MEDICATION, "m1").test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun save_storesTextAsProvided() = runTest {
        repo.save(NoteTarget.SCREENING, "2026-06-24", "some note")
        repo.observe(NoteTarget.SCREENING, "2026-06-24").test {
            assertEquals("some note", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun delete_removesRow() = runTest {
        repo.save(NoteTarget.ACTIVITY, "a1", "note")
        repo.delete(NoteTarget.ACTIVITY, "a1")
        repo.observe(NoteTarget.ACTIVITY, "a1").test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
