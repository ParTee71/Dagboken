package se.partee71.dagboken.ui.trender

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.DailyRestingHeartRate
import se.partee71.dagboken.domain.model.DailySteps
import se.partee71.dagboken.domain.model.HealthData
import se.partee71.dagboken.domain.model.WeeklyHealth
import se.partee71.dagboken.util.retryOnRenderGlitch
import java.time.LocalDate

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class TrenderScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: AktiviteterRepository
    private lateinit var vm: TrenderViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private val today get() = LocalDate.now().toString()

    private fun setUp(healthRepo: HealthConnectRepository = FakeHealthRepo()) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                 .allowMainThreadQueries().build()
        repo = AktiviteterRepository(db.aktivitetDao())
        vm = TrenderViewModel(repo, healthRepo)
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun tearDown() {
        // Stop the ViewModel's Room-flow collector before closing the DB,
        // otherwise its viewModelScope coroutine queries the closed in-memory
        // DB and throws "attempt to re-open an already-closed SQLiteDatabase".
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    private fun setContent() {
        scenario.onActivity {
            it.setContent { MaterialTheme { TrenderScreen(onBack = {}, vm = vm) } }
        }
    }

    @Test fun energy_slot_section_shows_its_title() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Energi per tillfälle").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun energy_daily_section_renders_with_logged_screenings() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.save(
                    Aktivitet(
                        id = "s1", timestamp = "x", datum = today, tid = "09:00",
                        aktivitet = "Lunch", energy = 6, stress = 3, somatiska = 0,
                        symptom = "", type = "screening",
                    ),
                )
            }
            setContent()
            composeRule.onNodeWithText("Energi (dag)").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun energy_slot_selector_does_not_list_discovered_symptoms() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.save(
                    Aktivitet(
                        id = "a1", timestamp = "x", datum = today, tid = "09:00",
                        aktivitet = "Promenad", energy = 5, stress = 3, somatiska = 0,
                        symptom = "Yrsel:4", type = "aktivitet",
                    ),
                )
            }
            setContent()
            composeRule.onNodeWithTag("trender_series_selector_energy").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Energi Lunch")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Energi Lunch").assertIsDisplayed()
            composeRule.onNodeWithText("Yrsel").assertDoesNotExist()
        } finally {
            tearDown()
        }
    }

    @Test fun symptom_selector_lists_discovered_symptom_series() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.save(
                    Aktivitet(
                        id = "a1", timestamp = "x", datum = today, tid = "09:00",
                        aktivitet = "Promenad", energy = 5, stress = 3, somatiska = 0,
                        symptom = "Yrsel:4", type = "aktivitet",
                    ),
                )
            }
            setContent()
            // Symptom är den sista sektionen i den scrollbara kolumnen — scrolla in
            // knappen innan klick, annars kan touch-injektionen missa den.
            composeRule.onNodeWithTag("trender_series_selector_symptom").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Yrsel")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Yrsel").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun selecting_a_symptom_series_adds_it_to_the_legend() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.save(
                    Aktivitet(
                        id = "a1", timestamp = "x", datum = today, tid = "09:00",
                        aktivitet = "Promenad", energy = 5, stress = 3, somatiska = 0,
                        symptom = "Yrsel:4", type = "aktivitet",
                    ),
                )
            }
            setContent()
            composeRule.runOnUiThread { vm.toggleSeries("Yrsel") }
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasTestTag("trender_legend_item_Yrsel")).fetchSemanticsNodes().isNotEmpty()
            }
            // Legenden ligger under diagrammet i en scrollbar kolumn — scrolla in den i
            // vyn innan assertion. Egen testTag eftersom väljarknappens etikett också blir
            // exakt "Yrsel" när det är den enda valda serien i symptomkategorin.
            composeRule.onNodeWithTag("trender_legend_item_Yrsel").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun range_chip_switches_selected_range() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("7 dagar").performClick()
            composeRule.waitUntil(20_000) { vm.state.value.rangeDays == 7 }
        } finally {
            tearDown()
        }
    }

    @Test fun empty_state_shown_in_energy_slot_section_when_no_series_selected() = retryOnRenderGlitch {
        setUp()
        try {
            composeRule.runOnUiThread { vm.toggleSeries("Energi Frukost") }
            setContent()
            // Stress- och symptomdiagrammen har heller inget valt som standard, så
            // samma tomlägestext kan visas i flera sektioner samtidigt.
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithText("Välj minst en dataserie").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            tearDown()
        }
    }

    // ─── Steg/vilopuls (Health Connect) — TRD-10, #146 ────────────────────────

    @Test fun steps_and_resting_hr_sections_show_empty_state_when_health_connect_not_connected() = retryOnRenderGlitch {
        setUp(FakeHealthRepo(weekly = null))
        try {
            setContent()
            composeRule.onNodeWithText("Steg").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Vilopuls").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Ingen stegdata för vald period").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun steps_and_resting_hr_sections_render_when_health_connect_data_available() = retryOnRenderGlitch {
        val weekly = WeeklyHealth(
            dailySteps = listOf(
                DailySteps(LocalDate.now().minusDays(1), 4000),
                DailySteps(LocalDate.now(), 9000),
            ),
            dailyRestingHeartRate = listOf(
                DailyRestingHeartRate(LocalDate.now().minusDays(1), 55),
                DailyRestingHeartRate(LocalDate.now(), 60),
            ),
        )
        setUp(FakeHealthRepo(weekly))
        try {
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Ingen stegdata för vald period")).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithText("Steg").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Vilopuls").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }
}

/** Health Connect ej tillgängligt i emulator — fake så Trender-skärmen kan renderas (analog med HomeScreenTest). */
private class FakeHealthRepo(private val weekly: WeeklyHealth? = null) : HealthConnectRepository {
    override val permissions: Set<String> = emptySet()
    override fun availability() = if (weekly != null) HealthAvailability.AVAILABLE else HealthAvailability.NOT_INSTALLED
    override suspend fun hasAllPermissions() = weekly != null
    override suspend fun readToday() = HealthData()
    override suspend fun readWeeklyHealth() = weekly ?: WeeklyHealth()
    override suspend fun readHealthRange(days: Int) = weekly ?: WeeklyHealth()
}
