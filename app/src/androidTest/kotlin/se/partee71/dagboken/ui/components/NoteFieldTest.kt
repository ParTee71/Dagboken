package se.partee71.dagboken.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteFieldTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    @Test fun `placeholder is shown when text is empty and not expanded`() {
        composeRule.setContent {
            MaterialTheme {
                NoteField(text = "", onTextChange = {})
            }
        }
        composeRule.onNodeWithText("Lägg till en anteckning…").assertIsDisplayed()
    }

    @Test fun `header label shown when text is not blank`() {
        composeRule.setContent {
            MaterialTheme {
                NoteField(text = "my note", onTextChange = {})
            }
        }
        composeRule.onNodeWithText("Anteckning").assertIsDisplayed()
    }

    @Test fun `text preview shown collapsed when text is not blank`() {
        composeRule.setContent {
            MaterialTheme {
                NoteField(text = "some note text", onTextChange = {})
            }
        }
        composeRule.onNodeWithText("some note text").assertIsDisplayed()
    }

    @Test fun `onTextChange called when user types in expanded field`() {
        var captured = ""
        var text by mutableStateOf("")
        composeRule.setContent {
            MaterialTheme {
                NoteField(text = text, onTextChange = { text = it; captured = it })
            }
        }
        // Click the header row to expand
        composeRule.onNodeWithText("Lägg till en anteckning…").performClick()
        composeRule.waitForIdle()
        // Type into the now-visible OutlinedTextField
        composeRule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("hello")
        composeRule.waitForIdle()
        assertEquals("hello", captured)
    }
}
