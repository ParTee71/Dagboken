package se.partee71.dagboken.ui.diagram

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.Zoom
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

    // Kör transaktionen även när segments är tom, så en tidigare renderad linje
    // faktiskt rensas (i stället för att stå kvar) om valet ändras till en serie
    // utan datapunkter — #141.
    LaunchedEffect(segments) {
        modelProducer.runTransaction {
            if (segments.isNotEmpty()) {
                lineSeries {
                    segments.forEach { seg -> series(x = seg.xs, y = seg.ys) }
                }
            }
        }
    }

    // Axeletiketterna följer temat explicit (i stället för Vicos inbyggda standardfärg)
    // eftersom den inte har tillräcklig kontrast mot appens mörka bakgrund (#123).
    val axisLabelColor = MaterialTheme.colorScheme.onSurface
    val axisLabel = rememberAxisLabelComponent(color = axisLabelColor)

    // Tvåfingerzoom + panorering (#144), nollställd när diagrammets period byts (#149) —
    // nycklad på [dates] (byts alltid med perioden) i stället för ett bart `remember`,
    // annars låg gammal zoom/pan-position kvar när användaren bytte period.
    //
    // initialZoom = Zoom.Content åsidosätter Vicos standardvärde
    // (Zoom.max(Zoom.fixed(), Zoom.Content), som väljer den STÖRRE — dvs. mer inzoomade —
    // av de två): för en period med många datapunkter valde standardvärdet den fasta
    // zoomen i stället för att zooma ut till att visa hela perioden (uppföljning till #149,
    // som bara fixade att zoom/pan nollställs — inte vad de nollställs *till*). Content
    // garanterar att diagrammet alltid startar helt utzoomat, oavsett antal datapunkter.
    val (scrollState, zoomState) = key(dates) {
        rememberVicoScrollState(scrollEnabled = true) to
            rememberVicoZoomState(zoomEnabled = true, initialZoom = Zoom.Content)
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    segments.map { seg ->
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(seg.color)),
                            areaFill = LineCartesianLayer.AreaFill.single(fill = fill(seg.color.copy(alpha = 0.24f))),
                            pointConnector = LineCartesianLayer.PointConnector.cubic(),
                        )
                    },
                ),
                rangeProvider = remember(minValue, maxValue) {
                    CartesianLayerRangeProvider.fixed(minY = minValue.toDouble(), maxY = maxValue.toDouble())
                },
            ),
            startAxis = VerticalAxis.rememberStart(label = axisLabel),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel,
                valueFormatter = { _, value, _ -> dateLabels.getOrNull(value.toInt())?.ifEmpty { " " } ?: " " },
            ),
        ),
        modelProducer = modelProducer,
        scrollState = scrollState,
        zoomState = zoomState,
        modifier = modifier,
    )
}
