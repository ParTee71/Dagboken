package se.partee71.dagboken.ui.health

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
import se.partee71.dagboken.domain.model.HealthData
import se.partee71.dagboken.util.retryOnRenderGlitch
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class HealthScreenTest {

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

    @Test fun data_state_shows_steps_and_heart_rate() = render(
        content = {
            HealthScreenContent(
                state = HealthUiState.Data(
                    HealthData(steps = 4200, heartRateAvg = 72, sleepDuration = Duration.ofMinutes(450)),
                ),
                onBack = {}, onGrantPermissions = {}, onRetry = {}, onOpenHealthConnect = {},
            )
        },
        assertions = {
            composeRule.onNodeWithText("Steg idag").assertIsDisplayed()
            composeRule.onNodeWithText("4200").assertIsDisplayed()
            composeRule.onNodeWithText("72 bpm").assertIsDisplayed()
            composeRule.onNodeWithText("7 h 30 min").assertIsDisplayed()
        },
    )

    @Test fun permissions_required_shows_grant_button_and_invokes_callback() {
        var granted = false
        render(
            content = {
                HealthScreenContent(
                    state = HealthUiState.PermissionsRequired,
                    onBack = {}, onGrantPermissions = { granted = true }, onRetry = {}, onOpenHealthConnect = {},
                )
            },
            assertions = {
                composeRule.onNodeWithText("Ge åtkomst").assertIsDisplayed().performClick()
                assert(granted) { "Expected onGrantPermissions to be invoked" }
            },
        )
    }

    @Test fun unavailable_shows_missing_message_and_open_button() {
        var opened = false
        render(
            content = {
                HealthScreenContent(
                    state = HealthUiState.Unavailable(updateRequired = false),
                    onBack = {}, onGrantPermissions = {}, onRetry = {}, onOpenHealthConnect = { opened = true },
                )
            },
            assertions = {
                composeRule.onNodeWithText("Health Connect saknas").assertIsDisplayed()
                composeRule.onNodeWithText("Öppna Health Connect").assertIsDisplayed().performClick()
                assert(opened) { "Expected onOpenHealthConnect to be invoked" }
            },
        )
    }

    @Test fun error_state_shows_retry_and_invokes_callback() {
        var retried = false
        render(
            content = {
                HealthScreenContent(
                    state = HealthUiState.Error,
                    onBack = {}, onGrantPermissions = {}, onRetry = { retried = true }, onOpenHealthConnect = {},
                )
            },
            assertions = {
                composeRule.onNodeWithText("Kunde inte läsa hälsodata").assertIsDisplayed()
                composeRule.onNodeWithText("Försök igen").assertIsDisplayed().performClick()
                assert(retried) { "Expected onRetry to be invoked" }
            },
        )
    }
}
