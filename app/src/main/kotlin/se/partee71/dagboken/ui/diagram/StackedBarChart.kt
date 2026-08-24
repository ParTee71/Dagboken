package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.formatShortDate
import java.time.LocalDate
import kotlin.math.max

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f
private const val MAX_DATE_LABELS = 6
private val AXIS_LABEL_SIZE = 11.sp

/** En del av stapeln — en kategori med sin färg, t.ex. ett sömnstadium. */
data class StackSegment(val label: String, val color: Color)

/**
 * En stapels delvärden, i samma ordning som diagrammets [StackSegment]-lista. Ett `null`
 * betyder att kategorin saknas den dagen och tar **ingen höjd** i stapeln — inte att den
 * var noll.
 */
data class StackedPoint(val values: List<Float?>)

/** Stapelns totalhöjd, eller null om ingen kategori har något värde alls. */
internal fun stackTotal(point: StackedPoint): Float? =
    point.values.filterNotNull().takeIf { it.isNotEmpty() }?.sum()

/** Totalhöjden per stapel — underlaget för y-skalan, min/max och trendlinjen. */
internal fun stackTotals(points: List<StackedPoint>): List<Float?> = points.map { stackTotal(it) }

/**
 * Segmentens underkanter, ackumulerade nedifrån och upp. Ett saknat segment flyttar inte
 * nästa segment uppåt, så en natt utan REM-mätning inte ser ut att ha mer djupsömn än den
 * hade.
 *
 * Ren funktion för enhetstestning (regel 2).
 */
internal fun stackBases(point: StackedPoint): List<Float> {
    var base = 0f
    return point.values.map { value ->
        val current = base
        base += value ?: 0f
        current
    }
}

/**
 * Den kategori som står för mest av perioden — används i skärmläsarbeskrivningen, eftersom
 * "vilket stadium dominerade" är det ett seende öga läser ur staplarna direkt. Null när
 * ingen kategori har någon tid alls.
 *
 * Ren funktion för enhetstestning (regel 2).
 */
internal fun dominantSegment(points: List<StackedPoint>, segments: List<StackSegment>): String? {
    if (segments.isEmpty() || points.isEmpty()) return null
    val sums = segments.indices.map { index ->
        points.sumOf { (it.values.getOrNull(index) ?: 0f).toDouble() }
    }
    val largest = sums.max()
    return if (largest <= 0.0) null else segments[sums.indexOf(largest)].label
}

/**
 * Generiskt **staplat stapeldiagram** (regel 4 — inte en sömn-specifik variant): en stapel
 * per x-position, delad nedifrån och upp i [segments] ordning. Används av Trenders
 * "Sömnstadier" (TRD-16), men vet ingenting om sömn.
 *
 * Poängen med ett staplat diagram i stället för överlagrade linjer är **sammansättningen**:
 * två lika långa nätter kan ha helt olika arkitektur, och det syns direkt i stapeln men inte
 * i fem linjer ovanpå varandra.
 *
 * Y-skalan sätts av anroparen efter staplarnas **totalhöjd** (TRD-7, `computeSmartYAxis` över
 * [stackTotals]), och [gridStep] styr värdelinjerna (TRD-9). Trendlinjen (TRD-13) ritas över
 * totalen. Tvåfingerzoom och panorering fungerar som i [IntervalBarChart] (TRD-10) — samma
 * handrullade gest-hantering, eftersom ingen av dem bygger på Vico. Zoom/pan nollställs när
 * [points] byts (ny period vald).
 */
