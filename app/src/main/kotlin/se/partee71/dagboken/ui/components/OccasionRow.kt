package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.OccasionStatus
import se.partee71.dagboken.ui.common.scaleLevel
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/** Ett loggat värde som chip på tillfällesraden: "Energi 7" i skalans ton ([scaleLevel]). */
data class OccasionValue(val label: String, val value: Int, val higherIsBetter: Boolean = true)

/**
 * Tillfällesraden för mående (HEM-4, HEM-5): en rad per tillfälle – Efter frukost · Lunch · Kvällsmat ·
 * Läggdags, namnet ([title]) ges av anroparen – med läget ([status], samma `OccasionStatus` som `:core`
 * räknar fram i `occasionStates`) alltid som text, inte bara färg:
 * [OccasionStatus.LOGGED] avbockad stil (som en tagen dos), "Loggad" och [values] som chips, [OccasionStatus.LATE] "Försenat" i
 * varningston och [OccasionStatus.SOON] "Snart" i solgult, båda med "Logga nu" ([onLog]), och
 * [OccasionStatus.UPCOMING] "Kommande" i dämpad text. [OccasionStatus.NOT_LOGGED] (en tidigare dag) visar
 * "Ej loggad" utan varningston, också med "Logga nu" (HEM-4). [time] är tillfällets klockslag eller
 * loggens tid; [onClick] öppnar en loggad post.
 */
@Composable
fun OccasionRow(
    title: String,
    status: OccasionStatus,
    onLog: () -> Unit,
    modifier: Modifier = Modifier,
    time: String? = null,
    values: List<OccasionValue> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    val logged = status == OccasionStatus.LOGGED
    val action: Pair<Int, Tone>? = when (status) {
        OccasionStatus.LATE -> R.string.occasion_late to Tone.Warning
        OccasionStatus.NOT_LOGGED -> R.string.occasion_not_logged to Tone.Neutral
        OccasionStatus.SOON -> R.string.occasion_soon to Tone.Sun
        else -> null
    }
    val subtitle = when (status) {
        OccasionStatus.LOGGED -> null
        OccasionStatus.UPCOMING -> listOfNotNull(stringResource(R.string.occasion_upcoming), time).joinToString(" · ")
        else -> time
    }
    ItemRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        trailing = when {
            action != null -> {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        InfoPill(stringResource(action.first), tone = action.second)
                        AppButton(stringResource(R.string.occasion_log_now), onLog, compact = true)
                    }
                }
            }
            logged -> {
                { InfoPill(listOfNotNull(stringResource(R.string.occasion_logged), time).joinToString(" "), tone = Tone.Neutral, icon = R.drawable.ic_check) }
            }
            else -> null
        },
        onClick = onClick,
        done = logged,
        below = values.takeIf { logged && it.isNotEmpty() }?.let { chips ->
            {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    chips.forEach { chip ->
                        InfoPill(
                            stringResource(R.string.occasion_value_format, chip.label, chip.value),
                            tone = scaleLevel(chip.value, higherIsBetter = chip.higherIsBetter).tone,
                        )
                    }
                }
            }
        },
    )
}
