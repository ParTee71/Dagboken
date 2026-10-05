package se.partee71.dagboken.ui.diagram

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.StackedPoint
import se.partee71.dagboken.core.engine.computeTrendLine
import se.partee71.dagboken.core.engine.dominantSegment
import se.partee71.dagboken.core.engine.formatChartValue
import se.partee71.dagboken.core.engine.knownCount
import se.partee71.dagboken.core.engine.stackBases
import se.partee71.dagboken.core.engine.stackTotals
import se.partee71.dagboken.core.engine.stackedAxisFor
import se.partee71.dagboken.core.engine.summarize
import se.partee71.dagboken.core.engine.trendSegment

/** En del av stapeln — en kategori med sin färg, t.ex. ett sömnstadium (`AppColors.extended.sleepStages`). */
@Immutable
data class StackSegment(val label: String, val color: Color)

/**
 * Staplat stapeldiagram (TRD-16, regel 4 – vet ingenting om sömn): en stapel per natt, delad nedifrån
 * och upp i [segments] ordning (sömnstadier enligt TRD-16: djup, REM, lätt, vaken). Ett saknat segment tar ingen höjd och
 * skjuter inte upp segmenten ovanför; en natt utan data är en lucka. Y-axeln skalas över totalhöjden
 * och börjar alltid på noll (TRD-7, TRD-16), med värdelinjer (TRD-9) och streckad trend över totalen
 * (TRD-13). Teckenförklaring och `MinMaxCaption` under; zoom och panorering som `IntervalBarChart`.
 * Under två nätter med data visas det gemensamma tomma läget.
 */
@Composable
fun StackedBarChart(
    points: List<StackedPoint>,
    segments: List<StackSegment>,
    modifier: Modifier = Modifier,
    xLabels: List<String> = emptyList(),
    label: String = stringResource(R.string.chart_a11y_default_label),
    emptyHint: String = stringResource(R.string.chart_empty_hint),
) {
    val totals = remember(points) { stackTotals(points) }
    val summary = summarize(totals)
    val direction = computeTrendLine(totals)?.direction
    val dominant = dominantSegment(points, segments.size)?.let { segments[it].label }
    val spoken = summary?.let {
        stringResource(
            R.string.chart_a11y_stacked_summary,
            label,
            pluralStringResource(R.plurals.chart_a11y_bars, it.count, it.count),
            formatChartValue(it.min),
            formatChartValue(it.max),
            dominant ?: stringResource(R.string.chart_a11y_no_dominant),
            trendSpoken(direction),
        )
    }.orEmpty()
    val fallback = chartColors.label

    ChartFrame(
        enoughData = knownCount(totals) >= MIN_CHART_POINTS,
        label = label,
        emptyHint = emptyHint,
        modifier = modifier,
        footer = {
            ChartLegend(
                segments.map { LegendItem(it.label, it.color, LegendMark.Square) } +
                    LegendItem(stringResource(R.string.chart_legend_trend), chartColors.trend, LegendMark.Dashed),
            )
            if (summary != null) MinMaxCaption(summary.min, summary.max, average = summary.average, trend = direction)
        },
    ) {
        val axis = remember(points) { stackedAxisFor(points) }
        val trend = remember(totals) { trendSegment(totals) }
        BarCanvas(points.size, axis, xLabels, trend, resetKey = points to xLabels, description = spoken, barStyle = BarStyle.Stack) { viewport, barWidth ->
            points.forEachIndexed { i, point ->
                val bases = stackBases(point)
                val x = viewport.xOf(i)
                point.values.forEachIndexed { segment, value ->
                    // Ett saknat segment tar ingen höjd — det är en lucka, inte en nolla.
                    if (value == null || value <= 0f) return@forEachIndexed
                    val bottom = viewport.yOf(bases[segment])
                    val top = viewport.yOf(bases[segment] + value)
                    drawRect(
                        color = segments.getOrNull(segment)?.color ?: fallback,
                        topLeft = Offset(x - barWidth / 2f, top),
                        size = Size(barWidth, (bottom - top).coerceAtLeast(0f)),
                    )
                }
            }
        }
    }
}

