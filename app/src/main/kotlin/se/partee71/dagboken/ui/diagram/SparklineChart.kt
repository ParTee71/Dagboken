package se.partee71.dagboken.ui.diagram

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.chartAxisFor
import se.partee71.dagboken.core.engine.computeTrendLine
import se.partee71.dagboken.core.engine.gapFreeRuns
import se.partee71.dagboken.core.engine.knownCount
import se.partee71.dagboken.core.engine.summarize
import se.partee71.dagboken.core.engine.trendSegment
import se.partee71.dagboken.ui.components.AppButton
import se.partee71.dagboken.ui.components.ButtonVariant

/**
 * Minidiagrammet på Idag (HEM-7, flyttat från 3.x `ui/home`): de senaste dagarnas [points] (`null` =
 * lucka) som teal kurva med fyllning, dagens punkt (sista platsen) solgul med ring, streckad
 * trendlinje (TRD-13), heltals-y-axel (TRD-7) och veckodagar under ([xLabels]). Under står
 * "Lägst · Högst · Idag" (TRD-9) och, med [onOpenTrends], länken till Trender (TRD-5). Ingen zoom –
 * Idags diagram är fasta vyer (TRD-10). Under två kända punkter visas det gemensamma tomma läget.
 */
@Composable
fun SparklineChart(
    points: List<Float?>,
    modifier: Modifier = Modifier,
    xLabels: List<String> = emptyList(),
    label: String = stringResource(R.string.chart_a11y_default_label),
    emptyHint: String = stringResource(R.string.chart_empty_hint),
    onOpenTrends: (() -> Unit)? = null,
) {
    val colors = chartColors
    val summary = summarize(points)
    val direction = computeTrendLine(points)?.direction
    val spoken = seriesSpoken(label, summary, direction)
    ChartFrame(
        enoughData = knownCount(points) >= MIN_CHART_POINTS,
        label = label,
        emptyHint = emptyHint,
        modifier = modifier,
        footer = {
            if (summary != null) {
                MinMaxCaption(min = summary.min, max = summary.max, trend = direction, today = points.lastOrNull(), showToday = true)
            }
            onOpenTrends?.let { AppButton(stringResource(R.string.chart_open_trends), it, variant = ButtonVariant.Text) }
        },
    ) {
        val lines = remember(points, colors) {
            val today = points.lastIndex.takeIf { points.lastOrNull() != null }
            gapFreeRuns(points).map { it.toPlotLine(PlotStyle(colors.line, area = true, points = true, highlightX = today)) } +
                listOfNotNull(trendSegment(points)?.toPlotLine(colors.trend))
        }
        val axis = remember(points) { chartAxisFor(listOf(points)) }
        VicoLinePlot(
            lines,
            axis,
            maxOf(points.size, xLabels.size),
            xLabels,
            SPARKLINE_HEIGHT,
            zoomable = false,
            modifier = Modifier.chartDescription(spoken),
        )
    }
}
