package se.partee71.dagboken.ui.aktiviteter

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Aktivitet

@RunWith(AndroidJUnit4::class)
class HistorikTabTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: AktiviteterRepository
    private lateinit var vm: AktiviteterViewModel

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db   = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                   .allowMainThreadQueries().build()
        repo = AktiviteterRepository(db.aktivitetDao())
        val noteRepo = NoteRepository(db.noteDao())
        val prefs    = PreferencesRepository(ctx)
        vm   = AktiviteterViewModel(repo, noteRepo, prefs)
    }

    @After fun tearDown() { db.close() }

    private fun aktivitet(id: String, aktivitet: String, type: String) = Aktivitet(
        id = id, timestamp = "2026-01-15T09:00:00.000Z",
        datum = "2026-01-15", tid = "09:00",
        aktivitet = aktivitet, energy = 5, stress = 2, somatiska = 0,
        symptom = "", aterhamtande = false, energitjuv = false,
        type = type, spentTime = null,
    )

    private fun setContent() {
        composeRule.setContent { MaterialTheme { HistorikTab(vm = vm, onEdit = { _, _ -> }) } }
    }

    // ─── No save bar on Historik tab ─────────────────────────────────────────

    @Test fun historik_tab_has_no_save_button() {
        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Spara aktivitet").assertDoesNotExist()
        composeRule.onNodeWithText("Spara screening").assertDoesNotExist()
    }

    // ─── Empty state ──────────────────────────────────────────────────────────

    @Test fun empty_state_shows_placeholder_text() {
        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Inga aktiviteter loggade").assertIsDisplayed()
    }

    // ─── Filter chips toggle ──────────────────────────────────────────────────

    @Test fun deselecting_Aktivitet_chip_hides_aktivitet_entries() {
        runBlocking {
            repo.save(aktivitet("a1", "Promenad", "aktivitet"))
            repo.save(aktivitet("a2", "Morgonscreening", "screening"))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Promenad").assertIsDisplayed()
        composeRule.onNodeWithText("Morgonscreening").assertIsDisplayed()

        composeRule.onNodeWithText("Aktivitet").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Promenad").assertIsNotDisplayed()
        composeRule.onNodeWithText("Morgonscreening").assertIsDisplayed()
    }

    @Test fun reselecting_Aktivitet_chip_shows_aktivitet_entries_again() {
        runBlocking { repo.save(aktivitet("b1", "Cykling", "aktivitet")) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Cykling")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Aktivitet").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cykling").assertIsNotDisplayed()

        composeRule.onNodeWithText("Aktivitet").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cykling").assertIsDisplayed()
    }

    // ─── Minst en typ kvar ────────────────────────────────────────────────────

    @Test fun Screening_chip_stays_selected_when_it_is_the_only_remaining_filter_type() {
        // Use a name that doesn't clash with the "Screening" filter chip label
        runBlocking { repo.save(aktivitet("c1", "Morgonkontroll", "screening")) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Morgonkontroll")).fetchSemanticsNodes().isNotEmpty()
        }
        // Remove "Aktivitet" filter — only "screening" type remains
        composeRule.onNodeWithText("Aktivitet").performClick()
        composeRule.waitForIdle()

        // "Screening" chip is the only remaining filter — clicking it should have no effect
        composeRule.onNodeWithText("Screening").performClick()
        composeRule.waitForIdle()

        assert("screening" in vm.historyFilter.value) {
            "Expected screening to remain in filter but got ${vm.historyFilter.value}"
        }
        composeRule.onNodeWithText("Screening").assertIsSelected()
    }
}
