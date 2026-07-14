package se.partee71.dagboken.ui.handelser

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class AddEditHandelseScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: HandelserRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var vm: HandelserViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db    = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                    .allowMainThreadQueries().build()
        repo  = HandelserRepository(db.handelseDao())
        prefs = PreferencesRepository(ctx)
        runBlocking { prefs.setHandelseTypOptions(emptyList()) }
        vm    = HandelserViewModel(repo, NoteRepository(db.noteDao()), prefs)
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun tearDown() {
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
        runBlocking { prefs.setHandelseTypOptions(emptyList()) }
    }

    private fun setContent(onBack: () -> Unit = {}, prefillDatum: String? = null) {
        scenario.onActivity {
            it.setContent {
                MaterialTheme {
                    AddEditHandelseScreen(editId = null, onBack = onBack, prefillDatum = prefillDatum, vm = vm)
                }
            }
        }
    }

    @Test fun favourited_type_appears_as_filter_chip() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                prefs.setHandelseTypOptions(listOf(SymptomOption("Yrsel", isFavorite = true)))
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Yrsel")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Yrsel").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun clicking_favourite_chip_sets_typ_in_form() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                prefs.setHandelseTypOptions(listOf(SymptomOption("Yrsel", isFavorite = true)))
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Yrsel")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Yrsel").performClick()
            composeRule.waitUntil(20_000) { vm.form.value.typ == "Yrsel" }
            assert(vm.form.value.typ == "Yrsel")
        } finally {
            tearDown()
        }
    }

    @Test fun non_favourite_types_appear_in_dropdown() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                prefs.setHandelseTypOptions(listOf(SymptomOption("Andnöd", isFavorite = false)))
            }
            setContent()
            // "Fler typer" renders only once the cold WhileSubscribed typ-options flow
            // emits, so poll for it instead of asserting on the first frame.
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Fler typer")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Fler typer").assertIsDisplayed()
            composeRule.onNodeWithText("Fler typer").performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Andnöd")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Andnöd").performClick()
            composeRule.waitUntil(20_000) { vm.form.value.typ == "Andnöd" }
            assert(vm.form.value.typ == "Andnöd")
        } finally {
            tearDown()
        }
    }

    @Test fun custom_db_type_not_in_managed_list_still_appears_in_dropdown() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                prefs.setHandelseTypOptions(listOf(SymptomOption("Yrsel", isFavorite = true)))
                repo.save(
                    Handelse(
                        id = "h1", timestamp = "2026-06-21T10:00:00.000Z",
                        datum = "2026-06-21", tid = "10:00", typ = "Egen typ",
                        svarighetsgrad = 5, varaktighetMinuter = 0,
                        triggers = "", atgarder = "",
                    )
                )
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Fler typer")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Fler typer").performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Egen typ")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Egen typ").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun free_text_typ_field_still_allows_custom_value() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNode(hasText("Typ av händelse") and hasSetTextAction())
                .performTextInput("Helt ny typ")
            composeRule.waitUntil(20_000) { vm.form.value.typ == "Helt ny typ" }
            assert(vm.form.value.typ == "Helt ny typ")
        } finally {
            tearDown()
        }
    }

    @Test fun note_field_is_shown_and_updates_form_state() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Lägg till en anteckning…").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Lägg till en anteckning…").performScrollTo().performClick()
            composeRule.onNode(hasText("Lägg till en anteckning…") and hasSetTextAction())
                .performTextInput("Kom efter möte")
            composeRule.waitUntil(20_000) { vm.form.value.anteckning == "Kom efter möte" }
            assert(vm.form.value.anteckning == "Kom efter möte")
        } finally {
            tearDown()
        }
    }

    // ─── Spara-knapp (dirty-state) och osparade ändringar ────────────────────

    @Test fun save_button_is_disabled_until_the_form_has_unsaved_changes() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Spara").assertIsNotEnabled()
            composeRule.onNode(hasText("Typ av händelse") and hasSetTextAction())
                .performTextInput("Yrsel")
            composeRule.waitUntil(20_000) { vm.isDirty.value }
            composeRule.onNodeWithText("Spara").assertIsEnabled()
        } finally {
            tearDown()
        }
    }

    @Test fun back_with_unsaved_changes_shows_confirmation_dialog() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNode(hasText("Typ av händelse") and hasSetTextAction())
                .performTextInput("Yrsel")
            composeRule.waitUntil(20_000) { vm.isDirty.value }

            composeRule.onNodeWithContentDescription("Tillbaka").performClick()

            composeRule.onNodeWithText("Osparade ändringar").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun back_without_changes_navigates_immediately_without_a_dialog() = retryOnRenderGlitch {
        setUp()
        try {
            var backCalled = false
            setContent(onBack = { backCalled = true })

            composeRule.onNodeWithContentDescription("Tillbaka").performClick()

            assert(backCalled)
            composeRule.onNodeWithText("Osparade ändringar").assertDoesNotExist()
        } finally {
            tearDown()
        }
    }

    @Test fun discarding_unsaved_changes_navigates_back_without_saving() = retryOnRenderGlitch {
        setUp()
        try {
            var backCalled = false
            setContent(onBack = { backCalled = true })
            composeRule.onNode(hasText("Typ av händelse") and hasSetTextAction())
                .performTextInput("Yrsel")
            composeRule.waitUntil(20_000) { vm.isDirty.value }
            composeRule.onNodeWithContentDescription("Tillbaka").performClick()
            composeRule.onNodeWithText("Osparade ändringar").assertIsDisplayed()

            composeRule.onNodeWithText("Kasta").performClick()

            assert(backCalled)
            composeRule.waitUntil(20_000) { vm.form.value.typ.isEmpty() }
        } finally {
            tearDown()
        }
    }

    @Test fun saving_from_unsaved_changes_dialog_persists_and_navigates_back() = retryOnRenderGlitch {
        setUp()
        try {
            var backCalled = false
            setContent(onBack = { backCalled = true })
            composeRule.onNode(hasText("Typ av händelse") and hasSetTextAction())
                .performTextInput("Yrsel")
            composeRule.waitUntil(20_000) { vm.isDirty.value }
            composeRule.onNodeWithContentDescription("Tillbaka").performClick()
            composeRule.onNodeWithText("Osparade ändringar").assertIsDisplayed()

            composeRule.onNode(hasText("Spara") and hasAnyAncestor(isDialog())).performClick()

            composeRule.waitUntil(20_000) { backCalled }
            runBlocking {
                assert(repo.all.first().any { it.typ == "Yrsel" })
            }
        } finally {
            tearDown()
        }
    }

    // ─── prefillDatum (#114 — öppnad från en tidigare dags Idag-vy) ───────────

    @Test fun prefillDatum_sets_the_date_row_to_the_given_date() = retryOnRenderGlitch {
        setUp()
        try {
            val yesterday = java.time.LocalDate.now().minusDays(1).toString()
            setContent(prefillDatum = yesterday)
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Igår")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Igår").assertIsDisplayed()
            assert(vm.form.value.datum == yesterday)
        } finally {
            tearDown()
        }
    }

    @Test fun prefillDatum_does_not_mark_the_form_as_dirty() = retryOnRenderGlitch {
        setUp()
        try {
            setContent(prefillDatum = java.time.LocalDate.now().minusDays(1).toString())
            composeRule.waitForIdle()
            assert(!vm.isDirty.value)
        } finally {
            tearDown()
        }
    }
}
