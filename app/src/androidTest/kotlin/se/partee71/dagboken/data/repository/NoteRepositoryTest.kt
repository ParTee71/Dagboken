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
        repo.observe(NoteTarget.DAY, "2026-06-24").test {
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
        repo.save(NoteTarget.EVENT, "e1", "some note")
        repo.save(NoteTarget.EVENT, "e1", "   ")
        repo.observe(NoteTarget.EVENT, "e1").test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun save_trimsWhitespace() = runTest {
        repo.save(NoteTarget.SCREENING, "2026-06-24", "  trimmed  ")
        repo.observe(NoteTarget.SCREENING, "2026-06-24").test {
            assertEquals("trimmed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
