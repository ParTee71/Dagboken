package se.partee71.dagboken.ui.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Om systemets "Ta bort animationer" är på (animationsskalan 0, skill ui-style → Rörelse): då visas rörelse som text
 * eller stillbild i stället för animation (`BreathingCircle`). Läses när komponenten visas.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
