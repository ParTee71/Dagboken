@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.dagboken.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import se.partee71.dagboken.R

/**
 * Kryssrad: en [ItemRow] med ett formmorfande kryss – en ring som fjädrar till en fylld
 * "kaka" med bock – och dämpad "klar"-stil när den är ikryssad (skill `ui-style`).
 * Hela raden är tryckytan; TalkBack läser den som en kryssruta. Med [onClick] är krysset en egen
 * knapp (läses med titel och undertext, t.ex. "Levaxin 50 µg, 9 tabletter") och resten av raden
 * öppnar detaljer – som en dos detaljer ([onClickLabel] läses upp). [accent] är en statusfärg i vänsterkanten (NFR-16).
 */
@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    accent: Color? = null,
) {
    val toggle = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
    ItemRowLayout(
        title = title,
        modifier = modifier,
        interaction = if (onClick == null) toggle else Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick),
        subtitle = subtitle,
        leading = {
            // Samma storlek i båda varianterna, så att titlarna linjerar; med onClick är rutan knappen.
            val label = listOfNotNull(title, subtitle).joinToString(", ")
            val button = if (onClick == null) Modifier else Modifier.clip(CircleShape).then(toggle).semantics { contentDescription = label }
            Box(Modifier.size(TOUCH).then(button), contentAlignment = Alignment.Center) { MorphingCheck(checked) }
        },
        trailing = trailing,
        tinted = checked,
        done = checked,
        accent = accent,
    )
}

@Composable
private fun MorphingCheck(checked: Boolean) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "kryss",
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
            modifier = Modifier.size(ICON).alpha(progress.coerceIn(0f, 1f)),
        )
    }
}

private val SIZE = 40.dp
private val TOUCH = 48.dp
private val ICON = 22.dp
private val STROKE = 2.5.dp
