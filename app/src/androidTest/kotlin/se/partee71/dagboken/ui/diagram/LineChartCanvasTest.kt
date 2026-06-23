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

    @get:Rule val composeRule = createComposeRule()

    private val mod = Modifier.fillMaxWidth().height(200.dp)

    private fun series(n: Int, withGap: Boolean = false) = List(n) { i ->
        ChartSeries(
            label  = "S$i",
            color  = Color(0xFF60a5fa),
            points = List(10) { j -> if (withGap && j == 5) null else (j % 5 + 1).toFloat() },
        )
    }

    // ─── Legacy branch (gridValues = null) ────────────────────────────────────

    @Test fun `legacy renders with symmetric range`() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(series = series(2), minValue = -10f, maxValue = 10f, modifier = mod)
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun `legacy renders with positive-only range`() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(series = series(1), minValue = 0f, maxValue = 10f, modifier = mod)
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun `legacy renders with empty series`() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(series = emptyList(), minValue = -10f, maxValue = 10f, modifier = mod)
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun `legacy renders with null gaps in series`() {
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(series = series(2, withGap = true), minValue = -10f, maxValue = 10f, modifier = mod)
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun `legacy renders without crash when minValue is zero (deduplicated levels)`() {
        // minValue=0 causes listOf(0,0,0,maxV/2,maxV) — distinct() must not crash
        composeRule.setContent {
            MaterialTheme {
                LineChartCanvas(series = series(1), minValue = 0f, maxValue = 5f, modifier = mod)
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun `legacy renders single-point series`() {
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

    @Test fun `gridValues branch renders with data`() {
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

    @Test fun `gridValues branch renders with empty series`() {
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

    @Test fun `gridValues branch renders with null gaps`() {
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

    @Test fun `gridValues branch renders single-value grid`() {
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
