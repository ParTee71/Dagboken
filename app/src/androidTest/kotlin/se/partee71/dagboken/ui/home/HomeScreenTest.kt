package se.partee71.dagboken.ui.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.DEFAULT_SCREENING_EVENTS
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.DailyRestingHeartRate
import se.partee71.dagboken.domain.model.DailySteps
import se.partee71.dagboken.domain.model.HealthData
import se.partee71.dagboken.domain.model.WeeklyHealth
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.usecase.CheckCooldownUseCase
import se.partee71.dagboken.domain.usecase.CheckDailyLimitUseCase
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel
import se.partee71.dagboken.ui.mediciner.MedicinerViewModel
import se.partee71.dagboken.util.retryOnRenderGlitch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var aktivRepo: AktiviteterRepository
    private lateinit var medicRepo: MedicinerRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var authRepo: FirebaseAuthRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var sjukdomarRepo: SjukdomarRepository
    private lateinit var vm: HomeViewModel
    private lateinit var screeningVm: AktiviteterViewModel
    private lateinit var medicinerVm: MedicinerViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private val today get() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun setUp(healthRepo: HealthConnectRepository = FakeHealthRepo()) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                 .allowMainThreadQueries().build()
        aktivRepo = AktiviteterRepository(db.aktivitetDao())
        noteRepo  = NoteRepository(db.noteDao())
        medicRepo = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            noteRepo           = noteRepo,
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
        authRepo      = FirebaseAuthRepository(ctx)
        prefs         = PreferencesRepository(ctx)
        sjukdomarRepo = SjukdomarRepository(db.sjukdomsEpisodDao(), db.sjukdomsIncheckningDao(), Dispatchers.IO)
        runBlocking {
            prefs.setScreeningEventConfigs(DEFAULT_SCREENING_EVENTS)
            prefs.setMedsNotificationsEnabled(false)
        }
        vm          = HomeViewModel(aktivRepo, medicRepo, authRepo, prefs, sjukdomarRepo, healthRepo)
        screeningVm = AktiviteterViewModel(aktivRepo, noteRepo, prefs)
        medicinerVm = MedicinerViewModel(medicRepo, noteRepo, CheckCooldownUseCase(), CheckDailyLimitUseCase())
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun tearDown() {
        runBlocking {
            prefs.setScreeningEventConfigs(DEFAULT_SCREENING_EVENTS)
            prefs.setMedsNotificationsEnabled(false)
        }
        // Cancel before closing the DB, or a leaked viewModelScope coroutine (e.g.
        // HomeViewModel's date-navigation collector) queries the closed in-memory
        // DB and throws "attempt to re-open an already-closed SQLiteDatabase".
        vm.viewModelScope.cancel()
        screeningVm.viewModelScope.cancel()
        medicinerVm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    private fun setContent(onAddHandelse: (LocalDate) -> Unit = {}) {
        scenario.onActivity {
            it.setContent {
                MaterialTheme {
                    HomeScreen(
                        onNavigateToSettings    = {},
                        onNavigateToTrender     = {},
                        onNavigateToSjukdomar   = {},
                        onAddAktivitet          = {},
                        onAddMedicin            = {},
                        onAddHandelse           = onAddHandelse,
                        onAddFavorit            = {},
                        onEditFavorit           = {},
                        onOpenHalsa             = {},
                        snackbarHostState       = SnackbarHostState(),
                        vm                      = vm,
                        screeningVm             = screeningVm,
                        medicinerVm             = medicinerVm,
                    )
                }
            }
        }
    }

    // ─── Sparkline döljs < 2 punkter ─────────────────────────────────────────

    @Test fun sparkline_fallback_text_shown_when_fewer_than_2_screening_points() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Logga din första screening för att se trender")
                .assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    // ─── Försenat-kort ────────────────────────────────────────────────────────

    @Test fun Forsenat_card_shown_when_screening_notification_time_has_passed() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                prefs.setScreeningEventConfigs(listOf(ScreeningEventConfig(enabled = true, time = "00:01")))
            }
            setContent()
            composeRule.waitUntil(5000) {
                composeRule.onAllNodes(hasText("Försenat")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Försenat").assertIsDisplayed()
            composeRule.onNodeWithText("Efter frukost").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    // ─── Bock markerar tagen ──────────────────────────────────────────────────

    @Test fun toggleMedicinTagen_updates_tagen_count_displayed_in_card() = retryOnRenderGlitch {
        setUp()
        try {
            val med = Medicin(
                id        = "test-med-1",
                timestamp = "${today}T00:01:00.000Z",
                datum     = today,
                tid       = "00:01",
                namn      = "Metformin",
                dos       = "500",
                enhet     = "mg",
                tidpunkt  = "Morgon",
                tagen     = false,
            )
            runBlocking { medicRepo.saveMedicin(med) }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Metformin")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Metformin").assertIsDisplayed()

            composeRule.runOnUiThread { vm.toggleMedicinTagen(med) }
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Metformin")).fetchSemanticsNodes().isEmpty()
            }
        } finally {
            tearDown()
        }
    }

    // ─── Hela dagslistan visas, inte bara försenade ──────────────────────────

    @Test fun non_overdue_medicine_is_shown_in_checklist() = retryOnRenderGlitch {
        setUp()
        try {
            val med = Medicin(
                id = "future-med", timestamp = "${today}T23:00:00.000Z", datum = today, tid = "23:00",
                namn = "Vitamin D", dos = "1", enhet = "tablett", tidpunkt = "Kväll", tagen = false,
            )
            runBlocking { medicRepo.saveMedicin(med) }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Vitamin D")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Vitamin D").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    // ─── Inline screening-loggning ────────────────────────────────────────────

    @Test fun tapping_screening_row_expands_inline_stepwise_form_and_save_logs_it() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                prefs.setScreeningEventConfigs(listOf(ScreeningEventConfig(enabled = true, time = "08:00")))
                // Utan symptom blir det två steg (energi → stress); stresssteget visar Spara.
                prefs.setSymptomOptions(emptyList())
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Efter frukost")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Efter frukost").performScrollTo().performClick()
            // Steg 1 (energi) → Nästa → steg 2 (stress, sista) → Spara.
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithTag("screening_next").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("screening_next").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithTag("screening_save").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("screening_save").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Loggad")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Loggad").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    // ─── Global FAB ───────────────────────────────────────────────────────────

    @Test fun fab_opens_menu_with_four_quick_add_options() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithContentDescription("Lägg till").performClick()
            composeRule.onNodeWithText("Logga aktivitet").assertIsDisplayed()
            composeRule.onNodeWithText("Logga engångsdos").assertIsDisplayed()
            composeRule.onNodeWithText("Ny vid behov-favorit").assertIsDisplayed()
            composeRule.onNodeWithText("Ny händelse").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    // ─── Vid behov — snabbdosering ────────────────────────────────────────────

    @Test fun favorite_marked_favorit_is_shown_as_quick_dose_chip() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                medicRepo.saveFavorit(
                    Favorit(
                        id = "fav1", namn = "Paracetamol", dos = "500", enhet = "mg",
                        tidpunkt = "Vid behov", minTidMellan = 0, isFavorite = true,
                    )
                )
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Paracetamol")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Paracetamol").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun tapping_favorit_chip_logs_a_dose() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                medicRepo.saveFavorit(
                    Favorit(
                        id = "fav1", namn = "Ipren", dos = "400", enhet = "mg",
                        tidpunkt = "Vid behov", minTidMellan = 0, isFavorite = true,
                    )
                )
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Ipren")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Ipren").performClick()
            composeRule.waitUntil(20_000) {
                runBlocking { medicRepo.allMediciner.first().any { it.namn == "Ipren" && it.tagen } }
            }
        } finally {
            tearDown()
        }
    }

    // ─── Datumnavigering (#114) ───────────────────────────────────────────────

    @Test fun navigating_to_previous_day_shows_that_days_medicine_checklist() = retryOnRenderGlitch {
        setUp()
        try {
            val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val medYesterday = Medicin(
                id = "y1", timestamp = "${yesterday}T08:00:00.000Z", datum = yesterday, tid = "08:00",
                namn = "Levaxin", dos = "50", enhet = "mcg", tidpunkt = "Morgon", tagen = false,
            )
            runBlocking { medicRepo.saveMedicin(medYesterday) }
            setContent()
            composeRule.waitForIdle()

            composeRule.onNodeWithContentDescription("Föregående dag").performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Levaxin")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Levaxin").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun next_day_button_is_disabled_when_viewing_today() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("Nästa dag").assertIsNotEnabled()
        } finally {
            tearDown()
        }
    }

    @Test fun logging_a_screening_from_a_previous_day_saves_it_against_that_date() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                prefs.setScreeningEventConfigs(listOf(ScreeningEventConfig(enabled = true, time = "08:00")))
                prefs.setSymptomOptions(emptyList())
            }
            setContent()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("Föregående dag").performClick()

            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Efter frukost")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Efter frukost").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithTag("screening_next").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("screening_next").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithTag("screening_save").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("screening_save").performScrollTo().performClick()

            val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            composeRule.waitUntil(20_000) {
                runBlocking { aktivRepo.all.first().any { it.datum == yesterday && it.type == "screening" } }
            }
        } finally {
            tearDown()
        }
    }

    // ─── Idag-kortet: gruppering (#129) ───────────────────────────────────────

    @Test fun date_nav_row_is_positioned_above_medicine_checklist_in_the_same_card() = retryOnRenderGlitch {
        setUp()
        try {
            val med = Medicin(
                id = "grouping-med", timestamp = "${today}T08:00:00.000Z", datum = today, tid = "08:00",
                namn = "Levaxin", dos = "50", enhet = "mcg", tidpunkt = "Morgon", tagen = false,
            )
            runBlocking { medicRepo.saveMedicin(med) }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Levaxin")).fetchSemanticsNodes().isNotEmpty()
            }
            val dateNavTop = composeRule.onNodeWithContentDescription("Föregående dag").fetchSemanticsNode().boundsInRoot.top
            val medicinTop = composeRule.onNodeWithText("Levaxin").fetchSemanticsNode().boundsInRoot.top
            assert(dateNavTop < medicinTop) {
                "Datumnavigeringen ska ligga ovanför medicinchecklistan i samma kort ($dateNavTop < $medicinTop)"
            }
        } finally {
            tearDown()
        }
    }

    @Test fun navigating_to_previous_day_updates_health_stats_card_and_shows_merged_trends_card() = retryOnRenderGlitch {
        val yesterday = LocalDate.now().minusDays(1)
        val weekly = WeeklyHealth(
            dailySteps = listOf(
                DailySteps(yesterday, 4000),
                DailySteps(LocalDate.now(), 9000),
            ),
            dailyRestingHeartRate = listOf(
                DailyRestingHeartRate(yesterday, 55),
                DailyRestingHeartRate(LocalDate.now(), 60),
            ),
            restingHeartRate = 60,
        )
        setUp(FakeHealthRepo(weekly))
        try {
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("9000")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("9000").assertIsDisplayed()
            composeRule.onNodeWithText("Trender senaste 7 dagarna").assertIsDisplayed()

            composeRule.onNodeWithContentDescription("Föregående dag").performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("4000")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("4000").assertIsDisplayed()
            composeRule.onNodeWithText("55 bpm").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun health_prompt_is_positioned_directly_above_the_energy_diagram_card() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.waitForIdle()
            // FakeHealthRepo() → HealthAvailability.NOT_INSTALLED → "Koppla hälsa"-raden visas.
            val healthPromptTop = composeRule.onNodeWithText("Koppla hälsa").fetchSemanticsNode().boundsInRoot.top
            val energyDiagramTop = composeRule.onNodeWithText("Energi senaste 7 dagarna").fetchSemanticsNode().boundsInRoot.top
            assert(healthPromptTop < energyDiagramTop) {
                "Hälsokortet ska ligga direkt ovanför energidiagrammet ($healthPromptTop < $energyDiagramTop)"
            }
        } finally {
            tearDown()
        }
    }

    @Test fun new_handelse_fab_action_passes_the_currently_selected_date() = retryOnRenderGlitch {
        setUp()
        try {
            // capturedDate declared fresh inside the retry block, not shared via
            // setContent's default closures, so stale state from a glitched
            // earlier attempt can't leak into a later attempt.
            var capturedDate: LocalDate? = null
            setContent(onAddHandelse = { capturedDate = it })
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("Föregående dag").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithContentDescription("Lägg till").performClick()
            composeRule.onNodeWithText("Ny händelse").performClick()

            composeRule.waitUntil(5000) { capturedDate != null }
            assertEquals(LocalDate.now().minusDays(1), capturedDate)
        } finally {
            tearDown()
        }
    }
}

/**
 * Health Connect ej tillgängligt i emulator — fake så HomeScreen kan renderas. Med [weekly]
 * satt simuleras i stället en kopplad källa med den datan (#138 — datumbunden hälsokort-test).
 */
private class FakeHealthRepo(private val weekly: WeeklyHealth? = null) : HealthConnectRepository {
    override val permissions: Set<String> = emptySet()
    override fun availability() = if (weekly != null) HealthAvailability.AVAILABLE else HealthAvailability.NOT_INSTALLED
    override suspend fun hasAllPermissions() = weekly != null
    override suspend fun readToday() = HealthData()
    override suspend fun readWeeklyHealth() = weekly ?: WeeklyHealth()
}
