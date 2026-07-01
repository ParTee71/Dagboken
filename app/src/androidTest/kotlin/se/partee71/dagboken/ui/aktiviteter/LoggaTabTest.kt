package se.partee71.dagboken.ui.aktiviteter

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Aktivitet

@RunWith(AndroidJUnit4::class)
class LoggaTabTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: AktiviteterRepository
    private lateinit var vm: AktiviteterViewModel

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db  = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                  .allowMainThreadQueries().build()
        repo = AktiviteterRepository(db.aktivitetDao())
        val noteRepo = NoteRepository(db.noteDao())
        val prefs    = PreferencesRepository(ctx)
        runBlocking {
            // Reset shared DataStore so no leftover options cause duplicate "Övrigt" chips
            prefs.setAktivitetOptions(emptyList())
            prefs.setSymptomOptions(emptyList())
        }
        vm = AktiviteterViewModel(repo, noteRepo, prefs)
    }

    private fun aktivitet(id: String, aktivitet: String, type: String = "aktivitet") = Aktivitet(
        id = id, timestamp = "2026-01-15T09:00:00.000Z", datum = "2026-01-15", tid = "09:00",
        aktivitet = aktivitet, energy = 5, stress = 2, somatiska = 0,
        symptom = "", aterhamtande = false, energitjuv = false, type = type, spentTime = null,
    )

    @After fun tearDown() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            PreferencesRepository(ctx).setAktivitetOptions(emptyList())
        }
        db.close()
    }

    private fun setContent() {
        composeRule.setContent { MaterialTheme { LoggaTab(vm) } }
        composeRule.waitForIdle()
    }

    // ─── Spara inaktiverad utan typ ──────────────────────────────────────────

    @Test fun save_button_is_disabled_when_no_activity_type_is_selected() {
        setContent()
        composeRule.onNodeWithText("Spara aktivitet").assertIsNotEnabled()
    }

    @Test fun save_button_is_enabled_after_selecting_an_activity_type() {
        setContent()
        composeRule.onNodeWithText("Fler typer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Övrigt").performClick()
        composeRule.waitForIdle()
        vm.updateForm { copy(aktivitetAnnat = "Yoga") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Spara aktivitet").assertIsEnabled()
    }

    // ─── Snackbar after save ──────────────────────────────────────────────────

    @Test fun save_fires_snackbar_with_activity_name() {
        setContent()
        composeRule.onNodeWithText("Fler typer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Övrigt").performClick()
        composeRule.waitForIdle()
        vm.updateForm { copy(aktivitetAnnat = "Promenad") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Spara aktivitet").performClick()
        // save() writes to the DB in a coroutine and only then sets the snackbar;
        // waitForIdle() does not wait for that coroutine, so poll until it lands.
        composeRule.waitUntil(3000) { vm.snackbar.value != null }
        val msg = vm.snackbar.value
        assertNotNull(msg)
        assertTrue("Expected snackbar to contain activity name, got: $msg", msg!!.contains("Promenad"))
    }

    // ─── "Övrigt"-fält ───────────────────────────────────────────────────────

    @Test fun Ovrigt_text_field_is_hidden_when_Ovrigt_chip_is_not_selected() {
        setContent()
        composeRule.onNodeWithText("Beskriv aktivitet").assertDoesNotExist()
    }

    @Test fun Ovrigt_text_field_appears_when_Ovrigt_chip_is_selected() {
        setContent()
        composeRule.onNodeWithText("Fler typer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Övrigt").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Beskriv aktivitet").assertIsDisplayed()
    }

    // ─── Chips ───────────────────────────────────────────────────────────────

    @Test fun Ovrigt_chip_is_always_shown() {
        setContent()
        composeRule.onNodeWithText("Fler typer").assertIsDisplayed()
    }

    @Test fun predefined_activity_option_chips_are_shown() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesRepository(ctx)
        runBlocking { prefs.setAktivitetOptions(listOf(SymptomOption("Promenad", isFavorite = true), SymptomOption("Simning", isFavorite = true))) }
        try {
            composeRule.setContent { MaterialTheme { LoggaTab(vm) } }
            composeRule.waitUntil(3000) {
                composeRule.onAllNodes(
                    hasText("Promenad")
                ).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Promenad").assertIsDisplayed()
            composeRule.onNodeWithText("Simning").assertIsDisplayed()
        } finally {
            runBlocking { prefs.setAktivitetOptions(emptyList()) }
        }
    }

    // ─── InputChips ──────────────────────────────────────────────────────────

    @Test fun Aterhamtande_and_Energitjuv_chips_are_shown() {
        setContent()
        composeRule.onNodeWithText("Återhämtande").assertIsDisplayed()
        composeRule.onNodeWithText("Energitjuv").assertIsDisplayed()
    }

    @Test fun Aterhamtande_chip_toggles_when_clicked() {
        setContent()
        assert(!vm.form.value.aterhamtande)
        composeRule.onNodeWithText("Återhämtande").performClick()
        composeRule.waitForIdle()
        assert(vm.form.value.aterhamtande)
    }

    // ─── Senaste registreringar ──────────────────────────────────────────────

    @Test fun recent_entries_section_is_hidden_when_no_entries_exist() {
        setContent()
        composeRule.onNodeWithText("Senaste registreringar").assertDoesNotExist()
    }

    @Test fun recent_entries_section_shows_saved_entries_mixed_by_type() {
        runBlocking {
            repo.save(aktivitet("a1", "Promenad", "aktivitet"))
            repo.save(aktivitet("a2", "Morgonscreening", "screening"))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Senaste registreringar").assertIsDisplayed()
        composeRule.onNodeWithText("Promenad").assertIsDisplayed()
        composeRule.onNodeWithText("Morgonscreening").assertIsDisplayed()
    }

    @Test fun recent_entry_edit_menu_item_invokes_onEdit_with_id_and_type() {
        runBlocking { repo.save(aktivitet("a1", "Promenad", "aktivitet")) }
        var editedId: String? = null
        var editedType: String? = null
        composeRule.setContent {
            MaterialTheme { LoggaTab(vm = vm, onEdit = { id, type -> editedId = id; editedType = type }) }
        }
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Alternativ").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Redigera").performClick()
        composeRule.waitForIdle()
        assertEquals("a1", editedId)
        assertEquals("aktivitet", editedType)
    }

    @Test fun recent_entry_delete_removes_it_after_confirmation() {
        runBlocking { repo.save(aktivitet("a1", "Promenad", "aktivitet")) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Alternativ").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ta bort").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ta bort").performClick()
        composeRule.waitUntil(3000) { vm.recentEntries.value.none { it.id == "a1" } }

        composeRule.onNodeWithText("Promenad").assertDoesNotExist()
    }
}
