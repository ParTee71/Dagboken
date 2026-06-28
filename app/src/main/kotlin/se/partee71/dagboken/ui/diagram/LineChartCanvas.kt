package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Float?>,
)

@Composable
fun LineChartCanvas(
    series: List<ChartSeries>,
    dates: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    minValue: Float = -10f,
    maxValue: Float = 10f,
    gridValues: List<Float>? = null,
) {
    val gridColor   = MaterialTheme.colorScheme.outlineVariant
    val labelColor  = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    val dateLabels = remember(dates) {
        if (dates.isEmpty()) emptyList()
        else {
            val fmt = DateTimeFormatter.ofPattern("d MMM", Locale("sv", "SE"))
            dates.map { runCatching { LocalDate.parse(it).format(fmt) }.getOrDefault("") }
        }
    }

    val n = series.firstOrNull()?.points?.size ?: 0
    val labelIndices = remember(n, dateLabels) {
        if (dateLabels.isEmpty() || n == 0) emptyList()
        else {
            val target = 5
            val step   = maxOf(1, (n - 1) / (target - 1))
            val result = (0 until n step step).toMutableList()
            if (n > 1 && result.last() != n - 1) result.add(n - 1)
            result
        }
    }

    Canvas(modifier = modifier) {
        val padL   = 44f
        val padR   = 16f
        val padT   = 16f
        val padB   = if (dateLabels.isNotEmpty()) 56f else 32f
        val chartW = size.width - padL - padR
        val chartH = size.height - padT - padB
        val range  = maxValue - minValue

        fun valueToY(v: Float) = padT + chartH * (1f - (v - minValue) / range)
        fun indexToX(i: Int)   = padL + if (n > 1) i * chartW / (n - 1).toFloat() else chartW / 2f

        val yPaint = android.graphics.Paint().apply {
            textSize    = 28f
            textAlign   = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
            color = android.graphics.Color.argb(
                (labelColor.alpha * 200).toInt(),
                (labelColor.red   * 255).toInt(),
                (labelColor.green * 255).toInt(),
                (labelColor.blue  * 255).toInt(),
            )
        }
        val xPaint = android.graphics.Paint().apply {
            textSize    = 26f
            textAlign   = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            color = android.graphics.Color.argb(
                (labelColor.alpha * 170).toInt(),
                (labelColor.red   * 255).toInt(),
                (labelColor.green * 255).toInt(),
                (labelColor.blue  * 255).toInt(),
            )
        }

        // Grid
        val gridLevels = gridValues
            ?: listOf(minValue, minValue + (maxValue - minValue) / 2f, maxValue)
        gridLevels.zipWithNext { a, b -> (a + b) / 2f }.forEach { v ->
            val y = valueToY(v)
            drawLine(gridColor.copy(alpha = 0.18f), Offset(padL, y), Offset(padL + chartW, y), 0.6f)
        }
        gridLevels.forEach { v ->
            val y          = valueToY(v)
            val isBaseline = v == minValue
            drawLine(
                color       = gridColor.copy(alpha = if (isBaseline) 0.88f else 0.52f),
                start       = Offset(padL, y),
                end         = Offset(padL + chartW, y),
                strokeWidth = if (isBaseline) 1.8f else 1.0f,
            )
            val label = if (v == v.toLong().toFloat()) v.toLong().toString() else "%.1f".format(v)
            drawContext.canvas.nativeCanvas.drawText(label, padL - 8f, y + 9f, yPaint)
        }

        // X-axis date labels + tick marks
        val axisY = padT + chartH
        labelIndices.forEach { i ->
            val label = dateLabels.getOrNull(i) ?: return@forEach
            val x = indexToX(i)
            drawLine(gridColor.copy(alpha = 0.35f), Offset(x, axisY), Offset(x, axisY + 7f), 1f)
            drawContext.canvas.nativeCanvas.drawText(label, x, axisY + 30f, xPaint)
        }

        if (n < 1) return@Canvas

        series.forEach { s ->
            // Collect continuous segments
            val segments = mutableListOf<List<Pair<Float, Float>>>()
            var current  = mutableListOf<Pair<Float, Float>>()
            s.points.forEachIndexed { i, v ->
                if (v == null) {
                    if (current.isNotEmpty()) { segments += current.toList(); current = mutableListOf() }
                } else {
                    current += indexToX(i) to valueToY(v)
                }
            }
            if (current.isNotEmpty()) segments += current.toList()

            val bottomY = padT + chartH

            segments.forEach { pts ->
                val linePath = Path()
                linePath.moveTo(pts[0].first, pts[0].second)
                for (k in 1 until pts.size) {
                    val (x0, y0) = pts[k - 1]
                    val (x1, y1) = pts[k]
                    val dx = (x1 - x0) * 0.38f
                    linePath.cubicTo(x0 + dx, y0, x1 - dx, y1, x1, y1)
                }

                if (pts.size >= 2) {
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(pts.last().first, bottomY)
                        lineTo(pts.first().first, bottomY)
                        close()
                    }
                    drawPath(
                        path  = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(s.color.copy(alpha = 0.20f), Color.Transparent),
                            startY = padT,
                            endY   = bottomY,
                        ),
                    )
                }

                drawPath(linePath, s.color, style = Stroke(
                    width = 3.5f,
                    cap   = StrokeCap.Round,
                    join  = StrokeJoin.Round,
                ))
            }

            // Dots: colored ring + white centre
            s.points.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val cx = indexToX(i)
                val cy = valueToY(v)
                drawCircle(s.color,      radius = 6.5f, center = Offset(cx, cy))
                drawCircle(surfaceColor, radius = 3.5f, center = Offset(cx, cy))
            }
        }
    }
}
