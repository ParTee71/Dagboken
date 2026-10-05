package se.partee71.dagboken.ui.diagram

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.chartAxisFor
import se.partee71.dagboken.core.engine.computeTrendLine
import se.partee71.dagboken.core.engine.gapFreeRuns
import se.partee71.dagboken.core.engine.knownCount
import se.partee71.dagboken.core.engine.summarize
import se.partee71.dagboken.core.engine.trendSegment

/**
 * En dataserie i ett linjediagram: [points] är ett värde per dag (eller natt), `null` där inget
 * loggats – en lucka, aldrig en nolla. [color] `null` = diagrammets kurvfärg (teal).
 */
@Immutable
data class ChartSeries(val label: String, val points: List<Float?>, val color: Color? = null)

/**
 * Linjediagram (TRD-6–TRD-10, TRD-13, TRD-18), ritat med Vico: kurvan i seriens färg med svag
 * gradientfyllning och punkter, **luckor** där en dag saknar värde, heltals-y-axel skalad efter
 * värdena (`computeSmartYAxis`, TRD-7), glesa x-etiketter ([xLabels], en per punkt) och en streckad
 * trendlinje per serie (TRD-13). Tvåfingerzoom och panorering, helt utzoomat från början och
 * nollställt när data eller [xLabels] byts (ny period, TRD-10).
 *
 * [previous] är föregående period (TRD-18) på samma x-index (dag 1 mot dag 1), nedtonad med egen
 * trendlinje; anroparen äger tillvalet. Med en serie är trendlinjen solgul och föregående period grå
 * (mockupen); med flera serier får trend och föregående period seriens färg, så att de går att skilja
 * åt – och en teckenförklaring visas.
 *
 * Under diagrammet står alltid `MinMaxCaption` (TRD-9) över alla visade värden, med snitt och
 * trendpill när diagrammet har en serie. Har ingen serie två kända punkter visas det gemensamma tomma
 * läget med [emptyHint]. Skärmläsaren får en sammanfattning per serie under [label] (NFR-14).
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    xLabels: List<String> = emptyList(),
    previous: List<ChartSeries> = emptyList(),
    label: String = stringResource(R.string.chart_a11y_default_label),
    emptyHint: String = stringResource(R.string.chart_empty_hint),
) {
    val colors = chartColors
    val single = series.size == 1
    val current = series.map { it.copy(color = it.color ?: colors.line) }
    val earlier = previous.mapIndexed { i, s ->
        val base = s.color ?: current.getOrNull(i)?.color ?: colors.line
        s.copy(color = if (single) colors.previous else base.previousPeriod())
    }
    val enough = current.any { knownCount(it.points) >= MIN_CHART_POINTS }
    // "Energi (föregående)": mallen hämtas en gång och fylls i per serie.
    val previousFormat = stringResource(R.string.chart_previous_format)
    val spoken = (current + earlier.map { it.copy(label = previousFormat.format(it.label)) })
        .map { seriesSpoken(it.label, summarize(it.points), computeTrendLine(it.points)?.direction) }
        .joinToString(". ")

    ChartFrame(
        enoughData = enough,
        label = label,
        emptyHint = emptyHint,
        modifier = modifier,
        footer = {
            if (!single || earlier.isNotEmpty()) ChartLegend(lineLegend(current, earlier, colors, previousFormat, stringResource(R.string.chart_legend_trend)))
            val all = summarize((current + earlier).flatMap { it.points })
            if (all != null) {
                MinMaxCaption(
                    min = all.min,
                    max = all.max,
                    average = if (single) summarize(current.single().points)?.average else null,
                    trend = if (single) computeTrendLine(current.single().points)?.direction else null,
                )
            }
        },
    ) {
        // Temats färger ingår i nyckeln: ett temabyte utan att aktiviteten skapas om ska rita om kurvorna.
        val lines = remember(current, earlier, colors) { linePlot(current, earlier, trendColor = if (single) colors.trend else null) }
        val axis = remember(current, earlier) { chartAxisFor((current + earlier).map { it.points }) }
        val xCount = (listOf(xLabels.size) + (current + earlier).map { it.points.size }).max()
        // Ny period eller nya data → helt utzoomat igen (TRD-10), även utan x-etiketter.
        key(current.map { it.points }, earlier.map { it.points }, xLabels) {
            VicoLinePlot(lines, axis, xCount, xLabels, CHART_HEIGHT, zoomable = true, modifier = Modifier.chartDescription(spoken))
        }
    }
}

/**
 * Kurvorna i ritordning: föregående period underst, sedan de nuvarande seriernas sammanhängande bitar
 * (en bit per lucka, samma färg), sist trendlinjerna överst. [trendColor] `null` = seriens färg.
 */
internal fun linePlot(current: List<ChartSeries>, previous: List<ChartSeries>, trendColor: Color?): List<PlotLine> {
    fun curves(series: List<ChartSeries>, points: Boolean, area: Boolean) = series.flatMap { s ->
        gapFreeRuns(s.points).map { it.toPlotLine(PlotStyle(checkNotNull(s.color), area = area, points = points)) }
    }
    fun trends(series: List<ChartSeries>, color: (ChartSeries) -> Color) =
        series.mapNotNull { s -> trendSegment(s.points)?.toPlotLine(color(s)) }
    return curves(previous, points = false, area = false) +
        curves(current, points = true, area = true) +
        trends(previous) { checkNotNull(it.color) } +
        trends(current) { trendColor ?: checkNotNull(it.color) }
}

private fun lineLegend(
    current: List<ChartSeries>,
    previous: List<ChartSeries>,
    colors: ChartColors,
    previousFormat: String,
    trendLabel: String,
): List<LegendItem> =
    current.map { LegendItem(it.label, checkNotNull(it.color)) } +
        previous.map { LegendItem(previousFormat.format(it.label), checkNotNull(it.color)) } +
        LegendItem(trendLabel, if (current.size == 1) colors.trend else colors.label, LegendMark.Dashed)
