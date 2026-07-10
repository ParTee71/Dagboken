package se.partee71.dagboken.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GradientSliderRowTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    @Test fun `slider exposes progress bar semantics with current value`() {
        composeRule.setContent {
            MaterialTheme {
                GradientSliderRow(
                    label         = "Energi",
                    value         = 4f,
                    onValueChange = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Energi").assertIsDisplayed()
        val node = composeRule.onNodeWithContentDescription("Energi").fetchSemanticsNode()
        val rangeInfo = node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        assertEquals(4f, rangeInfo?.current)
        assertEquals(0f..10f, rangeInfo?.range)
    }

    @Test fun `setProgress semantics action updates value`() {
        var value by mutableFloatStateOf(4f)
        composeRule.setContent {
            MaterialTheme {
                GradientSliderRow(
                    label         = "Energi",
                    value         = value,
                    onValueChange = { value = it },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Energi")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        composeRule.waitForIdle()
        assertEquals(7f, value)
    }

    @Test fun `disabled slider has no setProgress action`() {
        composeRule.setContent {
            MaterialTheme {
                GradientSliderRow(
                    label         = "Energi",
                    value         = 4f,
                    onValueChange = {},
                    enabled       = false,
                )
            }
        }
        val node = composeRule.onNodeWithContentDescription("Energi").fetchSemanticsNode()
        val hasSetProgress = node.config.getOrNull(SemanticsActions.SetProgress) != null
        assertEquals(false, hasSetProgress)
    }
}
