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
 * Ett tonat fält över x-indexen [from]…[to] (båda inräknade) bakom staplarna – en sjukdomsepisod i Händelser
 * och sjukdom (TRD-21). [label] står i teckenförklaringen (fält med samma etikett visas en gång).
 */
@Immutable
data class ChartBand(val from: Int, val to: Int, val label: String)

/**
 * Staplat stapeldiagram (TRD-16, regel 4 – vet ingenting om sömn): en stapel per natt, delad nedifrån
 * och upp i [segments] ordning (sömnstadier enligt TRD-16: djup, REM, lätt, vaken). Ett saknat segment tar ingen höjd och
 * skjuter inte upp segmenten ovanför; en natt utan data är en lucka. Y-axeln skalas över totalhöjden
 * och börjar alltid på noll (TRD-7, TRD-16), med värdelinjer (TRD-9) och streckad trend över totalen
 * (TRD-13). Teckenförklaring och `MinMaxCaption` under; zoom och panorering som `IntervalBarChart`.
 * Under två nätter med data visas det gemensamma tomma läget.
 *
 * Varianten för **Händelser och sjukdom** (TRD-21): [line] är en kurva ovanpå staplarna (incheckningarnas
 * svårighet, bruten vid luckor, i seriens färg, utan egen trend), [bands] tonade fält bakom dem (episoderna).
 * Axeln och det tomma läget räknar då både staplar och linje; `MinMaxCaption` gäller staplarna och säger det
 * ("Händelser: …"), eller linjen när staplarna är för få; linjen får en egen rad i teckenförklaringen och
 * skärmläsaren läser upp den som en serie. Utan [line] och [bands] är diagrammet oförändrat.
 */
@Composable
fun StackedBarChart(
    points: List<StackedPoint>,
    segments: List<StackSegment>,
    modifier: Modifier = Modifier,
    xLabels: List<String> = emptyList(),
    label: String = stringResource(R.string.chart_a11y_default_label),
    emptyHint: String = stringResource(R.string.chart_empty_hint),
    line: ChartSeries? = null,
    bands: List<ChartBand> = emptyList(),
) {
    val colors = chartColors
    val totals = remember(points) { stackTotals(points) }
    val lineValues = line?.points.orEmpty()
    val summary = summarize(totals)
    val direction = computeTrendLine(totals)?.direction
    val dominant = dominantSegment(points, segments.size)?.let { segments[it].label }
    val barsSpoken = summary?.let {
        stringResource(
            R.string.chart_a11y_stacked_summary,
            label,
            pluralStringResource(R.plurals.chart_a11y_bars, it.count, it.count),
            formatChartValue(it.min),
            formatChartValue(it.max),
            dominant ?: stringResource(R.string.chart_a11y_no_dominant),
            trendSpoken(direction),
        )
    }
    val lineColor = line?.color ?: colors.line
    val spoken = listOfNotNull(
        barsSpoken,
        line?.let { seriesSpoken(it.label, summarize(it.points), computeTrendLine(it.points)?.direction) },
    ).joinToString(". ")
    // Raden under diagrammet (TRD-9) gäller en sak i taget: staplarna när de räcker för ett diagram, annars linjen –
    // och med en linje står det vilken ("Händelser: Lägst 4 · Högst 7"). Linjen läses alltid upp som egen serie.
    val barsEnough = knownCount(totals) >= MIN_CHART_POINTS
    val lineSummary = line?.let { summarize(it.points) }
    val lineDirection = line?.let { computeTrendLine(it.points)?.direction }

    ChartFrame(
        enoughData = barsEnough || knownCount(lineValues) >= MIN_CHART_POINTS,
        label = label,
        emptyHint = emptyHint,
        modifier = modifier,
        footer = {
            ChartLegend(
                segments.map { LegendItem(it.label, it.color, LegendMark.Square) } +
                    listOfNotNull(line?.let { LegendItem(it.label, lineColor) }) +
                    bands.map { it.label }.distinct().map { LegendItem(it, colors.band.bandSwatch(), LegendMark.Square) } +
                    // Trenden står alltid med i det rena stapeldiagrammet; med en linje bara när den ritas.
                    listOfNotNull(LegendItem(stringResource(R.string.chart_legend_trend), colors.trend, LegendMark.Dashed).takeIf { line == null || direction != null }),
            )
            when {
                line == null -> summary?.let { MinMaxCaption(it.min, it.max, average = it.average, trend = direction) }
                barsEnough -> summary?.let { MinMaxCaption(it.min, it.max, average = it.average, trend = direction, scope = segments.joinToString(CHART_SEPARATOR) { s -> s.label }) }
                lineSummary != null -> MinMaxCaption(lineSummary.min, lineSummary.max, average = lineSummary.average, trend = lineDirection, scope = line.label)
            }
        },
    ) {
        val axis = remember(points, lineValues) { stackedAxisFor(points, lineValues) }
        val trend = remember(totals) { trendSegment(totals) }
        val fallback = colors.label
        BarCanvas(
            maxOf(points.size, lineValues.size),
            axis,
            xLabels,
            trend,
            resetKey = Triple(points, lineValues, xLabels),
            description = spoken,
            barStyle = BarStyle.Stack,
        ) { viewport, barWidth ->
            // Fälten underst, från första till sista dagens plats – klippta i ritytan som allt annat.
            bands.forEach { band ->
                val half = viewport.slotWidth * viewport.zoomPan.scale / 2f
                val left = viewport.xOf(band.from) - half
                drawRect(colors.band, topLeft = Offset(left, viewport.top), size = Size(viewport.xOf(band.to) + half - left, viewport.bottom - viewport.top))
            }
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
            if (line != null) drawSmoothCurve(line.points, viewport, lineColor) { lineColor }
        }
    }
}
