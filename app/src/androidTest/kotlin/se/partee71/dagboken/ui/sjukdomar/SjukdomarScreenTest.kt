package se.partee71.dagboken.ui.sjukdomar

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.util.retryOnRenderGlitch

// Proof-of-concept for #112: createEmptyComposeRule() + a fresh
// ActivityScenario per retry attempt (see retryOnRenderGlitch), instead of
// the old RetryTestRule which could only mask a failed attempt behind
// composeRule's single-use coroutine TestScope, never actually recover it.
@RunWith(AndroidJUnit4::class)
class SjukdomarScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: SjukdomarRepository
    private lateinit var vm: SjukdomarViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = SjukdomarRepository(db.sjukdomsEpisodDao(), db.sjukdomsIncheckningDao(), Dispatchers.IO)
        vm = SjukdomarViewModel(repo, NoteRepository(db.noteDao()))
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun tearDown() {
        // Stop the ViewModel's Room-flow collectors (WhileSubscribed(5000))
        // before closing the DB, or they query the closed in-memory DB and throw
        // "attempt to re-open an already-closed SQLiteDatabase".
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    private fun setContent(onBack: () -> Unit = {}) {
        scenario.onActivity { activity ->
            activity.setContent {
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
    }

    @Test fun empty_state_shown_when_no_episodes() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Inga sjukdomsepisoder").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun pagaende_episode_is_listed() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.saveEpisod(SjukdomsEpisod(id = "e1", typ = "Migrän", startDatum = "2026-01-01", slutDatum = ""))
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Migrän")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Migrän").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun back_button_invokes_onBack() = retryOnRenderGlitch {
        setUp()
        try {
            var backCalled = false
            setContent(onBack = { backCalled = true })
            composeRule.onNodeWithContentDescription("Tillbaka").performClick()
            assert(backCalled) { "Expected onBack to be invoked" }
        } finally {
            tearDown()
        }
    }
}
