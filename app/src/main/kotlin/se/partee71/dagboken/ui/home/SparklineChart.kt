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
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import se.partee71.dagboken.ui.diagram.computeSmartYRange

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
    val yRange = remember(points) { computeSmartYRange(points) }

    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries { series(y = points) }
        }
    }

    // Axeletiketterna följer temat explicit (samma fix som LineChartCanvas, #123).
    val axisLabelColor = MaterialTheme.colorScheme.onSurface
    val axisLabel = rememberAxisLabelComponent(color = axisLabelColor)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    listOf(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
                            areaFill = LineCartesianLayer.AreaFill.single(fill = fill(lineColor.copy(alpha = 0.24f))),
                            pointConnector = LineCartesianLayer.PointConnector.cubic(),
                        ),
                    ),
                ),
                rangeProvider = remember(yRange) {
                    CartesianLayerRangeProvider.fixed(minY = yRange.start.toDouble(), maxY = yRange.endInclusive.toDouble())
                },
            ),
            startAxis = VerticalAxis.rememberStart(label = axisLabel),
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
