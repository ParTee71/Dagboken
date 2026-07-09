package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LineChartCanvasTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    private val mod = Modifier.fillMaxWidth().height(200.dp)

    private fun series(n: Int, withGap: Boolean = false) = List(n) { i ->
        ChartSeries(
            label  = "S$i",
            color  = Color(0xFF60a5fa),
            points = List(10) { j -> if (withGap && j == 5) null else (j % 5 + 1).toFloat() },
        )
    }

    // ─── Legacy branch (gridValues = null) ────────────────────────────────────

    @Test fun legacy_renders_with_symmetric_range() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(series = series(2), minValue = -10f, maxValue = 10f, modifier = mod)
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun legacy_renders_with_positive_only_range() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(series = series(1), minValue = 0f, maxValue = 10f, modifier = mod)
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun legacy_renders_with_empty_series() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(series = emptyList(), minValue = -10f, maxValue = 10f, modifier = mod)
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun legacy_renders_with_null_gaps_in_series() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(series = series(2, withGap = true), minValue = -10f, maxValue = 10f, modifier = mod)
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun legacy_renders_without_crash_when_minValue_is_zero_deduplicated_levels() {
        // minValue=0 causes listOf(0,0,0,maxV/2,maxV) — distinct() must not crash
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(series = series(1), minValue = 0f, maxValue = 5f, modifier = mod)
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun legacy_renders_single_point_series() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(
                    series   = listOf(ChartSeries("A", Color.Blue, listOf(3f))),
                    minValue = -10f,
                    maxValue = 10f,
                    modifier = mod,
                )
            }
        }
        composeRule.waitForIdle()
    }

    // ─── gridValues branch ────────────────────────────────────────────────────

    @Test fun gridValues_branch_renders_with_data() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(
                    series     = series(2),
                    minValue   = 0f,
                    maxValue   = 5f,
                    gridValues = listOf(0f, 1f, 2f, 3f, 4f, 5f),
                    modifier   = mod,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun gridValues_branch_renders_with_empty_series() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(
                    series     = emptyList(),
                    minValue   = 0f,
                    maxValue   = 5f,
                    gridValues = listOf(0f, 1f, 2f, 3f, 4f, 5f),
                    modifier   = mod,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun gridValues_branch_renders_with_null_gaps() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(
                    series     = series(2, withGap = true),
                    minValue   = 0f,
                    maxValue   = 5f,
                    gridValues = listOf(0f, 1f, 2f, 3f, 4f, 5f),
                    modifier   = mod,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun gridValues_branch_renders_single_value_grid() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(
                    series     = series(1),
                    minValue   = 0f,
                    maxValue   = 5f,
                    gridValues = listOf(0f),
                    modifier   = mod,
                )
            }
        }
        composeRule.waitForIdle()
    }
}
