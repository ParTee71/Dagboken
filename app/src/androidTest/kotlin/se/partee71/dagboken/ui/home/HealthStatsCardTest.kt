package se.partee71.dagboken.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.domain.model.DailyRestingHeartRate
import se.partee71.dagboken.domain.model.DailySteps
import se.partee71.dagboken.domain.model.WeeklyHealth
import se.partee71.dagboken.ui.formatShortDate
import se.partee71.dagboken.util.retryOnRenderGlitch
import java.time.LocalDate

/** [HealthStatsCard] — Idag-hälsokortets steg/vilopuls för vald dag (HLS-7, HEM-15, #138). */
@RunWith(AndroidJUnit4::class)
class HealthStatsCardTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun render(content: @Composable () -> Unit, assertions: () -> Unit) =
        retryOnRenderGlitch {
            val scenario = ActivityScenario.launch(ComponentActivity::class.java)
            try {
                scenario.onActivity { it.setContent { MaterialTheme { content() } } }
                assertions()
            } finally {
                scenario.close()
            }
        }

    @Test fun stats_card_shows_steps_today_and_resting_heart_rate() = render(
        content = {
            HealthStatsCard(
                weekly = WeeklyHealth(
                    dailySteps = listOf(
                        DailySteps(LocalDate.now().minusDays(1), 5000),
                        DailySteps(LocalDate.now(), 8200),
                    ),
                    restingHeartRate = 58,
                ),
                selectedDate = LocalDate.now(),
                isToday      = true,
            )
        },
        assertions = {
            composeRule.onNodeWithText("Steg idag").assertIsDisplayed()
            composeRule.onNodeWithText("8200").assertIsDisplayed()
            composeRule.onNodeWithText("Vilopuls").assertIsDisplayed()
            composeRule.onNodeWithText("58 bpm").assertIsDisplayed()
        },
    )

    @Test fun stats_card_shows_a_past_days_values_and_a_date_label_when_not_today() = render(
        content = {
            val yesterday = LocalDate.now().minusDays(1)
            HealthStatsCard(
                weekly = WeeklyHealth(
                    dailySteps            = listOf(DailySteps(yesterday, 4000), DailySteps(LocalDate.now(), 8200)),
                    dailyRestingHeartRate = listOf(
                        DailyRestingHeartRate(yesterday, 55),
                        DailyRestingHeartRate(LocalDate.now(), 58),
                    ),
                    restingHeartRate = 58,
                ),
                selectedDate = yesterday,
                isToday      = false,
            )
        },
        assertions = {
            composeRule.onNodeWithText("4000").assertIsDisplayed()
            composeRule.onNodeWithText("55 bpm").assertIsDisplayed()
            composeRule.onNodeWithText("Steg idag").assertDoesNotExist()
            composeRule.onNodeWithText("Steg ${formatShortDate(LocalDate.now().minusDays(1))}").assertIsDisplayed()
        },
    )

    @Test fun stats_card_shows_dash_for_a_day_outside_the_fetched_window() = render(
        content = {
            HealthStatsCard(
                weekly = WeeklyHealth(
                    dailySteps            = listOf(DailySteps(LocalDate.now(), 8200)),
                    dailyRestingHeartRate = listOf(DailyRestingHeartRate(LocalDate.now(), 58)),
                    restingHeartRate      = 58,
                ),
                selectedDate = LocalDate.now().minusDays(10),
                isToday      = false,
            )
        },
        assertions = {
            composeRule.onAllNodesWithText("—").assertCountEquals(2)
        },
    )

    @Test fun connect_prompt_shows_and_invokes_callback() {
        var opened = false
        render(
            content = { HealthConnectPrompt(onClick = { opened = true }) },
            assertions = {
                composeRule.onNodeWithText("Koppla hälsa").assertIsDisplayed().performClick()
                assert(opened) { "Expected onClick to be invoked" }
            },
        )
    }
}
