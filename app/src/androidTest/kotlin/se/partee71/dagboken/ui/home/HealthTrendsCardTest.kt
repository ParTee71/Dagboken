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

/**
 * [HealthTrendsCard] — det gemensamma diagramkortet på Idag (HEM-17, #138): stegtrend,
 * vilopulstrend (HLS-7) och energitrend (HEM-7), i den ordningen.
 */
@RunWith(AndroidJUnit4::class)
class HealthTrendsCardTest {

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

    @Test fun trends_card_shows_step_trend_label_with_two_positive_days() = render(
        content = {
            HealthTrendsCard(
                weekly = WeeklyHealth(
                    dailySteps = listOf(
                        DailySteps(LocalDate.now().minusDays(1), 5000),
                        DailySteps(LocalDate.now(), 8200),
                    ),
                ),
                screeningPoints     = emptyList(),
                screeningLabels     = emptyList(),
                onNavigateToTrender = {},
            )
        },
        assertions = { composeRule.onNodeWithText("Steg senaste 7 dagarna").assertIsDisplayed() },
    )

    @Test fun trends_card_shows_resting_heart_rate_trend_label_with_two_known_days() = render(
        content = {
            HealthTrendsCard(
                weekly = WeeklyHealth(
                    dailyRestingHeartRate = listOf(
                        DailyRestingHeartRate(LocalDate.now().minusDays(1), 60),
                        DailyRestingHeartRate(LocalDate.now(), 58),
                    ),
                ),
                screeningPoints     = emptyList(),
                screeningLabels     = emptyList(),
                onNavigateToTrender = {},
            )
        },
        assertions = { composeRule.onNodeWithText("Vilopuls senaste 7 dagarna").assertIsDisplayed() },
    )

    @Test fun trends_card_hides_resting_heart_rate_trend_with_fewer_than_two_known_days() = render(
        content = {
            HealthTrendsCard(
                weekly = WeeklyHealth(
                    dailyRestingHeartRate = listOf(
                        DailyRestingHeartRate(LocalDate.now().minusDays(1), null),
                        DailyRestingHeartRate(LocalDate.now(), 58),
                    ),
                ),
                screeningPoints     = emptyList(),
                screeningLabels     = emptyList(),
                onNavigateToTrender = {},
            )
        },
        assertions = { composeRule.onNodeWithText("Vilopuls senaste 7 dagarna").assertDoesNotExist() },
    )

    @Test fun trends_card_shows_screening_fallback_text_when_fewer_than_2_points() = render(
        content = {
            HealthTrendsCard(
                weekly              = null,
                screeningPoints     = emptyList(),
                screeningLabels     = emptyList(),
                onNavigateToTrender = {},
            )
        },
        assertions = {
            composeRule.onNodeWithText("Energi senaste 7 dagarna").assertIsDisplayed()
            composeRule.onNodeWithText("Logga din första screening för att se trender").assertIsDisplayed()
        },
    )

    @Test fun trends_card_shows_only_energy_section_when_weekly_is_null() = render(
        content = {
            HealthTrendsCard(
                weekly              = null,
                screeningPoints     = listOf(3f, 5f),
                screeningLabels     = listOf("Mån", "Tis"),
                onNavigateToTrender = {},
            )
        },
        assertions = {
            composeRule.onNodeWithText("Steg senaste 7 dagarna").assertDoesNotExist()
            composeRule.onNodeWithText("Vilopuls senaste 7 dagarna").assertDoesNotExist()
            composeRule.onNodeWithText("Energi senaste 7 dagarna").assertIsDisplayed()
        },
    )

    @Test fun trends_card_shows_min_max_caption_for_energy_trend() = render(
        content = {
            HealthTrendsCard(
                weekly              = null,
                screeningPoints     = listOf(3f, 8f, 5f),
                screeningLabels     = listOf("Mån", "Tis", "Ons"),
                onNavigateToTrender = {},
            )
        },
        assertions = {
            composeRule.onNodeWithText("Min: 3").assertIsDisplayed()
            composeRule.onNodeWithText("Max: 8").assertIsDisplayed()
        },
    )

    @Test fun trends_card_shows_min_max_caption_for_step_trend() = render(
        content = {
            HealthTrendsCard(
                weekly = WeeklyHealth(
                    dailySteps = listOf(
                        DailySteps(LocalDate.now().minusDays(1), 5000),
                        DailySteps(LocalDate.now(), 8200),
                    ),
                ),
                screeningPoints     = emptyList(),
                screeningLabels     = emptyList(),
                onNavigateToTrender = {},
            )
        },
        assertions = {
            composeRule.onNodeWithText("Min: 5000").assertIsDisplayed()
            composeRule.onNodeWithText("Max: 8200").assertIsDisplayed()
        },
    )

    @Test fun trends_card_view_diagram_button_invokes_callback() {
        var navigated = false
        render(
            content = {
                HealthTrendsCard(
                    weekly              = null,
                    screeningPoints     = emptyList(),
                    screeningLabels     = emptyList(),
                    onNavigateToTrender = { navigated = true },
                )
            },
            assertions = {
                composeRule.onNodeWithText("Visa diagram →").assertIsDisplayed().performClick()
                assert(navigated) { "Expected onNavigateToTrender to be invoked" }
            },
        )
    }
}
