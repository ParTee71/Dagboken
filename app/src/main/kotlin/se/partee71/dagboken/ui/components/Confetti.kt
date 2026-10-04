package se.partee71.dagboken.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random
import se.partee71.dagboken.ui.theme.AppColors

/**
 * Kort konfettiregn när dagen är klar (belöningsläget, DSN-4). Dekorativt och blockerar ingenting; ritas med
 * `Canvas`, utan bibliotek. Samma [seed] ger samma bitar (deterministiska skärmdumpar).
 * [onFinished] anropas när regnet är över – så att det spelas en gång, inte vid nästa visning.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier, play: Boolean = true, seed: Int = 7, onFinished: () -> Unit = {}) {
    val progress = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(play) {
        if (play) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
            finished()
        }
    }
    val pieces = remember(seed) { pieces(seed) }
    Canvas(modifier.clearAndSetSemantics {}) {
        val t = progress.value
        if (!play || t <= 0f || t >= 1f) return@Canvas
        pieces.forEach { piece ->
            val y = (piece.startY + t * piece.speed) * size.height
            val x = (piece.x + sin((t * piece.sway + piece.phase) * 6.28f) * 0.03f) * size.width
            val alpha = if (t > 0.8f) (1f - t) / 0.2f else 1f
            rotate(piece.spin * t * 360f, Offset(x, y)) {
                if (piece.round) {
                    drawCircle(piece.color, radius = piece.size.toPx() / 2, center = Offset(x, y), alpha = alpha)
                } else {
                    val w = piece.size.toPx() * 0.6f
                    val h = piece.size.toPx()
                    drawRoundRect(piece.color, Offset(x - w / 2, y - h / 2), Size(w, h), CornerRadius(w / 3), alpha = alpha)
                }
            }
        }
    }
}

private class Piece(
    val x: Float,
    val startY: Float,
    val speed: Float,
    val sway: Float,
    val phase: Float,
    val spin: Float,
    val round: Boolean,
    val color: Color,
    val size: Dp,
)

private fun pieces(seed: Int): List<Piece> {
    val random = Random(seed)
    return List(PIECES) { i ->
        Piece(
            x = random.nextFloat(),
            startY = -0.2f + random.nextFloat() * 0.3f,
            speed = 0.8f + random.nextFloat() * 0.6f,
            sway = 1f + random.nextFloat() * 2f,
            phase = random.nextFloat(),
            spin = random.nextFloat() * 2f - 1f,
            round = i % 3 == 0,
            color = AppColors.swatch(i),
            size = (8f + random.nextFloat() * 8f).dp,
        )
    }
}

private const val PIECES = 48
private const val DURATION_MS = 1_800
