package se.partee71.dagboken.ui.trender

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
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
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Aktivitet
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TrenderScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: AktiviteterRepository
    private lateinit var vm: TrenderViewModel

    private val today get() = LocalDate.now().toString()

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                 .allowMainThreadQueries().build()
        repo = AktiviteterRepository(db.aktivitetDao())
        vm = TrenderViewModel(repo)
    }

    @After fun tearDown() { db.close() }

    private fun setContent() {
        composeRule.setContent {
            MaterialTheme {
                TrenderScreen(onBack = {}, vm = vm)
            }
        }
    }

    @Test fun selector_lists_both_aktivitet_and_symptom_series() {
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
        composeRule.onNodeWithText("Visa:").assertIsDisplayed()
        // Opens the series dropdown — its trigger button shows the current selection label.
        composeRule.onNodeWithText("Energi Frukost").performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Yrsel")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Yrsel").assertIsDisplayed()
    }

    @Test fun selecting_a_symptom_series_adds_it_to_the_legend() {
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
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Yrsel")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Yrsel").assertIsDisplayed()
    }

    @Test fun range_chip_switches_selected_range() {
        setContent()
        composeRule.onNodeWithText("7 dagar").performClick()
        composeRule.waitUntil(3000) { vm.state.value.rangeDays == 7 }
    }

    @Test fun empty_state_shown_when_no_series_selected() {
        composeRule.runOnUiThread { vm.toggleSeries("Energi Frukost") }
        setContent()
        composeRule.onNodeWithText("Välj minst en dataserie").assertIsDisplayed()
    }
}
