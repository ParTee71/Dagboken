package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Float?>,
)

@Composable
fun LineChartCanvas(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    minValue: Float = -10f,
    maxValue: Float = 10f,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val padL = 40f
        val padR = 16f
        val padT = 16f
        val padB = 32f
        val chartW = size.width - padL - padR
        val chartH = size.height - padT - padB
        val range = maxValue - minValue

        // Zero line
        val zeroY = padT + chartH * (1f - (-minValue) / range)
        drawLine(gridColor, Offset(padL, zeroY), Offset(padL + chartW, zeroY), strokeWidth = 1f)

        // Grid lines at -10, -5, 0, 5, 10
        listOf(minValue, minValue / 2, 0f, maxValue / 2, maxValue).forEach { v ->
            val y = padT + chartH * (1f - (v - minValue) / range)
            drawLine(
                color = gridColor.copy(alpha = 0.3f),
                start = Offset(padL, y),
                end = Offset(padL + chartW, y),
                strokeWidth = 0.5f,
            )
        }

        val count = series.firstOrNull()?.points?.size ?: return@Canvas
        if (count < 1) return@Canvas
        val step = if (count > 1) chartW / (count - 1).toFloat() else chartW

        series.forEach { s ->
            val path = Path()
            var started = false
            s.points.forEachIndexed { i, v ->
                if (v == null) { started = false; return@forEachIndexed }
                val x = padL + i * step
                val y = padT + chartH * (1f - (v - minValue) / range)
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            drawPath(path, s.color, style = Stroke(width = 4f))

            // Dots — use series color so multi-series charts remain readable
            s.points.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val x = padL + i * step
                val y = padT + chartH * (1f - (v - minValue) / range)
                drawCircle(s.color, radius = 5f, center = Offset(x, y))
                drawCircle(Color.Black.copy(alpha = 0.4f), radius = 5f, center = Offset(x, y), style = Stroke(1f))
            }
        }
    }
}
