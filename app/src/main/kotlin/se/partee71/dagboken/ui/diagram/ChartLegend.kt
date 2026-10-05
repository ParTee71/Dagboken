package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/** Hur en rad i teckenförklaringen markeras: punkt (serie), ruta (segment) eller streckad linje (trend). */
internal enum class LegendMark { Dot, Square, Dashed }

@Immutable
internal data class LegendItem(val label: String, val color: Color, val mark: LegendMark = LegendMark.Dot)

/**
 * Teckenförklaringen under ett diagram (TRD-13, TRD-16, TRD-18): färgen bär aldrig ensam
 * informationen – varje rad har sin text. Läses som en text per rad.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChartLegend(items: List<LegendItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Mark(item)
                Text(item.label, style = AppTypography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Mark(item: LegendItem) {
    when (item.mark) {
        LegendMark.Dot -> Box(Modifier.size(LEGEND_SWATCH).background(item.color, CircleShape))
        LegendMark.Square -> Box(Modifier.size(LEGEND_SWATCH).background(item.color, RectangleShape))
        LegendMark.Dashed -> Canvas(Modifier.size(LEGEND_DASH_WIDTH, LEGEND_SWATCH)) {
            drawLine(
                color = item.color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = TREND_WIDTH.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(TREND_DASH.toPx() / 2, TREND_GAP.toPx())),
            )
        }
    }
}
