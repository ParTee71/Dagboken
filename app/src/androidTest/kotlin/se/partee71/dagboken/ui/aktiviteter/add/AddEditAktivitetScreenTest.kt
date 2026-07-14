package se.partee71.dagboken.ui.aktiviteter.add

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel

// Regression test for #68: AddEditAktivitetScreen wrapped LoggaTab in a Scaffold with its
// own bottomBar save button, duplicating LoggaTab's own save button.
@RunWith(AndroidJUnit4::class)
class AddEditAktivitetScreenTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    private lateinit var db: AppDatabase
    private lateinit var repo: AktiviteterRepository
    private lateinit var vm: AktiviteterViewModel
    private var backCount = 0

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db   = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                   .allowMainThreadQueries().build()
        repo = AktiviteterRepository(db.aktivitetDao())
        val noteRepo = NoteRepository(db.noteDao())
        val prefs    = PreferencesRepository(ctx)
        runBlocking {
            prefs.setAktivitetOptions(emptyList())
            prefs.setSymptomOptions(emptyList())
        }
        vm = AktiviteterViewModel(repo, noteRepo, prefs)
        backCount = 0
    }

    @After fun tearDown() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { PreferencesRepository(ctx).setAktivitetOptions(emptyList()) }
        db.close()
    }

    @Test fun only_one_save_button_shown_when_adding_new_activity() {
        composeRule.setContent {
            MaterialTheme { AddEditAktivitetScreen(editId = null, onBack = { backCount++ }, vm = vm) }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Spara aktivitet").assertCountEquals(1)
    }

    @Test fun only_one_save_button_shown_when_editing_existing_activity() {
        val existing = Aktivitet(
            id = "a1", timestamp = "2026-07-01T08:00:00.000Z", datum = "2026-07-01", tid = "08:00",
            aktivitet = "Promenad", energy = 1, stress = 0, somatiska = 0, symptom = "",
        )
        runBlocking { repo.save(existing) }

        composeRule.setContent {
            MaterialTheme { AddEditAktivitetScreen(editId = "a1", onBack = { backCount++ }, vm = vm) }
        }
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodes(hasText("Spara aktivitet")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Spara aktivitet").assertCountEquals(1)
    }

    @Test fun save_button_is_disabled_when_no_activity_type_is_selected() {
        composeRule.setContent {
            MaterialTheme { AddEditAktivitetScreen(editId = null, onBack = { backCount++ }, vm = vm) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Spara aktivitet").assertIsNotEnabled()
    }

    @Test fun save_button_is_enabled_after_selecting_an_activity_type() {
        composeRule.setContent {
            MaterialTheme { AddEditAktivitetScreen(editId = null, onBack = { backCount++ }, vm = vm) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Fler typer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Övrigt").performClick()
        composeRule.waitForIdle()
        vm.updateForm { copy(aktivitetAnnat = "Yoga") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Spara aktivitet").assertIsEnabled()
    }

    @Test fun onBack_is_invoked_after_successful_save() {
        composeRule.setContent {
            MaterialTheme { AddEditAktivitetScreen(editId = null, onBack = { backCount++ }, vm = vm) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Fler typer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Övrigt").performClick()
        composeRule.waitForIdle()
        vm.updateForm { copy(aktivitetAnnat = "Yoga") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Spara aktivitet").performClick()
        composeRule.waitUntil(20_000) { backCount > 0 }
        assertTrue(backCount > 0)
    }
}
