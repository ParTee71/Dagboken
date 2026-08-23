package se.partee71.dagboken.ui.diagram

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    @Test fun renders_with_symmetric_range() = renderAndRetry {
        LineChartCanvas(series = series(2), minValue = -10f, maxValue = 10f, modifier = mod)
    }

    @Test fun renders_with_positive_only_range() = renderAndRetry {
        LineChartCanvas(series = series(1), minValue = 0f, maxValue = 10f, modifier = mod)
    }

    @Test fun renders_with_empty_series() = renderAndRetry {
        LineChartCanvas(series = emptyList(), minValue = -10f, maxValue = 10f, modifier = mod)
    }

    @Test fun renders_with_null_gaps_in_series() = renderAndRetry {
        LineChartCanvas(series = series(2, withGap = true), minValue = -10f, maxValue = 10f, modifier = mod)
    }

    @Test fun renders_without_crash_when_minValue_is_zero() = renderAndRetry {
        LineChartCanvas(series = series(1), minValue = 0f, maxValue = 5f, modifier = mod)
    }

    @Test fun renders_single_point_series() = renderAndRetry {
        LineChartCanvas(
            series   = listOf(ChartSeries("A", Color.Blue, listOf(3f))),
            minValue = -10f,
            maxValue = 10f,
            modifier = mod,
        )
    }

    // ─── Smart y-axelskala (#136) ──────────────────────────────────────────────

    @Test fun renders_without_crash_with_a_narrow_non_zero_anchored_range() = renderAndRetry {
        // t.ex. computeSmartYAxis på ett symptomband 5..8 — heltaligt sedan #170
        // (var tidigare 4.5..8.5 med steg 0.5).
        LineChartCanvas(series = series(1), minValue = 4f, maxValue = 9f, gridStep = 1f, modifier = mod)
    }

    @Test fun renders_without_crash_with_a_range_far_from_zero() = renderAndRetry {
        // t.ex. computeSmartYRange på stegvärden ~5000-9000
        LineChartCanvas(series = series(1), minValue = 4500f, maxValue = 9500f, modifier = mod)
    }

    // ─── Heltaliga y-axlar + explicit gridStep (#170) ──────────────────────────

    @Test fun renders_without_crash_with_an_explicit_gridStep() = renderAndRetry {
        LineChartCanvas(series = series(2), minValue = 0f, maxValue = 10f, gridStep = 2f, modifier = mod)
    }

    // ─── Trendlinje per serie (#170) ────────────────────────────────────────────

    @Test fun renders_a_dashed_trend_line_for_multiple_series_without_crashing() = renderAndRetry {
        LineChartCanvas(series = series(2), minValue = -10f, maxValue = 10f, modifier = mod)
    }

    @Test fun renders_a_trend_line_for_a_series_with_a_gap_without_crashing() = renderAndRetry {
        LineChartCanvas(series = series(1, withGap = true), minValue = -10f, maxValue = 10f, modifier = mod)
    }

    // ─── Tömd serie efter tidigare rendering (#141) ───────────────────────────

    /**
     * Modellen töms i en `LaunchedEffect`, alltså efter kompositionen: en omritning
     * däremellan ser föregående modells serier tillsammans med en redan tömd linjelista.
     * Utan reservlinjen i [LineChartCanvas] slog Vicos `getRepeating()` då till på en tom
     * lista ("Cannot get repeated item from empty collection") — sporadiskt, beroende på
     * var omritningen råkade hamna. Tillståndet hålls utanför kompositionen och växlas
     * flera gånger, ett steg i taget, så fönstret öppnas om och om igen i stället för att
     * bero på turen i en enda övergång (#141).
     */
    @Test fun `renders without crash when series goes from non-empty to empty`() = retryOnRenderGlitch {
        val currentSeries = mutableStateOf(series(2))
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        LineChartCanvas(
                            series   = currentSeries.value,
                            minValue = -10f,
                            maxValue = 10f,
                            modifier = mod,
                        )
                    }
                }
            }
            composeRule.waitForIdle()
            repeat(3) {
                scenario.onActivity { currentSeries.value = emptyList() }
                composeRule.waitForIdle()
                scenario.onActivity { currentSeries.value = series(2) }
                composeRule.waitForIdle()
            }
            scenario.onActivity { currentSeries.value = emptyList() }
            composeRule.waitForIdle()
        } finally {
            scenario.close()
        }
    }

    // ─── Zoom/pan nollställs vid periodbyte (#149) ────────────────────────────

    @Test fun `renders without crash for a wide period with many data points`() = retryOnRenderGlitch {
        // initialZoom = Zoom.Content (i stället för Vicos standard Zoom.max(fixed, Content),
        // som väljer den mer inzoomade av de två) ska visa hela perioden direkt, oavsett
        // hur många datapunkter den innehåller (uppföljning till #149). Pixel-exakt zoomnivå går inte att
        // asserta via semantikträdet (Vico ritar rakt mot en Canvas); det här verifierar
        // att ett brett dataset (t.ex. "Allt"/3 månader) inte kraschar renderingen.
        val many = 90
        renderAndRetry {
            LineChartCanvas(
                series = List(1) { ChartSeries("S", Color(0xFF60a5fa), List(many) { j -> (j % 5 + 1).toFloat() }) },
                dates = List(many) { "2026-01-01" },
                minValue = -10f,
                maxValue = 10f,
                modifier = mod,
            )
        }
    }

    @Test fun `renders without crash when dates change and zoom pan state resets`() = retryOnRenderGlitch {
        // scrollState/zoomState nycklas på `dates` (key(dates) { ... }) så en ny
        // remember-instans skapas — och därmed nollställd zoom/pan — varje gång
        // diagrammets period byts. Pixel-exakt zoomnivå går inte att asserta via
        // semantikträdet; det här verifierar att bytet inte kraschar renderingen.
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    var currentDates by remember { mutableStateOf(List(10) { "2026-01-${(it + 1).toString().padStart(2, '0')}" }) }
                    MaterialTheme {
                        LineChartCanvas(series = series(1), dates = currentDates, minValue = -10f, maxValue = 10f, modifier = mod)
                    }
                    LaunchedEffect(Unit) { currentDates = List(5) { "2026-02-${(it + 1).toString().padStart(2, '0')}" } }
                }
            }
            composeRule.waitForIdle()
        } finally {
            scenario.close()
        }
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
