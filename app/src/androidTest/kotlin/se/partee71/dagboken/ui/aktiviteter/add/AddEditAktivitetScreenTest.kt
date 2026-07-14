package se.partee71.dagboken.ui.aktiviteter.add

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel
import se.partee71.dagboken.util.retryOnRenderGlitch

// Regression test for #68: AddEditAktivitetScreen wrapped LoggaTab in a Scaffold with its
// own bottomBar save button, duplicating LoggaTab's own save button.
// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class AddEditAktivitetScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: AktiviteterRepository
    private lateinit var vm: AktiviteterViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private var backCount = 0

    private fun setUp() {
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
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun tearDown() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { PreferencesRepository(ctx).setAktivitetOptions(emptyList()) }
        // Stop the ViewModel's Room-flow collectors before closing the DB, or they
        // query the closed in-memory DB and throw "attempt to re-open an already-closed
        // SQLiteDatabase" on a retry attempt.
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    private fun setContent(editId: String?) {
        scenario.onActivity {
            it.setContent { MaterialTheme { AddEditAktivitetScreen(editId = editId, onBack = { backCount++ }, vm = vm) } }
        }
    }

    @Test fun only_one_save_button_shown_when_adding_new_activity() = retryOnRenderGlitch {
        setUp()
        try {
            setContent(editId = null)
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText("Spara aktivitet").assertCountEquals(1)
        } finally {
            tearDown()
        }
    }

    @Test fun only_one_save_button_shown_when_editing_existing_activity() = retryOnRenderGlitch {
        setUp()
        try {
            val existing = Aktivitet(
                id = "a1", timestamp = "2026-07-01T08:00:00.000Z", datum = "2026-07-01", tid = "08:00",
                aktivitet = "Promenad", energy = 1, stress = 0, somatiska = 0, symptom = "",
            )
            runBlocking { repo.save(existing) }

            setContent(editId = "a1")
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Spara aktivitet")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithText("Spara aktivitet").assertCountEquals(1)
        } finally {
            tearDown()
        }
    }

    @Test fun save_button_is_disabled_when_no_activity_type_is_selected() = retryOnRenderGlitch {
        setUp()
        try {
            setContent(editId = null)
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Spara aktivitet").assertIsNotEnabled()
        } finally {
            tearDown()
        }
    }

    @Test fun save_button_is_enabled_after_selecting_an_activity_type() = retryOnRenderGlitch {
        setUp()
        try {
            setContent(editId = null)
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Fler typer").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Övrigt").performClick()
            composeRule.waitForIdle()
            vm.updateForm { copy(aktivitetAnnat = "Yoga") }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Spara aktivitet").assertIsEnabled()
        } finally {
            tearDown()
        }
    }

    @Test fun onBack_is_invoked_after_successful_save() = retryOnRenderGlitch {
        setUp()
        try {
            setContent(editId = null)
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
        } finally {
            tearDown()
        }
    }
}
