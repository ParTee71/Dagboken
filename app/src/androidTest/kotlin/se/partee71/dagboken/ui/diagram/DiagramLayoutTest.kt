package se.partee71.dagboken.ui.diagram

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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

    private fun section(
        title: String,
        chartText: String,
        legendText: String? = null,
        periodSelectorText: String? = null,
    ) = DiagramSection(
        title          = title,
        periodSelector = periodSelectorText?.let { { Text(it) } },
        selector       = { Text("Selektor $title") },
        chart          = { Text(chartText) },
        legend         = legendText?.let { { Text(it) } },
    )

    private fun layoutAndRetry(sections: List<DiagramSection>, withExtras: Boolean = false, assertions: () -> Unit) =
        retryOnRenderGlitch {
            val scenario = ActivityScenario.launch(ComponentActivity::class.java)
            try {
                scenario.onActivity {
                    it.setContent {
                        MaterialTheme {
                            DiagramLayout(
                                title          = "Testdiagram",
                                onBack         = {},
                                sections       = sections,
                                portraitExtras = if (withExtras) ({ Text("Extras") }) else null,
                            )
                        }
                    }
                }
                assertions()
            } finally {
                scenario.close()
            }
        }

    @Test fun shows_title_in_TopAppBar() = layoutAndRetry(listOf(section("Sektion", "Diagram"))) {
        composeRule.onNodeWithText("Testdiagram").assertIsDisplayed()
    }

    @Test fun shows_sections_own_period_selector() = layoutAndRetry(
        listOf(section("Sektion", "Diagram", periodSelectorText = "Period")),
    ) {
        composeRule.onNodeWithText("Period").assertIsDisplayed()
    }

    @Test fun period_selector_is_positioned_to_the_right_of_the_section_title() = layoutAndRetry(
        listOf(section("Sektion", "Diagram", periodSelectorText = "Period")),
    ) {
        val titleLeft = composeRule.onNodeWithText("Sektion").fetchSemanticsNode().boundsInRoot.left
        val periodLeft = composeRule.onNodeWithText("Period").fetchSemanticsNode().boundsInRoot.left
        assert(periodLeft > titleLeft) {
            "Förväntade periodväljaren till höger om titeln ($periodLeft > $titleLeft)"
        }
    }

    @Test fun shows_section_title_selector_and_chart() = layoutAndRetry(listOf(section("Sektion", "Diagram"))) {
        composeRule.onNodeWithText("Sektion").assertIsDisplayed()
        composeRule.onNodeWithText("Selektor Sektion").assertIsDisplayed()
        composeRule.onNodeWithText("Diagram").assertIsDisplayed()
    }

    @Test fun shows_legend_when_provided() = layoutAndRetry(
        listOf(section("Sektion", "Diagram", legendText = "Legend")),
    ) {
        composeRule.onNodeWithText("Legend").performScrollTo().assertIsDisplayed()
    }

    @Test fun shows_multiple_sections_stacked() = layoutAndRetry(
        listOf(
            section("Första", "DiagramA"),
            section("Andra", "DiagramB"),
        ),
    ) {
        composeRule.onNodeWithText("Första").assertIsDisplayed()
        composeRule.onNodeWithText("DiagramA").assertIsDisplayed()
        composeRule.onNodeWithText("Andra").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("DiagramB").performScrollTo().assertIsDisplayed()
    }

    @Test fun shows_portraitExtras_when_provided() = layoutAndRetry(
        listOf(section("Sektion", "Diagram")),
        withExtras = true,
    ) {
        composeRule.onNodeWithText("Extras").performScrollTo().assertIsDisplayed()
    }

    @Test fun shows_back_button() = layoutAndRetry(listOf(section("Sektion", "Diagram"))) {
        composeRule.onNodeWithContentDescription("Tillbaka").assertIsDisplayed()
    }
}
