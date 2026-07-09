package se.partee71.dagboken.ui.aktiviteter

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class ScreeningTabTest {

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
        vm = AktiviteterViewModel(repo, noteRepo, prefs)
    }

    @After fun tearDown() {
        db.close()
    }

    private fun aktivitet(id: String, aktivitet: String, type: String = "screening") = Aktivitet(
        id = id, timestamp = "2026-01-15T09:00:00.000Z", datum = "2026-01-15", tid = "09:00",
        aktivitet = aktivitet, energy = 5, stress = 2, somatiska = 0,
        symptom = "", aterhamtande = false, energitjuv = false, type = type, spentTime = null,
    )

    private fun setContent() {
        composeRule.setContent { MaterialTheme { ScreeningTab(vm) } }
        composeRule.waitForIdle()
    }

    // ─── Senaste registreringar ──────────────────────────────────────────────

    @Test fun recent_entries_section_is_hidden_when_no_entries_exist() {
        setContent()
        composeRule.onNodeWithText("Senaste registreringar").assertDoesNotExist()
    }

    @Test fun recent_entries_section_shows_saved_entries_mixed_by_type() {
        runBlocking {
            repo.save(aktivitet("s1", "Morgonscreening", "screening"))
            repo.save(aktivitet("a1", "Promenad", "aktivitet"))
        }
        setContent()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Morgonscreening")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Senaste registreringar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Morgonscreening").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Promenad").performScrollTo().assertIsDisplayed()
    }

    @Test fun recent_entry_edit_menu_item_invokes_onEdit_with_id_and_type() {
        runBlocking { repo.save(aktivitet("s1", "Morgonscreening", "screening")) }
        var editedId: String? = null
        var editedType: String? = null
        composeRule.setContent {
            MaterialTheme { ScreeningTab(vm = vm, onEdit = { id, type -> editedId = id; editedType = type }) }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Morgonscreening")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Alternativ").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Redigera").performClick()
        composeRule.waitForIdle()
        assertEquals("s1", editedId)
        assertEquals("screening", editedType)
    }

    @Test fun recent_entry_delete_removes_it_after_confirmation() {
        runBlocking { repo.save(aktivitet("s1", "Morgonscreening", "screening")) }
        setContent()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Morgonscreening")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Alternativ").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ta bort").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ta bort").performClick()
        composeRule.waitUntil(10_000) { vm.recentEntries.value.none { it.id == "s1" } }

        composeRule.onNodeWithText("Morgonscreening").assertDoesNotExist()
    }

    // ─── Anteckning ──────────────────────────────────────────────────────────

    @Test fun note_placeholder_is_shown_when_no_note_entered() {
        setContent()
        composeRule.onNodeWithText("Lägg till en anteckning…").performScrollTo().assertIsDisplayed()
    }

    @Test fun typing_a_note_updates_the_form_state() {
        setContent()
        composeRule.onNodeWithText("Lägg till en anteckning…").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Yr och trött")
        composeRule.waitForIdle()
        assertEquals("Yr och trött", vm.form.value.note)
    }

    @Test fun saving_a_screening_persists_the_note_under_SCREENING_target() {
        setContent()
        composeRule.onNodeWithText("Efter frukost").performClick()
        composeRule.onNodeWithText("Lägg till en anteckning…").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Yr och trött")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Spara screening").performClick()
        composeRule.waitUntil(10_000) { vm.snackbar.value != null }
        val saved = runBlocking { db.noteDao().getAll() }
        assertEquals(1, saved.size)
        assertEquals("SCREENING", saved.first().target)
        assertEquals("Yr och trött", saved.first().text)
    }
}
