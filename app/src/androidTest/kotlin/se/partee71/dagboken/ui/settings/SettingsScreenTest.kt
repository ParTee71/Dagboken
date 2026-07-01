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
        prefs.setHandelseTypOptions(emptyList())
        prefs.setMedsNotificationsEnabled(false)
        prefs.setScreeningEventConfigs(DEFAULT_SCREENING_EVENTS)
        prefs.setThemeMode("auto")

        val alarmScheduler = AlarmScheduler(ctx, prefs)
        vm = SettingsViewModel(prefs, authRepo, alarmScheduler)
    }

    @After fun tearDown() = runBlocking {
        prefs.setAktivitetOptions(emptyList())
        prefs.setSymptomOptions(emptyList())
        prefs.setHandelseTypOptions(emptyList())
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
        // Add the option through the same UI path as the passing add-test
        composeRule.onNodeWithText("Ny typ").performTextInput("Simning")
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Simning")).fetchSemanticsNodes().isNotEmpty()
        }
        // Tapping the delete icon opens a confirmation dialog; it does not delete directly
        // useUnmergedTree is incompatible with performScrollTo (which needs the merged tree);
        // wait for the button to be present in the merged tree, then scroll + click.
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasContentDescription("Ta bort")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasContentDescription("Ta bort")).performScrollTo().performClick()
        composeRule.waitForIdle()
        // Confirm deletion in the AlertDialog (confirm button is labelled "Ta bort")
        composeRule.onNodeWithText("Ta bort").performClick()
        composeRule.waitUntil(3000) { vm.state.value.aktivitetOptions.isEmpty() }
        assert(vm.state.value.aktivitetOptions.isEmpty()) {
            "Expected empty aktivitetOptions but got: ${vm.state.value.aktivitetOptions}"
        }
    }

    // ─── Händelsetyper ────────────────────────────────────────────────────────

    private fun navigateToHandelseTypSection() {
        val railNodes = composeRule.onAllNodes(hasContentDescription("Händelsetyper"))
        if (railNodes.fetchSemanticsNodes().isNotEmpty()) {
            railNodes.onFirst().performClick()
            composeRule.waitForIdle()
        }
    }

    @Test fun handelsetyper_section_is_visible() {
        setContent()
        navigateToHandelseTypSection()
        composeRule.onNodeWithText("Händelsetyper").performScrollTo().assertIsDisplayed()
    }

    @Test fun added_handelse_typ_option_appears_in_list() {
        setContent()
        navigateToHandelseTypSection()
        composeRule.onNodeWithText("Ny händelsetyp").performTextInput("Feberanfall")
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Feberanfall")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Feberanfall").performScrollTo().assertIsDisplayed()
    }

    @Test fun removed_handelse_typ_option_disappears_from_list() {
        setContent()
        navigateToHandelseTypSection()
        composeRule.onNodeWithText("Ny händelsetyp").performTextInput("Svimning")
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Svimning")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasContentDescription("Ta bort")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasContentDescription("Ta bort")).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ta bort").performClick()
        composeRule.waitUntil(3000) { vm.state.value.handelseTypOptions.isEmpty() }
        assert(vm.state.value.handelseTypOptions.isEmpty()) {
            "Expected empty handelseTypOptions but got: ${vm.state.value.handelseTypOptions}"
        }
    }

    @Test fun starred_handelse_typ_option_persists_as_favorite() {
        setContent()
        navigateToHandelseTypSection()
        composeRule.onNodeWithText("Ny händelsetyp").performTextInput("Näsblod")
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
        composeRule.waitUntil(3000) { vm.state.value.handelseTypOptions.any { it.name == "Näsblod" } }

        composeRule.onAllNodes(hasContentDescription("Favorit")).onFirst().performScrollTo().performClick()
        composeRule.waitUntil(3000) {
            vm.state.value.handelseTypOptions.any { it.name == "Näsblod" && it.isFavorite }
        }
        assert(vm.state.value.handelseTypOptions.single { it.name == "Näsblod" }.isFavorite) {
            "Expected Näsblod to be marked as favorite"
        }
    }
}
