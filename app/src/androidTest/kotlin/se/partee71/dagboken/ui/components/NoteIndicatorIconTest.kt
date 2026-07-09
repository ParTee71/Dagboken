package se.partee71.dagboken.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteIndicatorIconTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    @Test fun `icon is not shown when note text is blank`() {
        composeRule.setContent {
            MaterialTheme {
                NoteIndicatorIcon(noteText = "")
            }
        }
        composeRule.onNodeWithContentDescription("Visa anteckning").assertDoesNotExist()
    }

    @Test fun `icon is shown when note text is not blank`() {
        composeRule.setContent {
            MaterialTheme {
                NoteIndicatorIcon(noteText = "Kände mig yr")
            }
        }
        composeRule.onNodeWithContentDescription("Visa anteckning").assertIsDisplayed()
    }

    @Test fun `tapping the icon shows the note text in a dialog`() {
        composeRule.setContent {
            MaterialTheme {
                NoteIndicatorIcon(noteText = "Kände mig yr", dialogTitle = "Screening")
            }
        }
        composeRule.onNodeWithContentDescription("Visa anteckning").performClick()
        composeRule.onNodeWithText("Screening").assertIsDisplayed()
        composeRule.onNodeWithText("Kände mig yr").assertIsDisplayed()
    }

    @Test fun `closing the dialog hides the note text`() {
        composeRule.setContent {
            MaterialTheme {
                NoteIndicatorIcon(noteText = "Kände mig yr")
            }
        }
        composeRule.onNodeWithContentDescription("Visa anteckning").performClick()
        composeRule.onNodeWithText("Kände mig yr").assertIsDisplayed()
        composeRule.onNodeWithText("Stäng").performClick()
        composeRule.onNodeWithText("Kände mig yr").assertDoesNotExist()
    }
}
