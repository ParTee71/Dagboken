package se.partee71.dagboken.ui.handelser

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Handelse

@RunWith(AndroidJUnit4::class)
class AddEditHandelseScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: HandelserRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var vm: HandelserViewModel

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db    = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                    .allowMainThreadQueries().build()
        repo  = HandelserRepository(db.handelseDao())
        prefs = PreferencesRepository(ctx)
        runBlocking { prefs.setHandelseTypOptions(emptyList()) }
        vm    = HandelserViewModel(repo, NoteRepository(db.noteDao()), prefs)
    }

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        db.close()
        runBlocking { prefs.setHandelseTypOptions(emptyList()) }
    }

    private fun setContent() {
        composeRule.setContent {
            MaterialTheme { AddEditHandelseScreen(editId = null, onBack = {}, vm = vm) }
        }
    }

    @Test fun favourited_type_appears_as_filter_chip() {
        runBlocking {
            prefs.setHandelseTypOptions(listOf(SymptomOption("Yrsel", isFavorite = true)))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Yrsel")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Yrsel").assertIsDisplayed()
    }

    @Test fun clicking_favourite_chip_sets_typ_in_form() {
        runBlocking {
            prefs.setHandelseTypOptions(listOf(SymptomOption("Yrsel", isFavorite = true)))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Yrsel")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Yrsel").performClick()
        composeRule.waitUntil(3000) { vm.form.value.typ == "Yrsel" }
        assert(vm.form.value.typ == "Yrsel")
    }

    @Test fun non_favourite_types_appear_in_dropdown() {
        runBlocking {
            prefs.setHandelseTypOptions(listOf(SymptomOption("Andnöd", isFavorite = false)))
        }
        setContent()
        composeRule.onNodeWithText("Fler typer").assertIsDisplayed()
        composeRule.onNodeWithText("Fler typer").performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Andnöd")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Andnöd").performClick()
        composeRule.waitUntil(3000) { vm.form.value.typ == "Andnöd" }
        assert(vm.form.value.typ == "Andnöd")
    }

    @Test fun custom_db_type_not_in_managed_list_still_appears_in_dropdown() {
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
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Fler typer").performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Egen typ")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Egen typ").assertIsDisplayed()
    }

    @Test fun free_text_typ_field_still_allows_custom_value() {
        setContent()
        composeRule.onNode(hasText("Typ av händelse") and hasSetTextAction())
            .performTextInput("Helt ny typ")
        composeRule.waitUntil(3000) { vm.form.value.typ == "Helt ny typ" }
        assert(vm.form.value.typ == "Helt ny typ")
    }

    @Test fun note_field_is_shown_and_updates_form_state() {
        setContent()
        composeRule.onNodeWithText("Lägg till en anteckning…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Lägg till en anteckning…").performScrollTo().performClick()
        composeRule.onNode(hasText("Lägg till en anteckning…") and hasSetTextAction())
            .performTextInput("Kom efter möte")
        composeRule.waitUntil(3000) { vm.form.value.anteckning == "Kom efter möte" }
        assert(vm.form.value.anteckning == "Kom efter möte")
    }
}
