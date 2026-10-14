package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.OccasionStatus
import se.partee71.dagboken.ui.common.scaleLevel
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/** Ett loggat värde som chip på tillfällesraden: "Energi 7" i skalans ton ([scaleLevel]). */
data class OccasionValue(val label: String, val value: Int, val higherIsBetter: Boolean = true)

/**
 * Tillfällesraden för mående (HEM-4, HEM-5): en rad per tillfälle – Efter frukost · Lunch · Kvällsmat ·
 * Läggdags, namnet ([title]) ges av anroparen – med bocken först ([MorphingCheck] i `LeadingSlot`, som en dos i
 * `CheckRow`): fylld när tillfället är loggat, tom ring annars. Bocken är bara visning; hela raden är tryckytan
 * ([onClick], NFR-17), och läget ([status], samma `OccasionStatus` som `:core` räknar fram i `occasionStates`)
 * står alltid som text, inte bara färg:
 * [OccasionStatus.LOGGED] avbockad stil, loggens klockslag som undertext och [values] som chips under namnet, i linje med
 * titeln (TalkBack läser "Loggad" som radens tillstånd); [OccasionStatus.LATE] "Försenat" i varningston,
 * [OccasionStatus.SOON] "Snart" i solgult och [OccasionStatus.NOT_LOGGED] (en tidigare dag) "Ej loggad" utan
 * varningston står under namnet, i linje med titeln (som en försenad dos), med "Logga nu" ([onLog]) till höger;
 * [OccasionStatus.UPCOMING] "18:00 · Kommande" i dämpad text. [time] är tillfällets klockslag eller loggens tid.
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
    val pending: Pair<Int, Tone>? = when (status) {
        OccasionStatus.LATE -> R.string.occasion_late to Tone.Warning
        OccasionStatus.NOT_LOGGED -> R.string.occasion_not_logged to Tone.Neutral
        OccasionStatus.SOON -> R.string.occasion_soon to Tone.Sun
        else -> null
    }
    val subtitle = when (status) {
        OccasionStatus.UPCOMING -> listOfNotNull(time, stringResource(R.string.occasion_upcoming)).joinToString(" · ")
        else -> time
    }
    val loggedState = stringResource(R.string.occasion_logged)
    ItemRow(
        title = title,
        modifier = if (logged) modifier.semantics { stateDescription = loggedState } else modifier,
        subtitle = subtitle,
        leading = { LeadingSlot { MorphingCheck(logged) } },
        trailing = pending?.let { { AppButton(stringResource(R.string.occasion_log_now), onLog, compact = true) } },
        onClick = onClick,
        done = logged,
        below = when {
            pending != null -> ({ InfoPill(stringResource(pending.first), tone = pending.second) })
            // Värdena under namnet, som statusen på en ologgad rad – till höger fick namnet inte plats på en rad.
            logged && values.isNotEmpty() -> {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        values.forEach { chip ->
                            InfoPill(
                                stringResource(R.string.occasion_value_format, chip.label, chip.value),
                                tone = scaleLevel(chip.value, higherIsBetter = chip.higherIsBetter).tone,
                            )
                        }
                    }
                }
            }
            else -> null
        },
        belowAlignsWithTitle = true,
    )
}
