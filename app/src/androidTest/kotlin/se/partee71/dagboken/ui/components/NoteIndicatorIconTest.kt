package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class NoteIndicatorIconTest {

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

    @Test fun `icon is not shown when note text is blank`() = renderAndRetry(
        content = { NoteIndicatorIcon(noteText = "") },
    ) {
        composeRule.onNodeWithContentDescription("Visa anteckning").assertDoesNotExist()
    }

    @Test fun `icon is shown when note text is not blank`() = renderAndRetry(
        content = { NoteIndicatorIcon(noteText = "Kände mig yr") },
    ) {
        composeRule.onNodeWithContentDescription("Visa anteckning").assertIsDisplayed()
    }

    @Test fun `tapping the icon shows the note text in a dialog`() = renderAndRetry(
        content = { NoteIndicatorIcon(noteText = "Kände mig yr", dialogTitle = "Screening") },
    ) {
        composeRule.onNodeWithContentDescription("Visa anteckning").performClick()
        composeRule.onNodeWithText("Screening").assertIsDisplayed()
        composeRule.onNodeWithText("Kände mig yr").assertIsDisplayed()
    }

    @Test fun `closing the dialog hides the note text`() = renderAndRetry(
        content = { NoteIndicatorIcon(noteText = "Kände mig yr") },
    ) {
        composeRule.onNodeWithContentDescription("Visa anteckning").performClick()
        composeRule.onNodeWithText("Kände mig yr").assertIsDisplayed()
        composeRule.onNodeWithText("Stäng").performClick()
        composeRule.onNodeWithText("Kände mig yr").assertDoesNotExist()
    }
}
