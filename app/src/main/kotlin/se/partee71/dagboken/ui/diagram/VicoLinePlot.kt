package se.partee71.dagboken.ui.diagram

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.math.roundToInt
import se.partee71.dagboken.core.engine.GapFreeRun
import se.partee71.dagboken.core.engine.SmartYAxis
import se.partee71.dagboken.core.engine.TrendSegment
import se.partee71.dagboken.core.engine.formatChartValue
import se.partee71.dagboken.core.engine.xLabelStep

// Linjediagrammens gemensamma Vico-ritning (TRD-6, TRD-7, TRD-10, TRD-13) för `LineChart` och
// `SparklineChart` – Vico används bara här i appen (ui-forbidden.txt [vico]). Modellen byggs direkt
// ur datan (ingen modellproducent): ingen asynkron transaktion, ingen inanimering och samma bild varje
// gång, och den tomma linjelistan som fällde 3.x (#141) kan inte uppstå – ett diagram med för lite
// data ritas aldrig (ChartFrame).

/** Hur en kurva ritas: [dashed] = trendlinje, [area] = gradientfyllning under, [points] = punkter. */
@Immutable
internal data class PlotStyle(
    val color: Color,
    val dashed: Boolean = false,
    val area: Boolean = false,
    val points: Boolean = false,
    /** x för dagens punkt (solgul med ring, HEM-7); `null` = ingen. */
    val highlightX: Int? = null,
)

/** En kurva i diagrammet: x-värden (index), y-värden och stil. */
@Immutable
internal data class PlotLine(val xs: List<Int>, val ys: List<Float>, val style: PlotStyle)

internal fun GapFreeRun.toPlotLine(style: PlotStyle) = PlotLine(xs, values, style)

internal fun TrendSegment.toPlotLine(color: Color) =
    PlotLine(listOf(startX, endX), listOf(startY, endY), PlotStyle(color, dashed = true))

/**
 * Ritar [lines] på y-axeln [axis] (heltal, TRD-7) över [xCount] x-positioner med glesa
 * [xLabels]. [zoomable] slår på tvåfingerzoom och panorering (TRD-10); diagrammet startar alltid
 * helt utzoomat (`Zoom.Content`) – anroparen nollställer det vid periodbyte med `key`.
 */
@Composable
internal fun VicoLinePlot(
    lines: List<PlotLine>,
    axis: SmartYAxis,
    xCount: Int,
    xLabels: List<String>,
    height: Dp,
    zoomable: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = chartColors
    val model = remember(lines) {
        CartesianChartModel(LineCartesianLayerModel.build { lines.forEach { series(it.xs, it.ys) } })
    }
    val vicoLines = remember(lines, colors, axis) { lines.map { it.style.toVicoLine(colors, axis) } }
    val layer = rememberLineCartesianLayer(
        lineProvider = remember(vicoLines) { LineCartesianLayer.LineProvider.series(vicoLines) },
        rangeProvider = remember(axis, xCount) {
            CartesianLayerRangeProvider.fixed(
                minX = 0.0,
                maxX = (xCount - 1).coerceAtLeast(1).toDouble(),
                minY = axis.range.start.toDouble(),
                maxY = axis.range.endInclusive.toDouble(),
            )
        },
    )
    val label = rememberTextComponent(style = chartLabelStyle, margins = Insets(AXIS_LABEL_GAP))
    val yFormatter = remember { CartesianValueFormatter { _, value, _ -> formatChartValue(value.toFloat()) } }
    val xFormatter = remember(xLabels) {
        CartesianValueFormatter { _, value, _ -> xLabels.getOrNull(value.roundToInt())?.ifEmpty { " " } ?: " " }
    }
    val chart = rememberCartesianChart(
        layer,
        startAxis = VerticalAxis.rememberStart(
            line = null,
            label = label,
            valueFormatter = yFormatter,
            tick = null,
            guideline = rememberAxisGuidelineComponent(fill = Fill(colors.grid), thickness = GRID_WIDTH),
            itemPlacer = remember(axis.step) { VerticalAxis.ItemPlacer.step(step = { axis.step.toDouble() }) },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            line = rememberAxisLineComponent(fill = Fill(colors.axis), thickness = GRID_WIDTH),
            label = if (xLabels.isEmpty()) null else label,
            valueFormatter = xFormatter,
            tick = null,
            guideline = null,
            itemPlacer = remember(xCount) { HorizontalAxis.ItemPlacer.aligned(spacing = { xLabelStep(xCount) }) },
        ),
        layerPadding = remember { { CartesianLayerPadding(unscalableStart = EDGE_PADDING, unscalableEnd = EDGE_PADDING) } },
        // Ett steg per dag även när luckor gör att de kända x-värdena ligger glesare.
        getXStep = ONE_DAY_X_STEP,
    )
    CartesianChartHost(
        chart = chart,
        model = model,
        modifier = modifier,
        scrollState = rememberVicoScrollState(scrollEnabled = zoomable),
        zoomState = rememberVicoZoomState(zoomEnabled = zoomable, initialZoom = remember { Zoom.Content }),
        chartAreaHeight = height,
    )
}

private fun PlotStyle.toVicoLine(colors: ChartColors, axis: SmartYAxis): LineCartesianLayer.Line {
    val stroke = if (dashed) {
        LineCartesianLayer.LineStroke.Dashed(thickness = TREND_WIDTH, cap = StrokeCap.Round, dashLength = TREND_DASH, gapLength = TREND_GAP)
    } else {
        LineCartesianLayer.LineStroke.Continuous(thickness = LINE_WIDTH, cap = StrokeCap.Round)
    }
    val areaFill = if (area) {
        LineCartesianLayer.AreaFill.single(
            fill = Fill(Brush.verticalGradient(listOf(color.copy(alpha = colors.fillTop.alpha), Color.Transparent))),
            splitY = { axis.range.start },
        )
    } else {
        null
    }
    val pointProvider = if (points) {
        HighlightPoints(
            normal = LineCartesianLayer.Point(ShapeComponent(fill = Fill(color), shape = CircleShape), POINT_SIZE),
            highlight = highlightX?.let {
                LineCartesianLayer.Point(
                    ShapeComponent(fill = Fill(colors.today), shape = CircleShape, strokeFill = Fill(colors.todayRing), strokeThickness = TODAY_RING_WIDTH),
                    TODAY_POINT_SIZE,
                )
            },
            highlightX = highlightX,
        )
    } else {
        null
    }
    return LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = stroke,
        areaFill = areaFill,
        pointProvider = pointProvider,
        interpolator = if (dashed) LineCartesianLayer.Interpolator.Sharp else LineCartesianLayer.Interpolator.cubic(),
    )
}

/** x-steget: en dag (eller natt) per steg. */
private val ONE_DAY_X_STEP: (CartesianChartModel, Double, Double) -> Double = { _, _, _ -> 1.0 }

/** Punkterna på en kurva; punkten på [highlightX] (dagens) ritas som [highlight]. */
private data class HighlightPoints(
    private val normal: LineCartesianLayer.Point,
    private val highlight: LineCartesianLayer.Point?,
    private val highlightX: Int?,
) : LineCartesianLayer.PointProvider {
    override fun getPoint(entry: LineCartesianLayerModel.Entry, extraStore: ExtraStore): LineCartesianLayer.Point =
        if (highlight != null && entry.x.roundToInt() == highlightX) highlight else normal

    override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point = highlight ?: normal
}
