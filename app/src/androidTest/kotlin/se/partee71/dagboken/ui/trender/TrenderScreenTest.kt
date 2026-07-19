package se.partee71.dagboken.ui.trender

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
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
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Aktivitet
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

    private fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                 .allowMainThreadQueries().build()
        repo = AktiviteterRepository(db.aktivitetDao())
        vm = TrenderViewModel(repo)
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
            composeRule.onNodeWithTag("trender_series_selector_energy").performClick()
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
            // testTag på knappen är ambiguös på grund av en text-baserad query mot
            // knappens vald-etikett om det finns en avvikande dubblettnod i trädet.
            composeRule.onNodeWithTag("trender_series_selector_symptom").performClick()
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
                composeRule.onAllNodes(hasText("Yrsel")).fetchSemanticsNodes().isNotEmpty()
            }
            // Legenden ligger under diagrammet i en scrollbar kolumn — scrolla in den
            // i vyn innan assertion, då den kan hamna under falsen på en liten emulator.
            composeRule.onNodeWithText("Yrsel").performScrollTo().assertIsDisplayed()
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
}
