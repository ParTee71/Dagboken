package se.partee71.dagboken.ui.hantera

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.DEFAULT_SCREENING_EVENTS
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import se.partee71.dagboken.notifications.AlarmScheduler
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class HanteraScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var prefs: PreferencesRepository
    private lateinit var db: AppDatabase
    private lateinit var medicinerRepo: MedicinerRepository
    private lateinit var vm: HanteraViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val authRepo = FirebaseAuthRepository(ctx)
        prefs = PreferencesRepository(ctx)

        prefs.setAktivitetOptions(emptyList())
        prefs.setSymptomOptions(emptyList())
        prefs.setHandelseTypOptions(emptyList())
        prefs.setMedsNotificationsEnabled(false)
        prefs.setScreeningEventConfigs(DEFAULT_SCREENING_EVENTS)
        prefs.setThemeMode("auto")

        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        medicinerRepo = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            noteRepo           = NoteRepository(db.noteDao()),
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        val alarmScheduler = AlarmScheduler(ctx, prefs)
        vm = HanteraViewModel(prefs, authRepo, alarmScheduler, medicinerRepo)
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun tearDown() = runBlocking {
        prefs.setAktivitetOptions(emptyList())
        prefs.setSymptomOptions(emptyList())
        prefs.setHandelseTypOptions(emptyList())
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    private fun setContent(onOpenSjukdomar: () -> Unit = {}, onOpenSchema: () -> Unit = {}) {
        scenario.onActivity {
            it.setContent {
                MaterialTheme {
                    HanteraScreen(onImport = {}, onOpenSjukdomar = onOpenSjukdomar, onOpenSchema = onOpenSchema, vm = vm)
                }
            }
        }
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodes(hasText("Hantera")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ─── Tema-läge ────────────────────────────────────────────────────────────

    @Test fun themeMode_starts_with_auto() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            assert(vm.state.value.themeMode == "auto") {
                "Expected themeMode=auto but got ${vm.state.value.themeMode}"
            }
        } finally {
            tearDown()
        }
    }

    @Test fun setThemeMode_dark_updates_ViewModel_state() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            vm.setThemeMode("dark")
            composeRule.waitUntil(20_000) { vm.state.value.themeMode == "dark" }
            assert(vm.state.value.themeMode == "dark") {
                "Expected themeMode=dark but got ${vm.state.value.themeMode}"
            }
        } finally {
            tearDown()
        }
    }

    // ─── Toggla notiser ───────────────────────────────────────────────────────

    @Test fun meds_notifications_row_is_displayed_and_starts_disabled() = retryOnRenderGlitch {
        setUp()
        try {
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
        } finally {
            tearDown()
        }
    }

    @Test fun toggleMedsNotifications_enables_meds_notifications() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            vm.toggleMedsNotifications()
            composeRule.waitUntil(20_000) { vm.state.value.medsNotificationsEnabled }
            assert(vm.state.value.medsNotificationsEnabled) {
                "Expected medsNotificationsEnabled=true after toggle"
            }
        } finally {
            tearDown()
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

    @Test fun added_aktivitet_option_chip_appears_in_list() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            navigateToAktivitetSection()
            // Type into aktivitetOptions text field ("Ny typ") — makes the first "Lägg till" enabled
            composeRule.onNodeWithText("Ny typ").performTextInput("Yoga")
            composeRule.waitForIdle()
            composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Yoga")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Yoga").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun duplicate_aktivitet_option_is_not_added() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            navigateToAktivitetSection()
            composeRule.onNodeWithText("Ny typ").performTextInput("Yoga")
            composeRule.waitForIdle()
            composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
            composeRule.waitUntil(20_000) { vm.state.value.aktivitetOptions.any { it.name == "Yoga" } }

            composeRule.onNodeWithText("Ny typ").performTextInput("Yoga")
            composeRule.waitForIdle()
            composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
            composeRule.waitForIdle()

            assert(vm.state.value.aktivitetOptions.count { it.name == "Yoga" } == 1) {
                "Expected exactly one Yoga but got: ${vm.state.value.aktivitetOptions}"
            }
        } finally {
            tearDown()
        }
    }

    // ─── Ta bort aktivitetstyp ───────────────────────────────────────────────

    @Test fun removed_aktivitet_option_chip_disappears_from_list() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            navigateToAktivitetSection()
            // Add the option through the same UI path as the passing add-test
            composeRule.onNodeWithText("Ny typ").performTextInput("Simning")
            composeRule.waitForIdle()
            composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Simning")).fetchSemanticsNodes().isNotEmpty()
            }
            // Tapping the delete icon opens a confirmation dialog; it does not delete directly
            // useUnmergedTree is incompatible with performScrollTo (which needs the merged tree);
            // wait for the button to be present in the merged tree, then scroll + click.
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasContentDescription("Ta bort")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasContentDescription("Ta bort")).performScrollTo().performClick()
            composeRule.waitForIdle()
            // Confirm deletion in the AlertDialog (confirm button is labelled "Ta bort")
            composeRule.onNodeWithText("Ta bort").performClick()
            composeRule.waitUntil(20_000) { vm.state.value.aktivitetOptions.isEmpty() }
            assert(vm.state.value.aktivitetOptions.isEmpty()) {
                "Expected empty aktivitetOptions but got: ${vm.state.value.aktivitetOptions}"
            }
        } finally {
            tearDown()
        }
    }

    // ─── Vid behov-mediciner ─────────────────────────────────────────────────

    private fun navigateToVidBehovSection() {
        val railNodes = composeRule.onAllNodes(hasContentDescription("Vid behov-mediciner"))
        if (railNodes.fetchSemanticsNodes().isNotEmpty()) {
            railNodes.onFirst().performClick()
            composeRule.waitForIdle()
        }
    }

    @Test fun vidBehov_section_shows_empty_state_when_no_favoriter() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            navigateToVidBehovSection()
            composeRule.onNodeWithText("Inga vid behov-mediciner skapade ännu.").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun vidBehov_section_lists_existing_med_and_toggles_favorite() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                medicinerRepo.saveFavorit(
                    Favorit(
                        id = "fav1", namn = "Paracetamol", dos = "500", enhet = "mg",
                        tidpunkt = "Vid behov", minTidMellan = 0,
                        isFavorite = false,
                    )
                )
            }
            setContent()
            navigateToVidBehovSection()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Paracetamol")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasContentDescription("Favorit")).performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                runBlocking { medicinerRepo.getFavoritById("fav1")?.isFavorite == true }
            }
            val updated = runBlocking { medicinerRepo.getFavoritById("fav1") }
            assert(updated?.isFavorite == true) {
                "Expected isFavorite=true after toggling, got ${updated?.isFavorite}"
            }
        } finally {
            tearDown()
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

    @Test fun handelsetyper_section_is_visible() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            navigateToHandelseTypSection()
            composeRule.onNodeWithText("Händelsetyper").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun added_handelse_typ_option_appears_in_list() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            navigateToHandelseTypSection()
            composeRule.onNodeWithText("Ny händelsetyp").performTextInput("Feberanfall")
            composeRule.waitForIdle()
            composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Feberanfall")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Feberanfall").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun removed_handelse_typ_option_disappears_from_list() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            navigateToHandelseTypSection()
            composeRule.onNodeWithText("Ny händelsetyp").performTextInput("Svimning")
            composeRule.waitForIdle()
            composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Svimning")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasContentDescription("Ta bort")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasContentDescription("Ta bort")).performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Ta bort").performClick()
            composeRule.waitUntil(20_000) { vm.state.value.handelseTypOptions.isEmpty() }
            assert(vm.state.value.handelseTypOptions.isEmpty()) {
                "Expected empty handelseTypOptions but got: ${vm.state.value.handelseTypOptions}"
            }
        } finally {
            tearDown()
        }
    }

    @Test fun starred_handelse_typ_option_persists_as_favorite() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            navigateToHandelseTypSection()
            composeRule.onNodeWithText("Ny händelsetyp").performTextInput("Näsblod")
            composeRule.waitForIdle()
            composeRule.onNode(hasContentDescription("Lägg till") and isEnabled()).performClick()
            composeRule.waitUntil(20_000) { vm.state.value.handelseTypOptions.any { it.name == "Näsblod" } }

            composeRule.onAllNodes(hasContentDescription("Favorit")).onFirst().performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                vm.state.value.handelseTypOptions.any { it.name == "Näsblod" && it.isFavorite }
            }
            assert(vm.state.value.handelseTypOptions.single { it.name == "Näsblod" }.isFavorite) {
                "Expected Näsblod to be marked as favorite"
            }
        } finally {
            tearDown()
        }
    }

    // ─── Sjukdomar & Recept/scheman nav-kort ─────────────────────────────────

    private fun navigateToSection(title: String) {
        val railNodes = composeRule.onAllNodes(hasContentDescription(title))
        if (railNodes.fetchSemanticsNodes().isNotEmpty()) {
            railNodes.onFirst().performClick()
            composeRule.waitForIdle()
        }
    }

    @Test fun sjukdomar_nav_card_opens_sjukdomar() = retryOnRenderGlitch {
        setUp()
        try {
            var opened = false
            setContent(onOpenSjukdomar = { opened = true })
            navigateToSection("Sjukdomar")
            composeRule.onNodeWithText("Öppna sjukdomar").performScrollTo().performClick()
            assert(opened) { "Expected onOpenSjukdomar to be invoked" }
        } finally {
            tearDown()
        }
    }

    @Test fun schema_nav_card_opens_schema() = retryOnRenderGlitch {
        setUp()
        try {
            var opened = false
            setContent(onOpenSchema = { opened = true })
            navigateToSection("Recept & scheman")
            composeRule.onNodeWithText("Öppna recept & scheman").performScrollTo().performClick()
            assert(opened) { "Expected onOpenSchema to be invoked" }
        } finally {
            tearDown()
        }
    }
}
