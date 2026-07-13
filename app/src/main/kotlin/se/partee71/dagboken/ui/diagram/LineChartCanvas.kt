package se.partee71.dagboken.ui.diagram

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
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
import se.partee71.dagboken.ui.formatShortDate
import java.time.LocalDate

data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Float?>,
)

private data class LineSegment(
    val color: Color,
    val xs: List<Int>,
    val ys: List<Float>,
)

/**
 * Multi-serie linjediagram, Vico-baserat (regel 4 — delar renderingsmotor med
 * [se.partee71.dagboken.ui.home.SparklineChart] i stället för att vara en andra,
 * oberoende handrullad Canvas-implementation).
 *
 * `null`-punkter i en [ChartSeries] ska rendera som luckor (samma beteende som innan).
 * Vicos `series()` tar inte `null`-värden, så varje logisk serie delas här upp i flera
 * sammanhängande Vico-serier runt sina luckor, alla med samma färg — visuellt identiskt
 * med en enda linje med luckor.
 */
@Composable
fun LineChartCanvas(
    series: List<ChartSeries>,
    dates: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    minValue: Float = -10f,
    maxValue: Float = 10f,
    gridValues: List<Float>? = null,
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val dateLabels = remember(dates) {
        if (dates.isEmpty()) emptyList()
        else dates.map { runCatching { formatShortDate(LocalDate.parse(it)) }.getOrDefault("") }
    }

    val segments = remember(series) {
        series.flatMap { s ->
            val segs = mutableListOf<LineSegment>()
            var xs = mutableListOf<Int>()
            var ys = mutableListOf<Float>()
            s.points.forEachIndexed { i, v ->
                if (v == null) {
                    if (xs.isNotEmpty()) {
                        segs += LineSegment(s.color, xs, ys)
                        xs = mutableListOf()
                        ys = mutableListOf()
                    }
                } else {
                    xs += i
                    ys += v
                }
            }
            if (xs.isNotEmpty()) segs += LineSegment(s.color, xs, ys)
            segs
        }
    }

    LaunchedEffect(segments) {
        if (segments.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    segments.forEach { seg -> series(x = seg.xs, y = seg.ys) }
                }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    segments.map { seg ->
                        LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(seg.color)))
                    },
                ),
                rangeProvider = remember(minValue, maxValue) {
                    CartesianLayerRangeProvider.fixed(minY = minValue.toDouble(), maxY = maxValue.toDouble())
                },
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = { _, value, _ -> dateLabels.getOrNull(value.toInt())?.ifEmpty { " " } ?: " " },
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}
