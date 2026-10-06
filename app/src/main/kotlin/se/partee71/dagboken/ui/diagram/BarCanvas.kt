package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import se.partee71.dagboken.core.engine.BarViewport
import se.partee71.dagboken.core.engine.SmartYAxis
import se.partee71.dagboken.core.engine.TrendSegment
import se.partee71.dagboken.core.engine.ZoomPan
import se.partee71.dagboken.core.engine.formatChartValue
import se.partee71.dagboken.core.engine.gridValuesFor
import se.partee71.dagboken.core.engine.xLabelStepFitting

/** Stapelns bredd: andel av platsen (växer med zoomen) och största bredd, minst 2 dp. */
internal enum class BarStyle(val fraction: Float, val max: Dp) {
    /** Dagens spann i `IntervalBarChart`. */
    Span(0.35f, MAX_SPAN_WIDTH),

    /** Nattens stapel i `StackedBarChart`. */
    Stack(0.6f, MAX_STACK_WIDTH),
}


/**
 * Stapeldiagrammens gemensamma rityta (`IntervalBarChart`, `StackedBarChart`) – egen `Canvas`, eftersom
 * Vicos stapellager inte ritar spann eller luckor på det sätt TRD-8 och TRD-16 kräver (som i 3.x). Här
 * finns allt de delar: värdelinjer med heltalsetiketter (TRD-7, TRD-9), glesa x-etiketter, den
 * streckade trendlinjen (TRD-13) och tvåfingerzoom med panorering (TRD-10), helt utzoomat från början
 * och nollställt när [resetKey] byts (anroparen nycklar på data och etiketter). Panoreringen begränsas
 * av ritytan, inte hela bredden. Stapelns bredd ([barStyle]) räknas här en gång. [drawBars] ritar själva staplarna i ritytan (klippt i x så att
 * zoomat innehåll aldrig hamnar över y-etiketterna). Koordinaterna räknas i `:core` ([BarViewport]).
 */
@Composable
internal fun BarCanvas(
    count: Int,
    axis: SmartYAxis,
    xLabels: List<String>,
    trend: TrendSegment?,
    resetKey: Any?,
    description: String,
    barStyle: BarStyle,
    modifier: Modifier = Modifier,
    height: Dp = CHART_HEIGHT,
    drawBars: DrawScope.(viewport: BarViewport, barWidth: Float) -> Unit,
) {
    val colors = chartColors
    val trendColor = colors.trend
    val labelStyle = chartLabelStyle
    val measurer = rememberTextMeasurer()
    var zoomPan by remember(resetKey) { mutableStateOf(ZoomPan()) }
    val grid = remember(axis) { gridValuesFor(axis.range.start, axis.range.endInclusive, axis.step) }
    // Etiketterna mäts en gång per data och stil, inte i varje bildruta.
    val yLabels = remember(grid, labelStyle, measurer) { grid.map { measurer.measure(formatChartValue(it), labelStyle) } }
    val xLayouts = remember(xLabels, labelStyle, measurer) { xLabels.map { text -> text.takeIf { it.isNotEmpty() }?.let { measurer.measure(it, labelStyle) } } }
    val frame = remember(yLabels, measurer, labelStyle, xLabels.isEmpty(), axis, count) {
        BarFrame(
            labelWidth = yLabels.maxOfOrNull { it.size.width } ?: 0,
            labelHeight = measurer.measure("0", labelStyle).size.height.toFloat(),
            hasXLabels = xLabels.isNotEmpty(),
            axis = axis,
            count = count,
        )
    }
    val currentFrame by rememberUpdatedState(frame)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(resetKey) {
                detectChartZoom(isZoomed = { zoomPan.scale > 1f }) { zoom, panX ->
                    val plot = currentFrame.viewport(this, size.width.toFloat(), size.height.toFloat(), zoomPan)
                    zoomPan = plot.transform(zoom, panX)
                }
            }
            .chartDescription(description),
    ) {
        val viewport = frame.viewport(this, size.width, size.height, zoomPan)
        val gap = AXIS_LABEL_GAP.toPx()

        // Värdelinjer och deras heltalsetiketter (TRD-9) – utanför klippningen, de följer inte zoomen.
        grid.forEachIndexed { i, value ->
            val y = viewport.yOf(value)
            drawLine(colors.grid, Offset(viewport.left, y), Offset(size.width, y), strokeWidth = GRID_WIDTH.toPx())
            val layout = yLabels[i]
            drawText(layout, topLeft = Offset(viewport.left - gap - layout.size.width, y - layout.size.height / 2f))
        }
        if (count == 0) return@Canvas

        val barWidth = (viewport.slotWidth * barStyle.fraction * viewport.zoomPan.scale).coerceIn(MIN_BAR_WIDTH.toPx(), barStyle.max.toPx())
        clipRect(left = viewport.left, top = 0f, right = size.width, bottom = size.height) {
            drawBars(viewport, barWidth)
            trend?.let {
                drawLine(
                    color = trendColor,
                    start = Offset(viewport.xOf(it.startX), viewport.yOf(it.startY)),
                    end = Offset(viewport.xOf(it.endX), viewport.yOf(it.endY)),
                    strokeWidth = TREND_WIDTH.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(TREND_DASH.toPx(), TREND_GAP.toPx())),
                )
            }
        }
        // X-etiketterna glesas ut efter uppmätt bredd så att de aldrig trängs (TRD-6), centreras på sin plats och
        // kläms in i ritytans bredd (kantetiketten får ligga under y-etiketterna, där inget annat ritas); en etikett
        // vars plats panorerats ut ur ritytan ritas inte.
        val widest = xLayouts.maxOfOrNull { it?.size?.width ?: 0 }?.toFloat() ?: 0f
        val step = xLabelStepFitting(xLayouts.size, widest, viewport.right - viewport.left, gap * 2)
        xLayouts.forEachIndexed { i, layout ->
            if (layout == null || i % step != 0) return@forEachIndexed
            val x = viewport.xOf(i)
            if (x < viewport.left || x > viewport.right) return@forEachIndexed
            val left = (x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(left, viewport.bottom + gap))
        }
    }
}

