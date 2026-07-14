package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class GradientSliderRowTest {

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

    @Test fun `slider exposes progress bar semantics with current value`() = renderAndRetry(
        content = {
            GradientSliderRow(
                label         = "Energi",
                value         = 4f,
                onValueChange = {},
            )
        },
    ) {
        composeRule.onNodeWithContentDescription("Energi").assertIsDisplayed()
        val node = composeRule.onNodeWithContentDescription("Energi").fetchSemanticsNode()
        val rangeInfo = node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        assertEquals(4f, rangeInfo?.current)
        assertEquals(0f..10f, rangeInfo?.range)
    }

    @Test fun `setProgress semantics action updates value`() = retryOnRenderGlitch {
        // value is declared fresh inside the retry block (not shared via
        // renderAndRetry's closures) so a stale value from a glitched
        // earlier attempt can't leak into a later attempt's assertion.
        var value by mutableFloatStateOf(4f)
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        GradientSliderRow(
                            label         = "Energi",
                            value         = value,
                            onValueChange = { value = it },
                        )
                    }
                }
            }
            composeRule.onNodeWithContentDescription("Energi")
                .performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
            composeRule.waitForIdle()
            assertEquals(7f, value)
        } finally {
            scenario.close()
        }
    }

    @Test fun `disabled slider has no setProgress action`() = renderAndRetry(
        content = {
            GradientSliderRow(
                label         = "Energi",
                value         = 4f,
                onValueChange = {},
                enabled       = false,
            )
        },
    ) {
        val node = composeRule.onNodeWithContentDescription("Energi").fetchSemanticsNode()
        val hasSetProgress = node.config.getOrNull(SemanticsActions.SetProgress) != null
        assertEquals(false, hasSetProgress)
    }
}
