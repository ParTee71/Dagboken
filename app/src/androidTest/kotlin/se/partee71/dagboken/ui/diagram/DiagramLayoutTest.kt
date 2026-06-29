package se.partee71.dagboken.ui.diagram

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagramLayoutTest {

    @get:Rule val composeRule = createComposeRule()

    private val portrait  = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }
    private val landscape = Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }

    private fun setLayout(config: Configuration, withExtras: Boolean = false) {
        composeRule.setContent {
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

    // ─── Portrait ──────────────────────────────────────────────────────────────

    @Test fun portrait_shows_title_in_TopAppBar() {
        setLayout(portrait)
        composeRule.onNodeWithText("Testdiagram").assertIsDisplayed()
    }

    @Test fun portrait_shows_selector() {
        setLayout(portrait)
        composeRule.onNodeWithText("Selektor").assertIsDisplayed()
    }

    @Test fun portrait_shows_range_chips() {
        setLayout(portrait)
        composeRule.onNodeWithText("Chips").assertIsDisplayed()
    }

    @Test fun portrait_shows_chart_content() {
        setLayout(portrait)
        composeRule.onNodeWithText("Diagram").assertIsDisplayed()
    }

    @Test fun portrait_shows_legend() {
        setLayout(portrait)
        composeRule.onNodeWithText("Legend").assertIsDisplayed()
    }

    @Test fun portrait_shows_portraitExtras_when_provided() {
        setLayout(portrait, withExtras = true)
        composeRule.onNodeWithText("Extras").assertIsDisplayed()
    }

    // ─── Landscape ─────────────────────────────────────────────────────────────

    @Test fun landscape_hides_TopAppBar_title() {
        setLayout(landscape)
        composeRule.onNodeWithText("Testdiagram").assertDoesNotExist()
    }

    @Test fun landscape_shows_selector_in_overlay() {
        setLayout(landscape)
        composeRule.onNodeWithText("Selektor").assertIsDisplayed()
    }

    @Test fun landscape_shows_range_chips_in_overlay() {
        setLayout(landscape)
        composeRule.onNodeWithText("Chips").assertIsDisplayed()
    }

    @Test fun landscape_shows_chart_content() {
        setLayout(landscape)
        composeRule.onNodeWithText("Diagram").assertIsDisplayed()
    }

    @Test fun landscape_shows_legend_in_bottom_overlay() {
        setLayout(landscape)
        composeRule.onNodeWithText("Legend").assertIsDisplayed()
    }

    @Test fun landscape_hides_portraitExtras() {
        setLayout(landscape, withExtras = true)
        composeRule.onNodeWithText("Extras").assertDoesNotExist()
    }

    @Test fun landscape_shows_back_button() {
        setLayout(landscape)
        composeRule.onNodeWithContentDescription("Tillbaka").assertIsDisplayed()
    }
}
