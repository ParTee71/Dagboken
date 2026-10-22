package se.partee71.dagboken.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.rememberReducedMotion
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.RING_WIDTH

/**
 * Andningscirkeln (Stunden, NOT-20): en cirkel i teal som växer under "Andas in" och krymper under "Andas ut", i
 * 4-4-takt ([BREATH_MILLIS] per halva), med fasen skriven i mitten. Med [reducedMotion] (systemets "Ta bort
 * animationer", standard) står ingen cirkel kvar – bara texten, som byter i samma takt. [active] = `false` (pausen är
 * klar) stannar cirkeln i vila utan text. Bara visning: TalkBack läser fasen som text, utan att avbryta var fjärde
 * sekund (ingen live region).
 */
@Composable
fun BreathingCircle(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    reducedMotion: Boolean = rememberReducedMotion(),
    size: Dp = BREATHING_CIRCLE_SIZE
) {
    var inhale by remember { mutableStateOf(true) }
    val scale = remember { Animatable(REST_SCALE) }
    LaunchedEffect(active, reducedMotion) {
        if (!active) {
            scale.snapTo(REST_SCALE)
            return@LaunchedEffect
        }
        while (true) {
            for (breathIn in listOf(true, false)) {
                inhale = breathIn
                val target = if (breathIn) FULL_SCALE else REST_SCALE
                if (reducedMotion) {
                    delay(BREATH_MILLIS.toLong())
                } else {
                    scale.animateTo(target, tween(BREATH_MILLIS, easing = FastOutSlowInEasing))
                }
            }
        }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (!reducedMotion) {
            val ring = AppColors.extended.track
            val fill = MaterialTheme.colorScheme.primaryContainer
            val edge = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize()) {
                val outer = this.size.minDimension / 2
                drawCircle(ring, radius = outer - RING_WIDTH.toPx() / 2, style = Stroke(RING_WIDTH.toPx()))
                val radius = outer * scale.value
                drawCircle(fill, radius = radius)
                drawCircle(edge, radius = radius - EDGE.toPx() / 2, style = Stroke(EDGE.toPx()))
            }
        }
        if (active) {
            Text(
                stringResource(if (inhale) R.string.breathing_in else R.string.breathing_out),
                style = AppTypography.sectionTitle,
                color = if (reducedMotion) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** En halv andning – in eller ut – i millisekunder (4-4-takt). */
const val BREATH_MILLIS = 4_000

/** Cirkelns plats på skärmen. */
val BREATHING_CIRCLE_SIZE = 220.dp

private const val REST_SCALE = 0.55f
private const val FULL_SCALE = 0.96f
private val EDGE = 3.dp
