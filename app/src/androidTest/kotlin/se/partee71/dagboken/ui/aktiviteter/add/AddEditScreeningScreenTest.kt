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

// Regression test for #68: AddEditScreeningScreen wrapped ScreeningTab in a Scaffold with its
// own bottomBar save button, duplicating ScreeningTab's own save button.
@RunWith(AndroidJUnit4::class)
class AddEditScreeningScreenTest {

    @get:Rule val composeRule = createComposeRule()

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

    @Test fun only_one_save_button_shown_when_editing_existing_screening() {
        val existing = Aktivitet(
            id = "s1", timestamp = "2026-07-01T08:00:00.000Z", datum = "2026-07-01", tid = "08:00",
            aktivitet = "Efter frukost", energy = 5, stress = 2, somatiska = 0, symptom = "",
            type = "screening",
        )
        runBlocking { repo.save(existing) }

        composeRule.setContent {
            MaterialTheme { AddEditScreeningScreen(editId = "s1", onBack = { backCount++ }, vm = vm) }
        }
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Spara screening")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Spara screening").assertCountEquals(1)
    }

    @Test fun save_button_is_disabled_when_no_screening_event_is_selected() {
        val existing = Aktivitet(
            id = "s2", timestamp = "2026-07-01T08:00:00.000Z", datum = "2026-07-01", tid = "08:00",
            aktivitet = "", energy = 0, stress = 0, somatiska = 0, symptom = "", type = "screening",
        )
        runBlocking { repo.save(existing) }

        composeRule.setContent {
            MaterialTheme { AddEditScreeningScreen(editId = "s2", onBack = { backCount++ }, vm = vm) }
        }
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Spara screening")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Spara screening").assertIsNotEnabled()
    }

    @Test fun save_button_is_enabled_after_selecting_a_screening_event() {
        val existing = Aktivitet(
            id = "s3", timestamp = "2026-07-01T08:00:00.000Z", datum = "2026-07-01", tid = "08:00",
            aktivitet = "", energy = 0, stress = 0, somatiska = 0, symptom = "", type = "screening",
        )
        runBlocking { repo.save(existing) }

        composeRule.setContent {
            MaterialTheme { AddEditScreeningScreen(editId = "s3", onBack = { backCount++ }, vm = vm) }
        }
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Spara screening")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Lunch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Spara screening").assertIsEnabled()
    }

    @Test fun onBack_is_invoked_after_successful_save() {
        val existing = Aktivitet(
            id = "s4", timestamp = "2026-07-01T08:00:00.000Z", datum = "2026-07-01", tid = "08:00",
            aktivitet = "", energy = 0, stress = 0, somatiska = 0, symptom = "", type = "screening",
        )
        runBlocking { repo.save(existing) }

        composeRule.setContent {
            MaterialTheme { AddEditScreeningScreen(editId = "s4", onBack = { backCount++ }, vm = vm) }
        }
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Spara screening")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Läggdags").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Spara screening").performClick()
        composeRule.waitUntil(3000) { backCount > 0 }
        assertTrue(backCount > 0)
    }
}
