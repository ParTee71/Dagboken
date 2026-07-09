package se.partee71.dagboken.ui.sjukdomar

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.SjukdomsEpisod

@RunWith(AndroidJUnit4::class)
class SjukdomarScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: SjukdomarRepository
    private lateinit var vm: SjukdomarViewModel

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = SjukdomarRepository(db.sjukdomsEpisodDao(), db.sjukdomsIncheckningDao(), Dispatchers.IO)
        vm = SjukdomarViewModel(repo, NoteRepository(db.noteDao()))
    }

    @After fun tearDown() { db.close() }

    private fun setContent(onBack: () -> Unit = {}) {
        composeRule.setContent {
            MaterialTheme {
                SjukdomarScreen(
                    onBack            = onBack,
                    onAddNew          = {},
                    onDetail          = {},
                    snackbarHostState = SnackbarHostState(),
                    vm                = vm,
                )
            }
        }
    }

    @Test fun empty_state_shown_when_no_episodes() {
        setContent()
        composeRule.onNodeWithText("Inga sjukdomsepisoder").assertIsDisplayed()
    }

    @Test fun pagaende_episode_is_listed() {
        runBlocking {
            repo.saveEpisod(SjukdomsEpisod(id = "e1", typ = "Migrän", startDatum = "2026-01-01", slutDatum = ""))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Migrän")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Migrän").assertIsDisplayed()
    }

    @Test fun back_button_invokes_onBack() {
        var backCalled = false
        setContent(onBack = { backCalled = true })
        composeRule.onNodeWithContentDescription("Tillbaka").performClick()
        assert(backCalled) { "Expected onBack to be invoked" }
    }
}
