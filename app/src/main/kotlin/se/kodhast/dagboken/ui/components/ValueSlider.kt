package se.kodhast.dagboken.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import se.kodhast.dagboken.R
import se.kodhast.dagboken.ui.common.color
import se.kodhast.dagboken.ui.common.scaleLevel
import se.kodhast.dagboken.ui.common.scaleValueText
import se.kodhast.dagboken.ui.theme.AppColors
import se.kodhast.ui.components.AppSlider
import se.kodhast.ui.components.InfoPill

/**
 * Reglaget med energiskalan (AKT-4, AKT-5, AKT-6, SCR-1): Dagbokens skala på android-ui:s `AppSlider` – bara skalans
 * färger och nivåtexter är appens. Etikett, värdet som text och nivån som tonad pill ([scaleLevel]) ovanför ett färgat
 * spår ur energiskalan. [higherIsBetter] = `true` (energi, sömnkvalitet) går från rött till grönt åt höger; `false`
 * (stress, symptom, smärta) från grönt till rött. Går skalan under noll (aktivitetens energi −10…+10) markeras nollan
 * mitt på spåret och den fyllda delen utgår från den. Färgen bär aldrig informationen ensam – text och pill visar samma
 * sak, och TalkBack läser etiketten, värdet och nivån ("Energi, 7, Hög").
 *
 * [step] låter reglaget bara stanna på var [step]:e värde från `valueRange.first` (standard varje heltal). En längd
 * eller ett antal som inte är en skala får [valueText]: värdet skrivs då med den texten ("2 min 15 s"), utan nivåpill,
 * och spåret är temats primärfärg i stället för energiskalan – TalkBack läser etiketten och texten (Stundens längd,
 * NOT-20). Utan [valueText] och [step] ser reglaget ut som förut.
 */
@Composable
fun ValueSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 0..10,
    higherIsBetter: Boolean = true,
    enabled: Boolean = true,
    step: Int = 1,
    valueText: String? = null
) {
    val level = scaleLevel(value, valueRange, higherIsBetter)
    val levelText = stringResource(level.label)
    val shown = valueText ?: scaleValueText(value, valueRange)
    val energy = AppColors.extended.energy
    val primary = MaterialTheme.colorScheme.primary
    val gradient = when {
        valueText != null -> listOf(primary, primary)
        higherIsBetter -> listOf(energy.low, energy.mid, energy.high)
        else -> listOf(energy.high, energy.mid, energy.low)
    }
    AppSlider(
        label = label,
        value = value,
        onValueChange = onValueChange,
        valueText = shown,
        modifier = modifier,
        valueRange = valueRange,
        step = step,
        enabled = enabled,
        stateDescription = valueText ?: stringResource(R.string.scale_value_format, shown, levelText),
        trackBrush = Brush.horizontalGradient(gradient),
        thumbColor = if (valueText != null) primary else level.color,
        badge = if (valueText == null) ({ InfoPill(levelText, tone = level.tone) }) else null
    )
}
