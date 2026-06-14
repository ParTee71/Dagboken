package se.partee71.dagboken.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.Amber400
import se.partee71.dagboken.ui.theme.Emerald400
import se.partee71.dagboken.ui.theme.Rose500

/**
 * 7-day energy sparkline. Ports the SVG sparkline from Home.jsx to Compose Canvas.
 * Points are color-coded: green ≥7, amber ≥5, red <5.
 */
@Composable
fun SparklineChart(
    points: List<Float>,    // energy values 1..10
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
    ) {
        val w = size.width
        val h = size.height
        val count = points.size
        if (count < 2) return@Canvas

        val xStep = w / (count - 1).coerceAtLeast(1)

        fun xAt(i: Int) = i * xStep
        fun yAt(v: Float) = h - (v / 10f).coerceIn(0f, 1f) * h * 0.85f - h * 0.075f

        // Draw connecting line
        val path = Path()
        path.moveTo(xAt(0), yAt(points[0]))
        for (i in 1 until count) {
            path.lineTo(xAt(i), yAt(points[i]))
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // Draw dots color-coded by energy zone
        points.forEachIndexed { i, v ->
            val dotColor = when {
                v >= 7f -> Emerald400
                v >= 5f -> Amber400
                else    -> Rose500
            }
            drawCircle(
                color  = dotColor,
                radius = 4.dp.toPx(),
                center = Offset(xAt(i), yAt(v)),
            )
        }
    }
}
