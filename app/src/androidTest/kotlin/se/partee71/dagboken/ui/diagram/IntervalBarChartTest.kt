package se.partee71.dagboken.ui.diagram

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class IntervalBarChartTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val mod = Modifier.fillMaxWidth().height(220.dp)

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

    @Test fun `renders a range with min value max`() = renderAndRetry(
        content = {
            IntervalBarChart(
                points   = listOf(IntervalPoint(min = 2f, value = 5f, max = 8f)),
                dates    = listOf("2026-07-10"),
                minValue = 0f,
                maxValue = 10f,
                modifier = mod,
            )
        },
    ) {
        composeRule.onNodeWithContentDescription(
            "Dagsspann, 1 dagar, lägsta 2, högsta 8",
        ).assertIsDisplayed()
    }

    @Test fun `renders when min equals max for a single screening day`() = renderAndRetry(
        content = {
            IntervalBarChart(
                points   = listOf(IntervalPoint(min = 6f, value = 6f, max = 6f)),
                minValue = 0f,
                maxValue = 10f,
                modifier = mod,
            )
        },
    ) {
        composeRule.onNodeWithContentDescription(
            "Dagsspann, 1 dagar, lägsta 6, högsta 6",
        ).assertIsDisplayed()
    }

    @Test fun `renders null gaps between known days without crashing`() = renderAndRetry(
        content = {
            IntervalBarChart(
                points = listOf(
                    IntervalPoint(min = 2f, value = 4f, max = 6f),
                    null,
                    IntervalPoint(min = 3f, value = 5f, max = 9f),
                ),
                dates    = listOf("2026-07-08", "2026-07-09", "2026-07-10"),
                minValue = 0f,
                maxValue = 10f,
                modifier = mod,
            )
        },
    ) {
        composeRule.onNodeWithContentDescription(
            "Dagsspann, 2 dagar, lägsta 2, högsta 9",
        ).assertIsDisplayed()
    }

    @Test fun `renders empty points without crashing`() = renderAndRetry(
        content = {
            IntervalBarChart(points = emptyList(), minValue = 0f, maxValue = 10f, modifier = mod)
        },
    ) {
        composeRule.onNodeWithContentDescription("Inga dagar med data").assertIsDisplayed()
    }

    @Test fun `renders many days with thinned date labels without crashing`() = renderAndRetry(
        content = {
            IntervalBarChart(
                points   = List(30) { i -> IntervalPoint(min = 1f, value = (i % 10 + 1).toFloat(), max = 10f) },
                dates    = List(30) { i -> "2026-06-${(i + 1).toString().padStart(2, '0')}" },
                minValue = 0f,
                maxValue = 10f,
                modifier = mod,
            )
        },
    ) {
        composeRule.onNodeWithContentDescription(
            "Dagsspann, 30 dagar, lägsta 1, högsta 10",
        ).assertIsDisplayed()
    }

    // ─── Bezier-kurva + värdelinjer (#141) ─────────────────────────────────────

    @Test fun `renders the value curve across multiple days without crashing`() = renderAndRetry(
        content = {
            IntervalBarChart(
                points = listOf(
                    IntervalPoint(min = 2f, value = 3f, max = 5f),
                    IntervalPoint(min = 4f, value = 7f, max = 9f),
                    IntervalPoint(min = 3f, value = 5f, max = 6f),
                ),
                dates    = listOf("2026-07-08", "2026-07-09", "2026-07-10"),
                minValue = 0f,
                maxValue = 10f,
                modifier = mod,
            )
        },
    ) {
        composeRule.onNodeWithContentDescription(
            "Dagsspann, 3 dagar, lägsta 2, högsta 9",
        ).assertIsDisplayed()
    }

    @Test fun `curve breaks cleanly across a gap without crashing`() = renderAndRetry(
        content = {
            IntervalBarChart(
                points = listOf(
                    IntervalPoint(min = 2f, value = 4f, max = 6f),
                    null,
                    IntervalPoint(min = 3f, value = 5f, max = 9f),
                ),
                dates    = listOf("2026-07-08", "2026-07-09", "2026-07-10"),
                minValue = 0f,
                maxValue = 10f,
                modifier = mod,
            )
        },
    ) {
        composeRule.onNodeWithContentDescription(
            "Dagsspann, 2 dagar, lägsta 2, högsta 9",
        ).assertIsDisplayed()
    }

    @Test fun `renders with an explicit gridStep without crashing`() = renderAndRetry(
        content = {
            IntervalBarChart(
                points   = listOf(IntervalPoint(min = 2f, value = 5f, max = 8f)),
                dates    = listOf("2026-07-10"),
                minValue = 0f,
                maxValue = 10f,
                gridStep = 2f,
                modifier = mod,
            )
        },
    ) {
        composeRule.onNodeWithContentDescription(
            "Dagsspann, 1 dagar, lägsta 2, högsta 8",
        ).assertIsDisplayed()
    }

    @Test fun `renders without crashing when maxValue equals minValue`() = renderAndRetry(
        content = {
            IntervalBarChart(
                points   = listOf(IntervalPoint(min = 5f, value = 5f, max = 5f)),
                minValue = 5f,
                maxValue = 5f,
                modifier = mod,
            )
        },
    ) {
        composeRule.onNodeWithContentDescription(
            "Dagsspann, 1 dagar, lägsta 5, högsta 5",
        ).assertIsDisplayed()
    }

    // ─── Tvåfingerzoom + panorering (#144) ─────────────────────────────────────

    @Test fun `pinch-zoom gesture does not crash and chart stays rendered`() = renderAndRetry(
        content = {
            IntervalBarChart(
                points = listOf(
                    IntervalPoint(min = 2f, value = 4f, max = 6f),
                    IntervalPoint(min = 3f, value = 5f, max = 9f),
                    IntervalPoint(min = 1f, value = 3f, max = 7f),
                ),
                dates    = listOf("2026-07-08", "2026-07-09", "2026-07-10"),
                minValue = 0f,
                maxValue = 10f,
                modifier = mod,
            )
        },
    ) {
        composeRule.onNodeWithContentDescription(
            "Dagsspann, 3 dagar, lägsta 1, högsta 9",
        ).performTouchInput {
            // Två fingrar från mitten och utåt — nyp-zooma isär.
            down(0, Offset(center.x - 20f, center.y))
            down(1, Offset(center.x + 20f, center.y))
            moveTo(0, Offset(center.x - 80f, center.y))
            moveTo(1, Offset(center.x + 80f, center.y))
            up(0)
            up(1)
        }
        composeRule.onNodeWithContentDescription(
            "Dagsspann, 3 dagar, lägsta 1, högsta 9",
        ).assertIsDisplayed()
    }
}
