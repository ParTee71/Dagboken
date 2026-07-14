package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112: createEmptyComposeRule() + en färsk
// ActivityScenario per retryOnRenderGlitch-försök, i stället för
// RetryTestRule (som bara kan maska ett misslyckat försök, aldrig
// återhämta det — se SjukdomarScreenTest för fullständig förklaring).
@RunWith(AndroidJUnit4::class)
class SectionHeaderTest {

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

    @Test fun `text is displayed`() = renderAndRetry(
        content = { SectionHeader(text = "Konto") },
    ) {
        composeRule.onNodeWithText("Konto").assertIsDisplayed()
    }

    @Test fun `renders with a custom color without error`() = renderAndRetry(
        content = { SectionHeader(text = "Sammanfattning", color = Color.Red) },
    ) {
        composeRule.onNodeWithText("Sammanfattning").assertIsDisplayed()
    }
}
