package se.partee71.dagboken.ui.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
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
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import se.partee71.dagboken.notifications.AlarmScheduler

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesRepository
    private lateinit var vm: SettingsViewModel

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                 .allowMainThreadQueries().build()
        val medicRepo = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
        val authRepo = FirebaseAuthRepository(ctx)
        prefs = PreferencesRepository(ctx)

        prefs.setAktivitetOptions(emptyList())
        prefs.setSymptomOptions(emptyList())
        prefs.setMedsNotificationsEnabled(false)
        prefs.setScreeningEventConfigs(DEFAULT_SCREENING_EVENTS)
        prefs.setThemeMode("auto")

        val alarmScheduler = AlarmScheduler(ctx, medicRepo, prefs)
        vm = SettingsViewModel(prefs, authRepo, alarmScheduler)
    }

    @After fun tearDown() = runBlocking {
        prefs.setAktivitetOptions(emptyList())
        prefs.setSymptomOptions(emptyList())
        db.close()
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

    @Test fun `themeMode starts with auto`() {
        setContent()
        assert(vm.state.value.themeMode == "auto") {
            "Expected themeMode=auto but got ${vm.state.value.themeMode}"
        }
    }

    @Test fun `setThemeMode dark updates ViewModel state`() {
        setContent()
        vm.setThemeMode("dark")
        composeRule.waitUntil(3000) { vm.state.value.themeMode == "dark" }
        assert(vm.state.value.themeMode == "dark") {
            "Expected themeMode=dark but got ${vm.state.value.themeMode}"
        }
    }

    // ─── Toggla notiser ───────────────────────────────────────────────────────

    @Test fun `meds notifications row is displayed and starts disabled`() {
        setContent()
        composeRule.onNodeWithText("Medicinpåminnelser").assertIsDisplayed()
        assert(!vm.state.value.medsNotificationsEnabled) {
            "Expected medsNotificationsEnabled=false initially"
        }
    }

    @Test fun `toggleMedsNotifications enables meds notifications`() {
        setContent()
        vm.toggleMedsNotifications()
        composeRule.waitUntil(3000) { vm.state.value.medsNotificationsEnabled }
        assert(vm.state.value.medsNotificationsEnabled) {
            "Expected medsNotificationsEnabled=true after toggle"
        }
    }

    // ─── Lägg till aktivitetstyp ─────────────────────────────────────────────

    @Test fun `added aktivitet option chip appears in list`() {
        setContent()
        // Type into aktivitetOptions text field ("Ny typ") — makes the first "Lägg till" enabled
        composeRule.onNodeWithText("Ny typ").performTextInput("Yoga")
        composeRule.waitForIdle()
        // Two "Lägg till" buttons exist (aktivitet + symptom); click the enabled one (aktivitet)
        composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Yoga")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Yoga").performScrollTo().assertIsDisplayed()
    }

    @Test fun `duplicate aktivitet option is not added`() {
        setContent()
        composeRule.onNodeWithText("Ny typ").performTextInput("Yoga")
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
        composeRule.waitUntil(3000) { vm.state.value.aktivitetOptions.contains("Yoga") }

        composeRule.onNodeWithText("Ny typ").performTextInput("Yoga")
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
        composeRule.waitForIdle()

        assert(vm.state.value.aktivitetOptions.count { it == "Yoga" } == 1) {
            "Expected exactly one Yoga but got: ${vm.state.value.aktivitetOptions}"
        }
    }

    // ─── Ta bort aktivitetstyp ───────────────────────────────────────────────

    @Test fun `removed aktivitet option chip disappears from list`() {
        runBlocking { prefs.setAktivitetOptions(listOf("Simning")) }
        setContent()
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
