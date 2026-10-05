package se.partee71.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.durationText

/**
 * Tidsåtgång (AKT-7): en [QuantityStepper] i minuter (steg om [step]) och snabbval under, i en
 * [LabeledGroup] som visar tiden i timmar och minuter ("1 tim 30 min"). Ett snabbval som stämmer
 * med värdet är markerat.
 */
@Composable
fun DurationRow(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    quickPicks: List<Int> = DEFAULT_QUICK_PICKS,
    step: Int = DEFAULT_STEP,
    label: String = stringResource(R.string.duration),
) {
    LabeledGroup(label, modifier, helper = durationText(minutes)) {
        QuantityStepper(minutes, onMinutesChange, stringResource(R.string.minutes), range = 0..MAX_MINUTES, step = step)
        ChipRow {
            quickPicks.forEach { pick -> AppFilterChip(durationText(pick), selected = pick == minutes, onClick = { onMinutesChange(pick) }) }
        }
    }
}

private val DEFAULT_QUICK_PICKS = listOf(15, 30, 45, 60, 90, 120)
private const val DEFAULT_STEP = 5
private const val MAX_MINUTES = 24 * 60
