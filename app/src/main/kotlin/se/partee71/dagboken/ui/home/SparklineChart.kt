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
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer

/**
 * 7-day energy sparkline (HEM-7), Vico-baserat — delar renderingsmotor med
 * [se.partee71.dagboken.ui.diagram.LineChartCanvas] i stället för att vara en andra,
 * oberoende handrullad Canvas-implementation (regel 4).
 *
 * Minimigränsen på 2 datapunkter (HEM-7) hålls av anroparen (`HomeScreen`); denna
 * komponent skyddar sig ändå defensivt mot färre punkter.
 */
@Composable
fun SparklineChart(
    points: List<Float>,    // energy values 1..10
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val modelProducer = remember { CartesianChartModelProducer() }
    val lineColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries { series(y = points) }
        }
    }

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
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
    )
}
