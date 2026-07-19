package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.partee71.dagboken.ui.formatShortDate
import java.time.LocalDate
import kotlin.math.max

/** En dags intervall: [min]–[max] för dagen, med [value] (t.ex. dagsgenomsnittet) markerat. */
data class IntervalPoint(val min: Float, val value: Float, val max: Float)

private const val MAX_DATE_LABELS = 6
private const val MAX_GRID_LINES = 12
private val AXIS_LABEL_SIZE = 11.sp

/** Rutnätsvärden mellan [minValue] och [maxValue], jämnt fördelade med [step] mellanrum. */
private fun gridValuesFor(minValue: Float, maxValue: Float, step: Float): List<Float> {
    if (step <= 0f || maxValue <= minValue) return listOf(minValue, maxValue)
    val values = mutableListOf<Float>()
    var v = minValue
    var guard = 0
    while (v <= maxValue + step * 0.001f && guard < MAX_GRID_LINES) {
        values += v
        v += step
        guard++
    }
    if (values.isEmpty() || values.last() < maxValue - step * 0.001f) values += maxValue
    return values
}

/**
 * Generiskt intervall-/spannstapeldiagram (regel 4 — inte en Energi-specifik variant):
 * en lodrät stapel per dag från [IntervalPoint.min] till [IntervalPoint.max], med
 * [IntervalPoint.value] markerat som en punkt på stapeln och dagsvärdena förbundna med
 * en mjuk kurva (samma bezier-stil som [LineChartCanvas], TRD-6). Används av Trenders
 * "Energi (dag)"-diagram (TRD-8) men är inte begränsad till energi.
 *
 * Byggd som en handrullad `Canvas` i stället för Vico (regel 4-avvägning): Vicos
 * `CandlestickCartesianLayer` är byggd för finansiella upp/ned-candlesticks (bullish/
 * bearish-färgning) — en semantisk missmatchning för ett hälsospann utan
 * uppåt/nedåt-koncept. `null`-punkter renderas som luckor (samma mönster som
 * [LineChartCanvas]/[ChartSeries]) — kurvan bryts vid en lucka.
 *
 * [gridStep] styr avståndet mellan de horisontella värdelinjerna (TRD-9); standardvärdet
 * räknas fram från [minValue]/[maxValue], men anropare som redan har ett [SmartYAxis]
 * (t.ex. via [computeSmartYAxis]) bör skicka in dess `step` så linjerna garanterat landar
 * exakt på [minValue]/[maxValue].
 */
@Composable
fun IntervalBarChart(
    points: List<IntervalPoint?>,
    dates: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    minValue: Float = -10f,
    maxValue: Float = 10f,
    gridStep: Float = niceStep(maxValue - minValue),
) {
    val barColor = MaterialTheme.colorScheme.primary
    val rangeColor = barColor.copy(alpha = 0.35f)
    val axisLabelColor = MaterialTheme.colorScheme.onSurface
    val gridLineColor = axisLabelColor.copy(alpha = 0.12f)
    val textMeasurer = rememberTextMeasurer()

    val dateLabels = remember(dates) {
        if (dates.isEmpty()) emptyList()
        else dates.map { runCatching { formatShortDate(LocalDate.parse(it)) }.getOrDefault("") }
    }
    val labelStep = max(1, dateLabels.size / MAX_DATE_LABELS)
    val gridValues = remember(minValue, maxValue, gridStep) { gridValuesFor(minValue, maxValue, gridStep) }

    val description = remember(points, minValue, maxValue) {
        val known = points.filterNotNull()
        if (known.isEmpty()) {
            "Inga dagar med data"
        } else {
            "Dagsspann, ${known.size} dagar, lägsta ${formatChartValue(known.minOf { it.min })}, " +
                "högsta ${formatChartValue(known.maxOf { it.max })}"
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(if (dateLabels.isEmpty()) 200.dp else 220.dp)
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

        // Värdelinjer (TRD-9) — horisontella rutnätslinjer + etiketter vid varje
        // gridStep-multipel, så det går att avläsa energivärdet utan att gissa.
        // min/max ingår alltid (gridValuesFor inkluderar alltid ändpunkterna).
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
                topLeft      = Offset(0f, (y - AXIS_LABEL_SIZE.toPx() / 2).coerceIn(0f, size.height - AXIS_LABEL_SIZE.toPx())),
                style        = TextStyle(color = axisLabelColor, fontSize = AXIS_LABEL_SIZE),
            )
        }

        val n = points.size
        if (n == 0) return@Canvas
        val slotWidth = (plotRight - plotLeft) / n
        val barWidth = (slotWidth * 0.35f).coerceAtLeast(2.dp.toPx())

        fun xOf(i: Int) = plotLeft + slotWidth * (i + 0.5f)

        // Spannstaplarna (min–max per dag) ritas under kurvan och prickarna.
        points.forEachIndexed { i, point ->
            if (point == null) return@forEachIndexed
            val x = xOf(i)
            drawLine(
                color = rangeColor,
                start = Offset(x, yOf(point.max)),
                end   = Offset(x, yOf(point.min)),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }

        // Dagsvärdena förbundna med en mjuk kurva (samma S-kurve-bezier-teknik som
        // Vicos PointConnector.cubic(), TRD-6) — bryts vid en lucka (null-punkt).
        val curvePath = Path()
        var curveOpen = false
        var prevX = 0f
        var prevY = 0f
        points.forEachIndexed { i, point ->
            if (point == null) {
                curveOpen = false
                return@forEachIndexed
            }
            val x = xOf(i)
            val y = yOf(point.value)
            if (!curveOpen) {
                curvePath.moveTo(x, y)
                curveOpen = true
            } else {
                val midX = (prevX + x) / 2f
                curvePath.cubicTo(midX, prevY, midX, y, x, y)
            }
            prevX = x
            prevY = y
        }
        drawPath(
            path = curvePath,
            color = barColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )

        // Dagsvärdesprickar och x-axelns dagsetiketter — ritas sist, ovanpå kurvan.
        points.forEachIndexed { i, point ->
            if (point == null) return@forEachIndexed
            val x = xOf(i)
            drawCircle(
                color = barColor,
                radius = (barWidth / 2 + 1.dp.toPx()),
                center = Offset(x, yOf(point.value)),
            )

            if (dateLabels.isNotEmpty() && i % labelStep == 0) {
                val label = dateLabels.getOrNull(i).orEmpty()
                if (label.isNotEmpty()) {
                    val layout = textMeasurer.measure(
                        text  = label,
                        style = TextStyle(
                            color     = axisLabelColor,
                            fontSize  = AXIS_LABEL_SIZE,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(x - layout.size.width / 2f, plotBottom + 4.dp.toPx()),
                    )
                }
            }
        }
    }
}
