package se.partee71.dagboken.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.HILL_DRIFT_MILLIS
import se.partee71.dagboken.ui.common.rememberBreathPhase
import se.partee71.dagboken.ui.common.rememberReducedMotion
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography

/**
 * Stundens kvällslandskap (NOT-20): en himmel i vertikal toning, en sol som andas i 4-4-takt ([rememberBreathPhase]) –
 * den stiger och växer under "Andas in", sjunker och krymper under "Andas ut", med dis omkring som följer – och två
 * lager kullar som glider mycket långsamt i sidled ([HILL_DRIFT_MILLIS] per varv, sömlöst) och täcker nedre delen, så
 * att text och knappar ovanpå ligger på mörk grund. Fasen står i solen; solen växer vid behov så att texten alltid ryms
 * i den. [hillsLevel] är andelen av höjden där innehållet ovanpå börjar (läses vid ritning): ligger det högre än
 * kullarnas jämna nivå ([HILLS_FLOOR]) lyfts kullarna så att allt innehåll hamnar på mörk grund. Färgerna är
 * [AppColors.moment], oberoende av ljust och mörkt tema. Fyller sin plats ([modifier]). Med [reducedMotion] (systemets
 * "Ta bort animationer", standard) står himlen, solen och kullarna stilla; bara texten byter i takten. [active] =
 * `false` (pausen är klar) för solen mjukt till vila, tar bort texten och bromsar kullarna mjukt till stillastående.
 * Landskapet är dekor – TalkBack läser bara fasen som text, utan live region.
 */
