package se.partee71.dagboken.ui.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.DEFAULT_SCREENING_EVENTS
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.notifications.AlarmScheduler

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var prefs: PreferencesRepository
    private lateinit var vm: SettingsViewModel

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val authRepo = FirebaseAuthRepository(ctx)
        prefs = PreferencesRepository(ctx)

        prefs.setAktivitetOptions(emptyList())
        prefs.setSymptomOptions(emptyList())
        prefs.setMedsNotificationsEnabled(false)
        prefs.setScreeningEventConfigs(DEFAULT_SCREENING_EVENTS)
        prefs.setThemeMode("auto")

        val alarmScheduler = AlarmScheduler(ctx, prefs)
        vm = SettingsViewModel(prefs, authRepo, alarmScheduler)
    }

    @After fun tearDown() = runBlocking {
        prefs.setAktivitetOptions(emptyList())
        prefs.setSymptomOptions(emptyList())
    }

    private fun setContent() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(onBack = {}, onImport = {}, vm = vm)
            }
        }
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Inställningar")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ─── Tema-läge ────────────────────────────────────────────────────────────

    @Test fun themeMode_starts_with_auto() {
        setContent()
        assert(vm.state.value.themeMode == "auto") {
            "Expected themeMode=auto but got ${vm.state.value.themeMode}"
        }
    }

    @Test fun setThemeMode_dark_updates_ViewModel_state() {
        setContent()
        vm.setThemeMode("dark")
        composeRule.waitUntil(3000) { vm.state.value.themeMode == "dark" }
        assert(vm.state.value.themeMode == "dark") {
            "Expected themeMode=dark but got ${vm.state.value.themeMode}"
        }
    }

    // ─── Toggla notiser ───────────────────────────────────────────────────────

    @Test fun meds_notifications_row_is_displayed_and_starts_disabled() {
        setContent()
        // On large-screen layout the sidebar is shown; navigate to the Notifications section.
        // On small-screen layout all sections are in a scrollable column; scroll to the row.
        val railNodes = composeRule.onAllNodes(hasContentDescription("Påminnelser"))
        if (railNodes.fetchSemanticsNodes().isNotEmpty()) {
            railNodes.onFirst().performClick()
            composeRule.waitForIdle()
        } else {
            composeRule.onNodeWithText("Medicinpåminnelser").performScrollTo()
        }
        composeRule.onNodeWithText("Medicinpåminnelser").assertIsDisplayed()
        assert(!vm.state.value.medsNotificationsEnabled) {
            "Expected medsNotificationsEnabled=false initially"
        }
    }

    @Test fun toggleMedsNotifications_enables_meds_notifications() {
        setContent()
        vm.toggleMedsNotifications()
        composeRule.waitUntil(3000) { vm.state.value.medsNotificationsEnabled }
        assert(vm.state.value.medsNotificationsEnabled) {
            "Expected medsNotificationsEnabled=true after toggle"
        }
    }

    // ─── Lägg till aktivitetstyp ─────────────────────────────────────────────

    private fun navigateToAktivitetSection() {
        val railNodes = composeRule.onAllNodes(hasContentDescription("Aktivitetstyper"))
        if (railNodes.fetchSemanticsNodes().isNotEmpty()) {
            railNodes.onFirst().performClick()
            composeRule.waitForIdle()
        }
    }

    @Test fun added_aktivitet_option_chip_appears_in_list() {
        setContent()
        navigateToAktivitetSection()
        // Type into aktivitetOptions text field ("Ny typ") — makes the first "Lägg till" enabled
        composeRule.onNodeWithText("Ny typ").performTextInput("Yoga")
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Yoga")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Yoga").performScrollTo().assertIsDisplayed()
    }

    @Test fun duplicate_aktivitet_option_is_not_added() {
        setContent()
        navigateToAktivitetSection()
        composeRule.onNodeWithText("Ny typ").performTextInput("Yoga")
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
        composeRule.waitUntil(3000) { vm.state.value.aktivitetOptions.any { it.name == "Yoga" } }

        composeRule.onNodeWithText("Ny typ").performTextInput("Yoga")
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
        composeRule.waitForIdle()

        assert(vm.state.value.aktivitetOptions.count { it.name == "Yoga" } == 1) {
            "Expected exactly one Yoga but got: ${vm.state.value.aktivitetOptions}"
        }
    }

    // ─── Ta bort aktivitetstyp ───────────────────────────────────────────────

    @Test fun removed_aktivitet_option_chip_disappears_from_list() {
        setContent()
        navigateToAktivitetSection()
        // Add option via ViewModel to avoid DataStore → Flow → recomposition timing race
        composeRule.runOnUiThread {
            vm.setNewAktivitetOption("Simning")
            vm.addAktivitetOption()
        }
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Simning")).fetchSemanticsNodes().isNotEmpty()
        }
        // Scroll the chip into view before clicking "Ta bort"
        composeRule.onNode(hasContentDescription("Ta bort")).performScrollTo().let {
            composeRule.onNode(hasContentDescription("Ta bort")).performClick()
        }
        composeRule.waitUntil(3000) { vm.state.value.aktivitetOptions.isEmpty() }
        assert(vm.state.value.aktivitetOptions.isEmpty()) {
            "Expected empty aktivitetOptions but got: ${vm.state.value.aktivitetOptions}"
        }
    }
}