@Composable
fun StackedBarChart(
    points: List<StackedPoint>,
    segments: List<StackSegment>,
    dates: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.diagram_a11y_default_label),
    minValue: Float = 0f,
    maxValue: Float = 10f,
    gridStep: Float = niceStep(maxValue - minValue),
) {
    val axisLabelColor = MaterialTheme.colorScheme.onSurface
    val gridLineColor = axisLabelColor.copy(alpha = 0.12f)
    val trendColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    val dateLabels = remember(dates) {
        if (dates.isEmpty()) emptyList()
        else dates.map { runCatching { formatShortDate(LocalDate.parse(it)) }.getOrDefault("") }
    }
    val labelStep = max(1, dateLabels.size / MAX_DATE_LABELS)
    val gridValues = remember(minValue, maxValue, gridStep) { gridValuesFor(minValue, maxValue, gridStep) }

    val totals = remember(points) { stackTotals(points) }
    val trend = remember(totals) { computeTrendLine(totals) }

    var scale by remember(points) { mutableFloatStateOf(1f) }
    var offsetX by remember(points) { mutableFloatStateOf(0f) }

    // Ett diagram är en rityta utan text — utan detta finns ingenting för TalkBack att läsa
    // upp. Beskrivningen bär det ett seende öga läser direkt: hur många staplar, hur höga de
    // lägsta och högsta är, vilken kategori som dominerar och åt vilket håll det går.
    val known = totals.filterNotNull()
    val dominant = remember(points, segments) { dominantSegment(points, segments) }
    val description = if (known.isEmpty()) {
        stringResource(R.string.diagram_a11y_empty, label)
    } else {
        stringResource(
            R.string.diagram_a11y_stacked_summary,
            label,
            known.size,
            formatChartValue(known.min()),
            formatChartValue(known.max()),
            dominant ?: stringResource(R.string.diagram_a11y_no_dominant),
            stringResource(
                when {
                    trend == null -> R.string.diagram_a11y_trend_unknown
                    trend.slope > 0f -> R.string.diagram_a11y_trend_rising
                    trend.slope < 0f -> R.string.diagram_a11y_trend_falling
                    else -> R.string.diagram_a11y_trend_flat
                },
            ),
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(if (dateLabels.isEmpty()) 200.dp else 220.dp)
            .pointerInput(points) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    val maxOffset = (size.width * (newScale - 1f)).coerceAtLeast(0f)
                    offsetX = (offsetX + pan.x).coerceIn(-maxOffset, 0f)
                    scale = newScale
                }
            }
            .semantics { contentDescription = description },
    ) {
        val leftMargin = 32.dp.toPx()
        val bottomMargin = if (dateLabels.isEmpty()) 0f else 20.dp.toPx()
        val plotLeft = leftMargin
        val plotRight = size.width
        val plotTop = 8.dp.toPx()
        val plotBottom = size.height - bottomMargin
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        val span = (maxValue - minValue).takeIf { it > 0f } ?: 1f

        fun yOf(v: Float) = plotBottom - ((v - minValue) / span) * plotHeight

        // Värdelinjer (TRD-9) — samma rutnät och etiketter som IntervalBarChart.
        gridValues.forEach { value ->
            val y = yOf(value)
            drawLine(
                color = gridLineColor,
                start = Offset(plotLeft, y),
                end   = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
            )
            drawText(
                textMeasurer = textMeasurer,
                text         = formatChartValue(value),
                topLeft      = Offset(
                    0f,
                    (y - AXIS_LABEL_SIZE.toPx() / 2).coerceIn(0f, size.height - AXIS_LABEL_SIZE.toPx()),
                ),
                style        = TextStyle(color = axisLabelColor, fontSize = AXIS_LABEL_SIZE),
            )
        }

        val n = points.size
        if (n == 0) return@Canvas
        val slotWidth = (plotRight - plotLeft) / n
        val barWidth = (slotWidth * 0.6f).coerceAtLeast(2.dp.toPx())

        fun xOf(i: Int): Float {
            val baseX = plotLeft + slotWidth * (i + 0.5f)
            return plotLeft + (baseX - plotLeft) * scale + offsetX
        }

        clipRect(left = plotLeft, top = 0f, right = plotRight, bottom = size.height) {
            points.forEachIndexed { i, point ->
                val bases = stackBases(point)
                val x = xOf(i)
                point.values.forEachIndexed { segmentIndex, value ->
                    // Ett saknat segment tar ingen höjd — det är en lucka, inte en nolla.
                    if (value == null || value <= 0f) return@forEachIndexed
                    val bottom = yOf(minValue + bases[segmentIndex])
                    val top = yOf(minValue + bases[segmentIndex] + value)
                    drawRect(
                        color   = segments.getOrNull(segmentIndex)?.color ?: axisLabelColor,
                        topLeft = Offset(x - barWidth / 2f, top),
                        size    = Size(barWidth, (bottom - top).coerceAtLeast(0f)),
                    )
                }
            }

            // Trendlinjen (TRD-13) över totalen — streckad, som i övriga diagram.
            trend?.let {
                val firstIndex = totals.indexOfFirst { total -> total != null }
                val lastIndex = totals.indexOfLast { total -> total != null }
                if (firstIndex >= 0 && lastIndex > firstIndex) {
                    drawLine(
                        color = trendColor,
                        start = Offset(xOf(firstIndex), yOf(it.valueAt(firstIndex.toFloat()))),
                        end   = Offset(xOf(lastIndex), yOf(it.valueAt(lastIndex.toFloat()))),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                    )
                }
            }

            if (dateLabels.isNotEmpty()) {
                points.indices.forEach { i ->
                    if (i % labelStep != 0) return@forEach
                    val text = dateLabels.getOrNull(i).orEmpty()
                    if (text.isEmpty()) return@forEach
                    val layout = textMeasurer.measure(
                        text  = text,
                        style = TextStyle(
                            color     = axisLabelColor,
                            fontSize  = AXIS_LABEL_SIZE,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(xOf(i) - layout.size.width / 2f, plotBottom + 4.dp.toPx()),
                    )
                }
            }
        }
    }
}