@Composable
fun SunsetBreathing(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    reducedMotion: Boolean = rememberReducedMotion(),
    hillsLevel: () -> Float = { HILLS_FLOOR }
) {
    val phase = rememberBreathPhase(active, reducedMotion)
    val drift = rememberHillDrift(active, reducedMotion)
    val colors = AppColors.moment
    val showsText by rememberUpdatedState(active)
    var textSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val sky = Brush.verticalGradient(colors.sky, startY = 0f, endY = size.height * SKY_END)
                val back = Path()
                val front = Path()
                onDrawBehind {
                    drawRect(sky)
                    drawSun(phase.progress, if (showsText) textRadius(textSize.width.toFloat(), textSize.height.toFloat()) else 0f, colors.sunCore, colors.sunMid, colors.sunEdge)
                    val lift = hillsLift(hillsLevel())
                    drawHills(back, HillLayer.Back, drift.value, lift, colors.hillBack)
                    drawHills(front, HillLayer.Front, drift.value, lift, colors.hillFront)
                }
            }
    ) {
        if (active) {
            Text(
                stringResource(if (phase.inhale) R.string.breathing_in else R.string.breathing_out),
                Modifier
                    .layout { measurable, constraints ->
                        val maxWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth * TEXT_WIDTH).toInt() else Constraints.Infinity
                        val placeable = measurable.measure(Constraints(maxWidth = maxWidth))
                        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
                        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
                        val center = sunGeometry(Size(width.toFloat(), height.toFloat()), phase.progress, textRadius(placeable.width.toFloat(), placeable.height.toFloat())).center
                        layout(width, height) {
                            placeable.place((center.x - placeable.width / 2f).roundToInt(), (center.y - placeable.height / 2f).roundToInt())
                        }
                    }
                    .onSizeChanged { textSize = it },
                style = AppTypography.sectionTitle,
                color = colors.onSun,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Kullarnas förskjutning, 0 till 1 per varv: går jämnt medan [active], bromsar mjukt till stillastående när pausen är klar och står stilla vid [reducedMotion]. */
@Composable
private fun rememberHillDrift(active: Boolean, reducedMotion: Boolean): State<Float> {
    val drift = remember { Animatable(0f) }
    LaunchedEffect(active, reducedMotion) {
        when {
            reducedMotion -> drift.snapTo(0f)

            active -> while (true) {
                drift.snapTo(drift.value % 1f)
                drift.animateTo(drift.value + 1f, tween(HILL_DRIFT_MILLIS, easing = LinearEasing))
            }

            else -> drift.animateDecay(drift.velocity, exponentialDecay(DRIFT_FRICTION))
        }
    }
    return drift.asState()
}

/** Solens mitt och radie i en yta av [size] vid andningens [progress] (0 vila, 1 full); radien är aldrig under [minRadius]. */
internal data class SunGeometry(val center: Offset, val radius: Float)

internal fun sunGeometry(size: Size, progress: Float, minRadius: Float = 0f): SunGeometry {
    val full = min(size.width * SUN_WIDTH_FRACTION, size.height * SUN_HEIGHT_FRACTION)
    return SunGeometry(
        center = Offset(size.width / 2f, size.height * (SUN_REST_Y - SUN_RISE * progress)),
        radius = max(full * lerp(SUN_REST_SCALE, 1f, progress), minRadius)
    )
}

/** Solens minsta radie för att rymma en text av [width] × [height]. */
internal fun textRadius(width: Float, height: Float): Float = hypot(width, height) / 2f * TEXT_ROOM

/** Disets radie runt en sol. */
internal fun hazeRadius(sun: SunGeometry, progress: Float): Float = sun.radius * lerp(HAZE_REST, HAZE_FULL, progress)

/** Disets täckning [ratio] (0 i mitten, 1 vid kanten) av dess radie ut från solen: mjukt avtagande, så att det inte når texterna. */
internal fun hazeAlpha(progress: Float, ratio: Float): Float = lerp(HAZE_ALPHA_REST, HAZE_ALPHA_FULL, progress) * (1f - ratio.coerceIn(0f, 1f)).pow(HAZE_FALLOFF)

/** Himlens färg vid [y] (andel av höjden), som toningen ritas. */
internal fun skyColorAt(y: Float, sky: List<Color>): Color {
    val position = (y / SKY_END).coerceIn(0f, 1f) * (sky.size - 1)
    val index = min(position.toInt(), sky.size - 2)
    return androidx.compose.ui.graphics.lerp(sky[index], sky[index + 1], position - index)
}

/** Hur mycket kullarna lyfts (andel av höjden, aldrig nedåt) för att täcka allt från [level] och nedåt. */
internal fun hillsLift(level: Float): Float = if (level.isNaN()) 0f else min(0f, level - HILLS_FLOOR)

/** Ett kulllager: kammens höjd som andel av ytans höjd vid sidled [x] (0 till 1) och förskjutningen [drift] (0 till 1 per varv). */
internal enum class HillLayer(
    val base: Float,
    val first: Float,
    val second: Float,
    val firstHarmonic: Int,
    val secondHarmonic: Int,
    val direction: Int
) {
    Back(0.40f, 0.02f, 0.01f, 1, 2, 1),
    Front(0.50f, 0.012f, 0.008f, 2, 3, -1);

    /** Heltalsövertoner gör att kurvan är periodisk med ett varv, så att [drift] 0 och 1 ger samma bild (sömlöst). */
    fun crest(x: Float, drift: Float, lift: Float = 0f): Float {
        val t = x + direction * drift
        return max(0f, base + lift + first * sin(TWO_PI * firstHarmonic * t) + second * sin(TWO_PI * (secondHarmonic * t + SECOND_SHIFT)))
    }

    /** Högsta punkten (minsta y-andel) någonstans på kammen. */
    val topMost: Float get() = base - first - second

    /** Lägsta punkten (största y-andel) någonstans på kammen. */
    val bottomMost: Float get() = base + first + second
}

private fun DrawScope.drawSun(progress: Float, minRadius: Float, core: Color, mid: Color, edge: Color) {
    val sun = sunGeometry(size, progress, minRadius)
    val haze = hazeRadius(sun, progress)
    drawCircle(
        Brush.radialGradient(
            colorStops = Array(HAZE_STOPS + 1) { i -> (i / HAZE_STOPS.toFloat()).let { it to mid.copy(alpha = hazeAlpha(progress, it)) } },
            center = sun.center,
            radius = haze
        ),
        radius = haze,
        center = sun.center
    )
    drawCircle(
        Brush.radialGradient(0f to core, SUN_MID_STOP to mid, 1f to edge, center = sun.center, radius = sun.radius),
        radius = sun.radius,
        center = sun.center
    )
}

private fun DrawScope.drawHills(path: Path, layer: HillLayer, drift: Float, lift: Float, color: Color) {
    path.rewind()
    path.moveTo(0f, size.height)
    for (step in 0..HILL_STEPS) {
        val x = step / HILL_STEPS.toFloat()
        path.lineTo(x * size.width, layer.crest(x, drift, lift) * size.height)
    }
    path.lineTo(size.width, size.height)
    path.close()
    drawPath(path, color)
}

/** Den högsta punkten kullarna når som andel av höjden – solen ryms ovanför den. */
internal val HILLS_TOP: Float = HillLayer.entries.minOf { it.topMost }

/** Andelen av höjden under vilken något kullager alltid täcker, oavsett glidning – där ligger texterna på mörk grund. */
internal val HILLS_FLOOR: Float = HillLayer.entries.minOf { it.bottomMost }

private const val TWO_PI = (2 * PI).toFloat()
private const val SECOND_SHIFT = 0.25f
private const val HILL_STEPS = 96
private const val SKY_END = 0.42f
private const val SUN_REST_Y = 0.28f
private const val SUN_RISE = 0.035f
private const val SUN_WIDTH_FRACTION = 0.22f
private const val SUN_HEIGHT_FRACTION = 0.09f
private const val SUN_REST_SCALE = 0.62f
private const val SUN_MID_STOP = 0.55f
private const val HAZE_REST = 1.6f
private const val HAZE_FULL = 1.9f
private const val HAZE_ALPHA_REST = 0.30f
private const val HAZE_ALPHA_FULL = 0.50f
private const val HAZE_FALLOFF = 3f
private const val HAZE_STOPS = 6
private const val TEXT_WIDTH = 0.8f
private const val TEXT_ROOM = 1.1f
private const val DRIFT_FRICTION = 1.2f
