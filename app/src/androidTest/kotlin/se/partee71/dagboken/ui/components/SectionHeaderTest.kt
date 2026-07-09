package se.partee71.dagboken.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SectionHeaderTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    @Test fun `text is displayed`() {
        composeRule.setContent {
            MaterialTheme {
                SectionHeader(text = "Konto")
            }
        }
        composeRule.onNodeWithText("Konto").assertIsDisplayed()
    }

    @Test fun `renders with a custom color without error`() {
        composeRule.setContent {
            MaterialTheme {
                SectionHeader(text = "Sammanfattning", color = Color.Red)
            }
        }
        composeRule.onNodeWithText("Sammanfattning").assertIsDisplayed()
    }
}
