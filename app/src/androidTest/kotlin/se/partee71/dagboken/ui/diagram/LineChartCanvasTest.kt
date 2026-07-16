package se.partee71.dagboken.ui.diagram

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class LineChartCanvasTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val mod = Modifier.fillMaxWidth().height(200.dp)

    private fun series(n: Int, withGap: Boolean = false) = List(n) { i ->
        ChartSeries(
            label  = "S$i",
            color  = Color(0xFF60a5fa),
            points = List(10) { j -> if (withGap && j == 5) null else (j % 5 + 1).toFloat() },
        )
    }

    private fun renderAndRetry(content: @Composable () -> Unit) = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { it.setContent { MaterialTheme { content() } } }
            composeRule.waitForIdle()
        } finally {
            scenario.close()
        }
    }

    // ─── Legacy branch (gridValues = null) ────────────────────────────────────

    @Test fun legacy_renders_with_symmetric_range() = renderAndRetry {
        LineChartCanvas(series = series(2), minValue = -10f, maxValue = 10f, modifier = mod)
    }

    @Test fun legacy_renders_with_positive_only_range() = renderAndRetry {
        LineChartCanvas(series = series(1), minValue = 0f, maxValue = 10f, modifier = mod)
    }

    @Test fun legacy_renders_with_empty_series() = renderAndRetry {
        LineChartCanvas(series = emptyList(), minValue = -10f, maxValue = 10f, modifier = mod)
    }

    @Test fun legacy_renders_with_null_gaps_in_series() = renderAndRetry {
        LineChartCanvas(series = series(2, withGap = true), minValue = -10f, maxValue = 10f, modifier = mod)
    }

    @Test fun legacy_renders_without_crash_when_minValue_is_zero_deduplicated_levels() = renderAndRetry {
        // minValue=0 causes listOf(0,0,0,maxV/2,maxV) — distinct() must not crash
        LineChartCanvas(series = series(1), minValue = 0f, maxValue = 5f, modifier = mod)
    }

    @Test fun legacy_renders_single_point_series() = renderAndRetry {
        LineChartCanvas(
            series   = listOf(ChartSeries("A", Color.Blue, listOf(3f))),
            minValue = -10f,
            maxValue = 10f,
            modifier = mod,
        )
    }

    // ─── gridValues branch ────────────────────────────────────────────────────

    @Test fun gridValues_branch_renders_with_data() = renderAndRetry {
        LineChartCanvas(
            series     = series(2),
            minValue   = 0f,
            maxValue   = 5f,
            gridValues = listOf(0f, 1f, 2f, 3f, 4f, 5f),
            modifier   = mod,
        )
    }

    @Test fun gridValues_branch_renders_with_empty_series() = renderAndRetry {
        LineChartCanvas(
            series     = emptyList(),
            minValue   = 0f,
            maxValue   = 5f,
            gridValues = listOf(0f, 1f, 2f, 3f, 4f, 5f),
            modifier   = mod,
        )
    }

    @Test fun gridValues_branch_renders_with_null_gaps() = renderAndRetry {
        LineChartCanvas(
            series     = series(2, withGap = true),
            minValue   = 0f,
            maxValue   = 5f,
            gridValues = listOf(0f, 1f, 2f, 3f, 4f, 5f),
            modifier   = mod,
        )
    }

    @Test fun gridValues_branch_renders_single_value_grid() = renderAndRetry {
        LineChartCanvas(
            series     = series(1),
            minValue   = 0f,
            maxValue   = 5f,
            gridValues = listOf(0f),
            modifier   = mod,
        )
    }

    // ─── Mörkt tema (#123) ─────────────────────────────────────────────────────

    @Test fun renders_without_crash_in_dark_theme() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        LineChartCanvas(
                            series   = series(2),
                            dates    = List(10) { "2026-01-${(it + 1).toString().padStart(2, '0')}" },
                            minValue = -10f,
                            maxValue = 10f,
                            modifier = mod,
                        )
                    }
                }
            }
            composeRule.waitForIdle()
        } finally {
            scenario.close()
        }
    }
}
