package se.partee71.dagboken.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import se.partee71.dagboken.core.model.FeelingCatalog

// Känslohjulets geometri – delad av `FeelingWheel` (väljarens ingång) och `FeelingMiniWheel` (minihjul och
// värmekarta): sex sektorer i hjulets ordning medurs från klockan 12, mellanrummen och navet utstansade.

/**
 * Ritar sektorerna i [colors] (en per kärna, hjulets ordning) bakom innehållet och stansar ut mellanrummen ([gap]) och
 * navet ([hubFraction] av radien, 0 = inget nav) – så att hjulet ligger på vilken yta som helst.
 */
internal fun Modifier.feelingSectors(colors: List<Color>, gap: Dp, hubFraction: Float): Modifier = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawBehind {
        val radius = size.minDimension / 2
        val sweep = (FULL_TURN / colors.size).toFloat()
        colors.forEachIndexed { index, color -> drawArc(color = color, startAngle = START_ANGLE + sweep * index, sweepAngle = sweep, useCenter = true) }
        repeat(colors.size) { index ->
            val angle = Math.toRadians((sweep * index).toDouble())
            val end = center + Offset((sin(angle) * radius).toFloat(), (-cos(angle) * radius).toFloat())
            drawLine(Color.Black, center, end, strokeWidth = gap.toPx(), blendMode = BlendMode.Clear)
        }
        if (hubFraction > 0f) drawCircle(Color.Black, radius = radius * hubFraction, blendMode = BlendMode.Clear)
    }

/**
 * Vilken sektor (index i hjulets ordning) [position] ligger i, räknat medurs från klockan 12 – `null` i navet och
 * utanför hjulet.
 */
internal fun coreAt(position: Offset, radius: Float, hubFraction: Float, count: Int = FeelingCatalog.cores.size): Int? {
    val dx = position.x - radius
    val dy = position.y - radius
    val distance = hypot(dx, dy)
    if (distance > radius || distance < radius * hubFraction) return null
    val degrees = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + FULL_TURN) % FULL_TURN
    return (degrees / (FULL_TURN / count)).toInt().coerceIn(0, count - 1)
}

/** Var en text i sektorn står: mitt i sektorn [index] på [distance] från mitten, som förskjutning (x, y). */
internal fun sectorOffset(index: Int, distance: Dp, count: Int = FeelingCatalog.cores.size): Pair<Dp, Dp> {
    val angle = Math.toRadians(FULL_TURN / count * (index + 0.5))
    return distance * sin(angle).toFloat() to distance * -cos(angle).toFloat()
}

/** Hjulets största diameter (som i prototypen: radie 150, namnen på 100, navet 30) – väljaren och värmekartan. */
internal val WHEEL_MAX = 320.dp

/** Texten i en sektor står på så stor del av radien. */
internal const val LABEL_FRACTION = 2f / 3f

private const val FULL_TURN = 360.0

/** Canvas räknar vinklar medurs från klockan 3; hjulet börjar klockan 12. */
private const val START_ANGLE = -90f
