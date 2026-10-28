package se.partee71.dagboken.ui.common

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Om systemets "Ta bort animationer" är på (animationsskalan 0, skill ui-style → Rörelse): då visas rörelse som text
 * eller stillbild i stället för animation (`SunsetBreathing`). Läses när komponenten visas.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** En halv andning – in eller ut – i millisekunder (4-4-takt). */
const val BREATH_MILLIS = 4_000

/** Ett helt varv för Stundens kullar i sidled i millisekunder – mycket långsamt, ~90 s. */
const val HILL_DRIFT_MILLIS = 90_000

/** Tiden solen tar på sig att komma till vila när pausen är klar. */
const val BREATH_SETTLE_MILLIS = 1_500

/**
 * Andningens takt (Stunden, NOT-20): [inhale] är sant under "Andas in" och falskt under "Andas ut", [progress] går mjukt
 * från 0 (vila) till 1 (full) under inandningen och tillbaka under utandningen – [BREATH_MILLIS] per halva. Den enda
 * källan till takten; `SunsetBreathing` ritar efter den. Bara läsning – skapas av [rememberBreathPhase].
 */
@Stable
class BreathPhase internal constructor() {
    var inhale by mutableStateOf(true)
        private set
    private val animated = Animatable(0f)

    /** 0 = vila, 1 = full andning. */
    val progress: Float get() = animated.value

    internal suspend fun run(active: Boolean, reducedMotion: Boolean) {
        if (reducedMotion) animated.snapTo(0f)
        if (!active) {
            animated.animateTo(0f, tween(BREATH_SETTLE_MILLIS, easing = FastOutSlowInEasing))
            return
        }
        while (true) {
            for (breathIn in listOf(true, false)) {
                inhale = breathIn
                if (reducedMotion) {
                    delay(BREATH_MILLIS.toLong())
                } else {
                    animated.animateTo(if (breathIn) 1f else 0f, tween(BREATH_MILLIS, easing = FastOutSlowInEasing))
                }
            }
        }
    }
}

/**
 * Andningsfasen i 4-4-takt ([BREATH_MILLIS] per halva). Med [reducedMotion] står [BreathPhase.progress] still på 0 och
 * bara [BreathPhase.inhale] byter i takten. [active] = `false` (pausen är klar) för fasen mjukt till vila.
 */
@Composable
fun rememberBreathPhase(active: Boolean, reducedMotion: Boolean = rememberReducedMotion()): BreathPhase {
    val phase = remember { BreathPhase() }
    LaunchedEffect(active, reducedMotion) { phase.run(active, reducedMotion) }
    return phase
}
