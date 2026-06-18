package se.partee71.dagboken.ui.aktiviteter

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import se.partee71.dagboken.data.room.AppDatabase

@RunWith(AndroidJUnit4::class)
class LoggaTabTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var vm: AktiviteterViewModel

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db  = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                  .allowMainThreadQueries().build()
        val repo  = AktiviteterRepository(db.aktivitetDao())
        val prefs = PreferencesRepository(ctx)
        runBlocking {
            // Reset shared DataStore so no leftover options cause duplicate "Övrigt" chips
            prefs.setAktivitetOptions(emptyList())
            prefs.setSymptomOptions(emptyList())
        }
        vm = AktiviteterViewModel(repo, prefs)
    }

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

    @Test fun `save button is disabled when no activity type is selected`() {
        setContent()
        composeRule.onNodeWithText("Spara aktivitet").assertIsNotEnabled()
    }

    @Test fun `save button is enabled after selecting an activity type`() {
        setContent()
        composeRule.onNodeWithText("Övrigt").performClick()
        vm.updateForm { copy(aktivitetAnnat = "Yoga") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Spara aktivitet").assertIsEnabled()
    }

    // ─── "Övrigt"-fält ───────────────────────────────────────────────────────

    @Test fun `Ovrigt text field is hidden when Ovrigt chip is not selected`() {
        setContent()
        composeRule.onNodeWithText("Beskriv aktivitet").assertDoesNotExist()
    }

    @Test fun `Ovrigt text field appears when Ovrigt chip is selected`() {
        setContent()
        composeRule.onNodeWithText("Övrigt").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Beskriv aktivitet").assertIsDisplayed()
    }

    // ─── Chips ───────────────────────────────────────────────────────────────

    @Test fun `Ovrigt chip is always shown`() {
        setContent()
        composeRule.onNodeWithText("Övrigt").assertIsDisplayed()
    }

    @Test fun `predefined activity option chips are shown`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesRepository(ctx)
        runBlocking { prefs.setAktivitetOptions(listOf("Promenad", "Simning")) }
        try {
            composeRule.setContent { MaterialTheme { LoggaTab(vm) } }
            composeRule.waitUntil(3000) {
                composeRule.onAllNodes(
                    androidx.compose.ui.test.hasText("Promenad")
                ).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Promenad").assertIsDisplayed()
            composeRule.onNodeWithText("Simning").assertIsDisplayed()
        } finally {
            runBlocking { prefs.setAktivitetOptions(emptyList()) }
        }
    }

    // ─── InputChips ──────────────────────────────────────────────────────────

    @Test fun `Aterhamtande and Energitjuv chips are shown`() {
        setContent()
        composeRule.onNodeWithText("Återhämtande").assertIsDisplayed()
        composeRule.onNodeWithText("Energitjuv").assertIsDisplayed()
    }

    @Test fun `Aterhamtande chip toggles when clicked`() {
        setContent()
        assert(!vm.form.value.aterhamtande)
        composeRule.onNodeWithText("Återhämtande").performClick()
        composeRule.waitForIdle()
        assert(vm.form.value.aterhamtande)
    }
}
