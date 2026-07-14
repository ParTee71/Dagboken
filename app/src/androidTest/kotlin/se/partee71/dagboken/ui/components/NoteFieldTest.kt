package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class NoteFieldTest {

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

    @Test fun `placeholder is shown when text is empty and not expanded`() = renderAndRetry(
        content = { NoteField(text = "", onTextChange = {}) },
    ) {
        composeRule.onNodeWithText("Lägg till en anteckning…").assertIsDisplayed()
    }

    @Test fun `header label shown when text is not blank`() = renderAndRetry(
        content = { NoteField(text = "my note", onTextChange = {}) },
    ) {
        composeRule.onNodeWithText("Anteckning").assertIsDisplayed()
    }

    @Test fun `text preview shown collapsed when text is not blank`() = renderAndRetry(
        content = { NoteField(text = "some note text", onTextChange = {}) },
    ) {
        composeRule.onNodeWithText("some note text").assertIsDisplayed()
    }

    @Test fun `onTextChange called when user types in expanded field`() = retryOnRenderGlitch {
        // text/captured are declared fresh inside the retry block (not
        // shared via renderAndRetry's closures) so stale state from a
        // glitched earlier attempt can't leak into a later attempt.
        var captured = ""
        var text by mutableStateOf("")
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        NoteField(text = text, onTextChange = { text = it; captured = it })
                    }
                }
            }
            // Click the header row to expand
            composeRule.onNodeWithText("Lägg till en anteckning…").performClick()
            composeRule.waitForIdle()
            // Type into the now-visible OutlinedTextField
            composeRule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("hello")
            composeRule.waitForIdle()
            assertEquals("hello", captured)
        } finally {
            scenario.close()
        }
    }
}
