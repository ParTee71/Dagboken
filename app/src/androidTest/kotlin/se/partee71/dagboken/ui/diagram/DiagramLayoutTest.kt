package se.partee71.dagboken.ui.diagram

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class DiagramLayoutTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    /**
     * Diagramkorten är stängda som standard (TRD-14). Testerna som handlar om vad ett kort
     * *innehåller* bygger därför utfällda sektioner; att stängt är standardläget pinnas av
     * [a_section_is_collapsed_unless_told_otherwise] via den råa konstruktorn.
     */
    private fun section(
        title: String,
        chartText: String,
        legendText: String? = null,
        periodSelectorText: String? = null,
        expanded: Boolean = true,
    ) = DiagramSection(
        title          = title,
        periodSelector = periodSelectorText?.let { { Text(it) } },
        selector       = { Text("Selektor $title") },
        chart          = { Text(chartText) },
        legend         = legendText?.let { { Text(it) } },
        expanded       = expanded,
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
        // Titelraden är klickbar och merge:ar sina barn (NFR-18) — peka på Text-noderna
        // i det omerge:ade trädet, annars svarar båda uppslagen med samma rad.
        val titleLeft = composeRule.onNodeWithText("Sektion", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        val periodLeft = composeRule.onNodeWithText("Period", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
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

    // ─── Ihopfällbara sektionskort — TRD-14/NFR-18, #189 ──────────────────────

    /** Råa konstruktorn utan `expanded` — pinnar att stängt är standardläget. */
    @Test fun a_section_is_collapsed_unless_told_otherwise() = layoutAndRetry(
        listOf(DiagramSection(title = "Sektion", chart = { Text("Diagram") })),
    ) {
        composeRule.onNodeWithText("Sektion").assertIsDisplayed()
        composeRule.onNodeWithText("Diagram").assertDoesNotExist()
    }

    @Test fun a_collapsed_section_hides_its_chart_selector_legend_and_period_selector() = layoutAndRetry(
        listOf(
            section("Sektion", "Diagram", legendText = "Legend", periodSelectorText = "Period", expanded = false),
        ),
    ) {
        composeRule.onNodeWithText("Sektion").assertIsDisplayed()
        composeRule.onNodeWithText("Diagram").assertDoesNotExist()
        composeRule.onNodeWithText("Selektor Sektion").assertDoesNotExist()
        composeRule.onNodeWithText("Legend").assertDoesNotExist()
        // Periodväljaren säger ingenting utan sitt diagram (TRD-14).
        composeRule.onNodeWithText("Period", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun tapping_the_section_title_reports_a_toggle() = retryOnRenderGlitch {
        var toggles = 0
        val sections = listOf(
            DiagramSection(
                title            = "Sektion",
                chart            = { Text("Diagram") },
                expanded         = false,
                onToggleExpanded = { toggles++ },
            ),
        )
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DiagramLayout(title = "Testdiagram", onBack = {}, sections = sections)
                    }
                }
            }
            composeRule.onNodeWithText("Sektion").performClick()
            composeRule.waitForIdle()
            assertEquals(1, toggles)
        } finally {
            scenario.close()
        }
    }
}
