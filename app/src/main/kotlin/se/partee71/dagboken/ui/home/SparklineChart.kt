package se.partee71.dagboken.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import se.partee71.dagboken.ui.diagram.computeSmartYAxis
import se.partee71.dagboken.ui.diagram.computeTrendLine
import se.partee71.dagboken.ui.diagram.formatChartValue

/**
 * 7-day energy sparkline (HEM-7), Vico-baserat — delar renderingsmotor med
 * [se.partee71.dagboken.ui.diagram.LineChartCanvas] i stället för att vara en andra,
 * oberoende handrullad Canvas-implementation (regel 4).
 *
 * Minimigränsen på 2 datapunkter (HEM-7) hålls av anroparen (`HomeScreen`); denna
 * komponent skyddar sig ändå defensivt mot färre punkter.
 *
 * Ritar y-axel (värdeskala) och, när [xLabels] anges, x-axel (dagsetiketter) — se #132.
 */
@Composable
fun SparklineChart(
    points: List<Float>,    // energy values 1..10, or step counts etc.
    modifier: Modifier = Modifier,
    xLabels: List<String> = emptyList(),
) {
    if (points.size < 2) return

    val modelProducer = remember { CartesianChartModelProducer() }
    val lineColor = MaterialTheme.colorScheme.primary
    // Trendlinjen (TRD-13) räknas in i axelspannet så den aldrig hamnar utanför.
    val trend = remember(points) { computeTrendLine(points) }
    val yAxis = remember(points, trend) {
        val trendValues = trend?.let { listOf(it.valueAt(0f), it.valueAt((points.size - 1).toFloat())) }.orEmpty()
        computeSmartYAxis(points + trendValues)
    }

    LaunchedEffect(points, trend) {
        modelProducer.runTransaction {
            lineSeries {
                series(y = points)
                trend?.let { series(x = listOf(0, points.size - 1), y = listOf(it.valueAt(0f), it.valueAt((points.size - 1).toFloat()))) }
            }
        }
    }

    // Axeletiketterna följer temat explicit (samma fix som LineChartCanvas, #123).
    val axisLabelColor = MaterialTheme.colorScheme.onSurface
    val axisLabel = rememberAxisLabelComponent(color = axisLabelColor)

    // Y-axeln ska alltid visa heltal (#170), samma mönster som LineChartCanvas.
    val yValueFormatter = remember { CartesianValueFormatter { _, value, _ -> formatChartValue(value.toFloat()) } }
    val yItemPlacer = remember(yAxis.step) { VerticalAxis.ItemPlacer.step(step = { yAxis.step.toDouble() }) }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    buildList {
                        add(
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
                                areaFill = LineCartesianLayer.AreaFill.single(fill = fill(lineColor.copy(alpha = 0.24f))),
                                pointConnector = LineCartesianLayer.PointConnector.cubic(),
                            ),
                        )
                        if (trend != null) {
                            add(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
                                    stroke = LineCartesianLayer.LineStroke.Dashed(),
                                ),
                            )
                        }
                    },
                ),
                rangeProvider = remember(yAxis.range) {
                    CartesianLayerRangeProvider.fixed(minY = yAxis.range.start.toDouble(), maxY = yAxis.range.endInclusive.toDouble())
                },
            ),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel,
                valueFormatter = yValueFormatter,
                itemPlacer = yItemPlacer,
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel,
                valueFormatter = { _, value, _ -> xLabels.getOrNull(value.toInt())?.ifEmpty { " " } ?: " " },
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(if (xLabels.isEmpty()) 60.dp else 76.dp),
    )
}
