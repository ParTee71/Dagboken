package se.partee71.dagboken.ui.aktiviteter

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class LoggaTabTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: AktiviteterRepository
    private lateinit var vm: AktiviteterViewModel
    private lateinit var prefs: PreferencesRepository
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db  = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                  .allowMainThreadQueries().build()
        repo = AktiviteterRepository(db.aktivitetDao())
        val noteRepo = NoteRepository(db.noteDao())
        prefs = PreferencesRepository(ctx)
        runBlocking {
            // Reset shared DataStore so no leftover options cause duplicate "Övrigt" chips
            prefs.setAktivitetOptions(emptyList())
            prefs.setSymptomOptions(emptyList())
        }
        vm = AktiviteterViewModel(repo, noteRepo, prefs)
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun aktivitet(id: String, aktivitet: String, type: String = "aktivitet") = Aktivitet(
        id = id, timestamp = "2026-01-15T09:00:00.000Z", datum = "2026-01-15", tid = "09:00",
        aktivitet = aktivitet, energy = 5, stress = 2, somatiska = 0,
        symptom = "", aterhamtande = false, energitjuv = false, type = type, spentTime = null,
    )

    private fun tearDown() {
        runBlocking { prefs.setAktivitetOptions(emptyList()) }
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    private fun setContent(onEdit: (String, String) -> Unit = { _, _ -> }) {
        scenario.onActivity { it.setContent { MaterialTheme { LoggaTab(vm = vm, onEdit = onEdit) } } }
        composeRule.waitForIdle()
    }

    // ─── Spara inaktiverad utan typ ──────────────────────────────────────────

    @Test fun save_button_is_disabled_when_no_activity_type_is_selected() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Spara aktivitet").assertIsNotEnabled()
        } finally {
            tearDown()
        }
    }

    @Test fun save_button_is_enabled_after_selecting_an_activity_type() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
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

    // ─── Snackbar after save ──────────────────────────────────────────────────

    @Test fun save_fires_snackbar_with_activity_name() = retryOnRenderGlitch {
        setUp()
        try {
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
            composeRule.waitUntil(20_000) { vm.snackbar.value != null }
            val msg = vm.snackbar.value
            assertNotNull(msg)
            assertTrue("Expected snackbar to contain activity name, got: $msg", msg!!.contains("Promenad"))
        } finally {
            tearDown()
        }
    }

    // ─── "Övrigt"-fält ───────────────────────────────────────────────────────

    @Test fun Ovrigt_text_field_is_hidden_when_Ovrigt_chip_is_not_selected() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Beskriv aktivitet").assertDoesNotExist()
        } finally {
            tearDown()
        }
    }

    @Test fun Ovrigt_text_field_appears_when_Ovrigt_chip_is_selected() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Fler typer").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Övrigt").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Beskriv aktivitet").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    // ─── Chips ───────────────────────────────────────────────────────────────

    @Test fun Ovrigt_chip_is_always_shown() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Fler typer").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun predefined_activity_option_chips_are_shown() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                prefs.setAktivitetOptions(
                    listOf(SymptomOption("Promenad", isFavorite = true), SymptomOption("Simning", isFavorite = true)),
                )
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Promenad").assertIsDisplayed()
            composeRule.onNodeWithText("Simning").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    // ─── InputChips ──────────────────────────────────────────────────────────

    @Test fun Aterhamtande_and_Energitjuv_chips_are_shown() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Återhämtande").assertIsDisplayed()
            composeRule.onNodeWithText("Energitjuv").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun Aterhamtande_chip_toggles_when_clicked() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            assert(!vm.form.value.aterhamtande)
            composeRule.onNodeWithText("Återhämtande").performClick()
            composeRule.waitForIdle()
            assert(vm.form.value.aterhamtande)
        } finally {
            tearDown()
        }
    }

    // ─── Senaste registreringar ──────────────────────────────────────────────

    @Test fun recent_entries_section_is_hidden_when_no_entries_exist() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Senaste registreringar").assertDoesNotExist()
        } finally {
            tearDown()
        }
    }

    @Test fun recent_entries_section_shows_saved_entries_mixed_by_type() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.save(aktivitet("a1", "Promenad", "aktivitet"))
                repo.save(aktivitet("a2", "Morgonscreening", "screening"))
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Senaste registreringar").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Promenad").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Morgonscreening").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun recent_entry_edit_menu_item_invokes_onEdit_with_id_and_type() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking { repo.save(aktivitet("a1", "Promenad", "aktivitet")) }
            var editedId: String? = null
            var editedType: String? = null
            setContent(onEdit = { id, type -> editedId = id; editedType = type })
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("Alternativ").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Redigera").performClick()
            composeRule.waitForIdle()
            assertEquals("a1", editedId)
            assertEquals("aktivitet", editedType)
        } finally {
            tearDown()
        }
    }

    @Test fun recent_entry_delete_removes_it_after_confirmation() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking { repo.save(aktivitet("a1", "Promenad", "aktivitet")) }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithContentDescription("Alternativ").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Ta bort").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Ta bort").performClick()
            composeRule.waitUntil(20_000) { vm.recentEntries.value.none { it.id == "a1" } }

            composeRule.onNodeWithText("Promenad").assertDoesNotExist()
        } finally {
            tearDown()
        }
    }

    // ─── Anteckning ──────────────────────────────────────────────────────────

    @Test fun note_placeholder_is_shown_when_no_note_entered() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Lägg till en anteckning…").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun typing_a_note_updates_the_form_state() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Lägg till en anteckning…").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Regnigt väder")
            composeRule.waitForIdle()
            assertEquals("Regnigt väder", vm.form.value.note)
        } finally {
            tearDown()
        }
    }

    @Test fun saving_an_aktivitet_persists_the_note_under_ACTIVITY_target() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Fler typer").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Övrigt").performClick()
            composeRule.waitForIdle()
            vm.updateForm { copy(aktivitetAnnat = "Yoga") }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Lägg till en anteckning…").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNode(
                androidx.compose.ui.test.hasText("Lägg till en anteckning…")
                    and androidx.compose.ui.test.hasSetTextAction()
            ).performTextInput("Regnigt väder")
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Spara aktivitet").performClick()
            composeRule.waitUntil(20_000) { vm.snackbar.value != null }
            val saved = runBlocking { db.noteDao().getAll() }
            assertEquals(1, saved.size)
            assertEquals("ACTIVITY", saved.first().target)
            assertEquals("Regnigt väder", saved.first().text)
        } finally {
            tearDown()
        }
    }
}
