package se.partee71.dagboken.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
import se.partee71.dagboken.util.retryOnRenderGlitch
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HealthTrendCardTest {

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

    @Test fun trend_card_shows_steps_today_and_resting_heart_rate() = render(
        content = {
            HealthTrendCard(
                WeeklyHealth(
                    dailySteps = listOf(
                        DailySteps(LocalDate.now().minusDays(1), 5000),
                        DailySteps(LocalDate.now(), 8200),
                    ),
                    restingHeartRate = 58,
                ),
            )
        },
        assertions = {
            composeRule.onNodeWithText("Steg idag").assertIsDisplayed()
            composeRule.onNodeWithText("8200").assertIsDisplayed()
            composeRule.onNodeWithText("Vilopuls").assertIsDisplayed()
            composeRule.onNodeWithText("58 bpm").assertIsDisplayed()
        },
    )

    @Test fun trend_card_shows_resting_heart_rate_trend_label_with_two_known_days() = render(
        content = {
            HealthTrendCard(
                WeeklyHealth(
                    dailyRestingHeartRate = listOf(
                        DailyRestingHeartRate(LocalDate.now().minusDays(1), 60),
                        DailyRestingHeartRate(LocalDate.now(), 58),
                    ),
                    restingHeartRate = 58,
                ),
            )
        },
        assertions = {
            composeRule.onNodeWithText("Vilopuls senaste 7 dagarna").assertIsDisplayed()
        },
    )

    @Test fun trend_card_hides_resting_heart_rate_trend_with_fewer_than_two_known_days() = render(
        content = {
            HealthTrendCard(
                WeeklyHealth(
                    dailyRestingHeartRate = listOf(
                        DailyRestingHeartRate(LocalDate.now().minusDays(1), null),
                        DailyRestingHeartRate(LocalDate.now(), 58),
                    ),
                    restingHeartRate = 58,
                ),
            )
        },
        assertions = {
            composeRule.onNodeWithText("Vilopuls senaste 7 dagarna").assertDoesNotExist()
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
