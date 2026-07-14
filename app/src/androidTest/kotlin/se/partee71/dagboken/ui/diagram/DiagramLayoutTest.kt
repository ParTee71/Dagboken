package se.partee71.dagboken.ui.diagram

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class DiagramLayoutTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val portrait  = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }
    private val landscape = Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }

    private fun layoutAndRetry(config: Configuration, withExtras: Boolean = false, assertions: () -> Unit) =
        retryOnRenderGlitch {
            val scenario = ActivityScenario.launch(ComponentActivity::class.java)
            try {
                scenario.onActivity {
                    it.setContent {
                        MaterialTheme {
                            CompositionLocalProvider(LocalConfiguration provides config) {
                                DiagramLayout(
                                    title          = "Testdiagram",
                                    onBack         = {},
                                    selector       = { Text("Selektor") },
                                    rangeChips     = { Text("Chips") },
                                    chart          = { _ -> Text("Diagram") },
                                    legend         = { Text("Legend") },
                                    portraitExtras = if (withExtras) ({ Text("Extras") }) else null,
                                )
                            }
                        }
                    }
                }
                assertions()
            } finally {
                scenario.close()
            }
        }

    // ─── Portrait ──────────────────────────────────────────────────────────────

    @Test fun portrait_shows_title_in_TopAppBar() = layoutAndRetry(portrait) {
        composeRule.onNodeWithText("Testdiagram").assertIsDisplayed()
    }

    @Test fun portrait_shows_selector() = layoutAndRetry(portrait) {
        composeRule.onNodeWithText("Selektor").assertIsDisplayed()
    }

    @Test fun portrait_shows_range_chips() = layoutAndRetry(portrait) {
        composeRule.onNodeWithText("Chips").assertIsDisplayed()
    }

    @Test fun portrait_shows_chart_content() = layoutAndRetry(portrait) {
        composeRule.onNodeWithText("Diagram").assertIsDisplayed()
    }

    @Test fun portrait_shows_legend() = layoutAndRetry(portrait) {
        composeRule.onNodeWithText("Legend").assertIsDisplayed()
    }

    @Test fun portrait_shows_portraitExtras_when_provided() = layoutAndRetry(portrait, withExtras = true) {
        composeRule.onNodeWithText("Extras").assertIsDisplayed()
    }

    // ─── Landscape ─────────────────────────────────────────────────────────────

    @Test fun landscape_hides_TopAppBar_title() = layoutAndRetry(landscape) {
        composeRule.onNodeWithText("Testdiagram").assertDoesNotExist()
    }

    @Test fun landscape_shows_selector_in_overlay() = layoutAndRetry(landscape) {
        composeRule.onNodeWithText("Selektor").assertIsDisplayed()
    }

    @Test fun landscape_shows_range_chips_in_overlay() = layoutAndRetry(landscape) {
        composeRule.onNodeWithText("Chips").assertIsDisplayed()
    }

    @Test fun landscape_shows_chart_content() = layoutAndRetry(landscape) {
        composeRule.onNodeWithText("Diagram").assertIsDisplayed()
    }

    @Test fun landscape_shows_legend_in_bottom_overlay() = layoutAndRetry(landscape) {
        composeRule.onNodeWithText("Legend").assertIsDisplayed()
    }

    @Test fun landscape_hides_portraitExtras() = layoutAndRetry(landscape, withExtras = true) {
        composeRule.onNodeWithText("Extras").assertDoesNotExist()
    }

    @Test fun landscape_shows_back_button() = layoutAndRetry(landscape) {
        composeRule.onNodeWithContentDescription("Tillbaka").assertIsDisplayed()
    }
}
