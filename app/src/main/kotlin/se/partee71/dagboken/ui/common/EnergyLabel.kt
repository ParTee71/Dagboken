package se.partee71.dagboken.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.Tone

/** Var på skalan ett värde ligger: lägsta, mittersta eller högsta delen. */
enum class ScaleZone { Low, Mid, High }

/**
 * Nivån för ett värde på en skala (AKT-4, AKT-5, AKT-6, SCR-1): [label] är texten ("Låg"/"Medel"/"Hög"
 * när högre är bättre, "Lätt"/"Måttlig"/"Svår" när högre är sämre – och "Ingen" för 0 på en sådan
 * skala som börjar på noll) och [tone] pillens ton – grön för
 * det bra, solgul i mitten, terrakotta för det dåliga. Färgen bär aldrig informationen ensam:
 * etiketten visas alltid bredvid (skill `accessibility-compose`).
 */
data class ScaleLevel(val zone: ScaleZone, val higherIsBetter: Boolean, @param:StringRes val label: Int, val tone: Tone)

/**
 * Den enda indelningen av en skala i nivåer, för båda riktningarna (regel 4). Gränserna ligger vid
 * 40 % och 70 % av skalan – på 0–10 betyder det 0–3, 4–6 och 7–10 som i 3.x, på −10…+10
 * (aktivitetens energi) −10…−3, −2…+3 och +4…+10.
 */
fun scaleLevel(value: Int, range: IntRange = 0..10, higherIsBetter: Boolean = true): ScaleLevel {
    val span = (range.last - range.first).coerceAtLeast(1)
    // I tiondelar av skalan, med heltal – inga avrundningsfel precis på gränsen.
    val tenths = (value.coerceIn(range) - range.first) * TENTHS
    val zone = when {
        tenths >= span * HIGH_FROM -> ScaleZone.High
        tenths >= span * MID_FROM -> ScaleZone.Mid
        else -> ScaleZone.Low
    }
    val good = if (higherIsBetter) zone else zone.mirrored()
    val tone = when (good) {
        ScaleZone.High -> Tone.Positive
        ScaleZone.Mid -> Tone.Sun
        ScaleZone.Low -> Tone.Warning
    }
    val label = if (higherIsBetter) {
        when (zone) {
            ScaleZone.Low -> R.string.level_low
            ScaleZone.Mid -> R.string.level_mid
            ScaleZone.High -> R.string.level_high
        }
    } else {
        when {
            value <= 0 && range.first == 0 -> R.string.severity_none
            zone == ScaleZone.Low -> R.string.severity_mild
            zone == ScaleZone.Mid -> R.string.severity_moderate
            else -> R.string.severity_severe
        }
    }
    return ScaleLevel(zone, higherIsBetter, label, tone)
}

/**
 * Nivåns färg ur energiskalan (`AppColors.extended.energy`, DSN-1) – för reglage, diagram och
 * postkortets vänsteraccent (NFR-16), aldrig för text.
 */
val ScaleLevel.color: Color
    @Composable @ReadOnlyComposable
    get() {
        val energy = AppColors.extended.energy
        return when (if (higherIsBetter) zone else zone.mirrored()) {
            ScaleZone.Low -> energy.low
            ScaleZone.Mid -> energy.mid
            ScaleZone.High -> energy.high
        }
    }

/** Värdet som text: med tecken när skalan går under noll ("+3", "−2", "0"). */
fun scaleValueText(value: Int, range: IntRange = 0..10): String = when {
    range.first >= 0 || value == 0 -> value.toString()
    value > 0 -> "+$value"
    else -> "−${-value}"
}

private fun ScaleZone.mirrored(): ScaleZone = when (this) {
    ScaleZone.Low -> ScaleZone.High
    ScaleZone.Mid -> ScaleZone.Mid
    ScaleZone.High -> ScaleZone.Low
}

private const val TENTHS = 10
private const val MID_FROM = 4
private const val HIGH_FROM = 7
