package se.partee71.dagboken.ui.diagram

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.IntervalPoint
import se.partee71.dagboken.core.engine.computeTrendLine
import se.partee71.dagboken.core.engine.formatChartValue
import se.partee71.dagboken.core.engine.intervalAxisFor
import se.partee71.dagboken.core.engine.summarizeIntervals
import se.partee71.dagboken.core.engine.trendSegment
import se.partee71.dagboken.ui.common.color
import se.partee71.dagboken.ui.common.scaleLevel

/**
 * Intervall-/spannstapeldiagram (TRD-8, regel 4 – inte energispecifikt): en rundad stapel per dag från
 * dagens lägsta till högsta värde (35 % opacitet) och en punkt för dagsvärdet, båda i energiskalans färg
 * för dagsvärdet (`scaleLevel` på [scale], [higherIsBetter]); dagsvärdena förbinds med en mjuk kurva
 * som bryts vid en dag utan data (`null`). Heltalsaxel med värdelinjer (TRD-7, TRD-9), streckad trend
 * över dagsvärdena (TRD-13), zoom och panorering (TRD-10) och `MinMaxCaption` under.
 *
 * Visas redan med **en** dag med data (TRD-8: "visas alltid när minst en dag har en screening");
 * utan någon visas det gemensamma tomma läget.
 */
@Composable
fun IntervalBarChart(
    points: List<IntervalPoint?>,
    modifier: Modifier = Modifier,
    xLabels: List<String> = emptyList(),
    label: String = stringResource(R.string.chart_a11y_default_label),
    emptyHint: String = stringResource(R.string.chart_empty_hint),
    scale: IntRange = 0..10,
    higherIsBetter: Boolean = true,
) {
    val summary = summarizeIntervals(points)
    val direction = computeTrendLine(points.map { it?.value })?.direction
    val spoken = summary?.let {
        stringResource(
            R.string.chart_a11y_interval_summary,
            label,
            pluralStringResource(R.plurals.chart_a11y_days, it.count, it.count),
            formatChartValue(it.min),
            formatChartValue(it.max),
            trendSpoken(direction),
        )
    }.orEmpty()
    // Dagsvärdets färg ur energiskalan – samma nivågränser som reglagen och chipsen (DSN-1).
    val dayColors = points.map { point -> point?.let { scaleLevel(it.value.roundToInt(), scale, higherIsBetter).color } }
    val curveColor = chartColors.line

    ChartFrame(
        enoughData = summary != null,
        label = label,
        emptyHint = emptyHint,
        modifier = modifier,
        footer = {
            if (summary != null) MinMaxCaption(summary.min, summary.max, average = summary.average, trend = direction)
        },
    ) {
        val axis = remember(points) { intervalAxisFor(points) }
        val trend = remember(points) { trendSegment(points.map { it?.value }) }
        BarCanvas(points.size, axis, xLabels, trend, resetKey = points to xLabels, description = spoken, barStyle = BarStyle.Span) { viewport, barWidth ->
            // Spannen (min–max) underst, rundade i ändarna.
            points.forEachIndexed { i, point ->
                if (point == null) return@forEachIndexed
                val x = viewport.xOf(i)
                drawLine(
                    color = checkNotNull(dayColors[i]).span(),
                    start = Offset(x, viewport.yOf(point.max)),
                    end = Offset(x, viewport.yOf(point.min)),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
            // Dagsvärdena som mjuk kurva, bruten vid en lucka (TRD-8), med dagsvärdets punkt överst.
            drawSmoothCurve(points.map { it?.value }, viewport, curveColor) { i -> dayColors[i] }
        }
    }
}