/**
 * Dagsvärdena som en mjuk kurva (S-kurva som Vicos kubiska) genom [points] (`null` = lucka, kurvan bryts),
 * med en punkt på varje värde – `IntervalBarChart`s dagsvärden (TRD-8) och linjen i `StackedBarChart`
 * (TRD-21). [dotColor] ger punktens färg per index; `null` ritar ingen punkt där.
 */
internal fun DrawScope.drawSmoothCurve(points: List<Float?>, viewport: BarViewport, color: Color, dotColor: (Int) -> Color?) {
    val curve = Path()
    var open = false
    var previous = Offset.Zero
    points.forEachIndexed { i, value ->
        if (value == null) {
            open = false
            return@forEachIndexed
        }
        val p = Offset(viewport.xOf(i), viewport.yOf(value))
        if (open) {
            val midX = (previous.x + p.x) / 2f
            curve.cubicTo(midX, previous.y, midX, p.y, p.x, p.y)
        } else {
            curve.moveTo(p.x, p.y)
            open = true
        }
        previous = p
    }
    drawPath(curve, color, style = Stroke(width = LINE_WIDTH.toPx(), cap = StrokeCap.Round))
    points.forEachIndexed { i, value ->
        if (value == null) return@forEachIndexed
        val dot = dotColor(i) ?: return@forEachIndexed
        drawCircle(dot, radius = INTERVAL_DOT_RADIUS.toPx(), center = Offset(viewport.xOf(i), viewport.yOf(value)))
    }
}

/** Det som avgör ritytans kanter: y-etiketternas bredd, etiketthöjden och om x-etiketter finns. */
private data class BarFrame(val labelWidth: Int, val labelHeight: Float, val hasXLabels: Boolean, val axis: SmartYAxis, val count: Int) {
    fun viewport(density: Density, width: Float, height: Float, zoomPan: ZoomPan): BarViewport = with(density) {
        val gap = AXIS_LABEL_GAP.toPx()
        BarViewport(
            left = labelWidth + gap * 2,
            top = labelHeight / 2,
            right = width - EDGE_PADDING.toPx(),
            bottom = height - if (hasXLabels) labelHeight + gap else labelHeight / 2,
            minValue = axis.range.start,
            maxValue = axis.range.endInclusive,
            count = count,
            zoomPan = zoomPan,
        )
    }
}

/**
 * Tvåfingerzoom och panorering i sidled (TRD-10). Till skillnad från 3.x (`detectTransformGestures`)
 * tas ett enfingersdrag bara när diagrammet är inzoomat och draget går i sidled – annars får sidan
 * runt omkring rulla som vanligt.
 */
private suspend fun PointerInputScope.detectChartZoom(isZoomed: () -> Boolean, onGesture: (zoom: Float, panX: Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val fingers = event.changes.count { it.pressed }
            val pan = event.calculatePan()
            if (fingers >= 2 || (isZoomed() && abs(pan.x) > abs(pan.y))) {
                onGesture(event.calculateZoom(), pan.x)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
