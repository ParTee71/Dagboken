package se.partee71.dagboken.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import se.partee71.dagboken.R

// For the -10..10 activity energy scale
fun energyColor(energy: Int, scheme: ColorScheme): Color = when {
    energy >= 5  -> scheme.tertiary
    energy >= 1  -> scheme.secondary
    energy >= -1 -> scheme.onSurfaceVariant
    else         -> scheme.error
}

// For the 0..10 screening scale
fun screeningEnergyColor(value: Int, scheme: ColorScheme): Color = when {
    value >= 7 -> scheme.tertiary
    value >= 4 -> scheme.secondary
    else       -> scheme.error
}

fun gradientColors(): List<Color> = listOf(Rose500, Amber400, Emerald400)

/**
 * Textetikett för samma nivåindelning som [screeningEnergyColor] färgsätter. Färgen
 * ensam bär annars hela informationen, vilket inte fungerar för färgblinda — etiketten
 * ger samma nivå i text (a11y).
 */
@StringRes
fun screeningEnergyLabelRes(value: Int): Int = when {
    value >= 7 -> R.string.energy_level_high
    value >= 4 -> R.string.energy_level_medium
    else       -> R.string.energy_level_low
}

fun energyLabel(energy: Int): String = if (energy > 0) "+$energy" else "$energy"
