package se.partee71.dagboken.ui.diagram

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class MinMaxCaptionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun renderAndRetry(content: @Composable () -> Unit, assertions: () -> Unit) =
        retryOnRenderGlitch {
            val scenario = ActivityScenario.launch(ComponentActivity::class.java)
            try {
                scenario.onActivity { it.setContent { MaterialTheme { content() } } }
                assertions()
            } finally {
                scenario.close()
            }
        }

    @Test fun `shows min and max as whole numbers when they have no fraction`() = renderAndRetry(
        content = { MinMaxCaption(min = 2f, max = 9f) },
    ) {
        composeRule.onNodeWithText("Min: 2").assertIsDisplayed()
        composeRule.onNodeWithText("Max: 9").assertIsDisplayed()
    }

    @Test fun `shows one decimal when the value has a fraction`() = renderAndRetry(
        content = { MinMaxCaption(min = 2.5f, max = 8.25f) },
    ) {
        composeRule.onNodeWithText("Min: 2.5").assertIsDisplayed()
        composeRule.onNodeWithText("Max: 8.3").assertIsDisplayed()
    }

    @Test fun `renders when min equals max`() = renderAndRetry(
        content = { MinMaxCaption(min = 5f, max = 5f) },
    ) {
        composeRule.onNodeWithText("Min: 5").assertIsDisplayed()
        composeRule.onNodeWithText("Max: 5").assertIsDisplayed()
    }
}
