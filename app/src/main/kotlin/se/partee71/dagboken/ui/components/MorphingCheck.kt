@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.dagboken.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.IconSize

/**
 * Det formmorfande krysset – en ring som fjädrar till en fylld "kaka" med bock när [checked] (skill `ui-style`).
 * Bara visning, utan semantik: raden som visar det bär rollen – `CheckRow` (en kryssruta) och `OccasionRow`
 * (loggat tillfälle, en klickbar rad). Läggs i en `LeadingSlot`, så att titlarna linjerar.
 */
@Composable
internal fun MorphingCheck(checked: Boolean) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "kryss"
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(SIZE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(SIZE)) {
            val path = morph.toPath(progress)
            val stroke = STROKE.toPx()
            val inner = size.minDimension - stroke
            val offset = Offset(stroke / 2, stroke / 2)
            translate(offset.x, offset.y) {
                scale(inner, pivot = Offset.Zero) {
                    if (progress < 1f) drawPath(path, color, alpha = 1f - progress, style = Stroke(width = stroke / inner))
                    if (progress > 0f) drawPath(path, color, alpha = progress)
                }
            }
        }
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(IconSize.row).alpha(progress.coerceIn(0f, 1f))
        )
    }
}

private val SIZE = 40.dp
private val STROKE = 2.5.dp
