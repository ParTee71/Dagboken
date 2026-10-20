package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.FeelingStrength
import se.partee71.dagboken.ui.common.feelingsLabel
import se.partee71.dagboken.ui.common.strengthIcon
import se.partee71.dagboken.ui.common.strengthName
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Neutral
import se.partee71.ui.components.ChipRow
import se.partee71.ui.components.InfoPill
import se.partee71.ui.theme.Spacing
import se.partee71.ui.theme.Tone

/**
 * En känsla som pill i kärnans ton ("Lugn › Tacksam", `feelingsLabel`) – en gång för känslostegets valda känslor
 * ([FeelingPicker], SCR-7), postkortet ([DagbokenEntryCard], HIST-10) och tillfällesraden ([OccasionRow], HEM-21).
 * Med [strength] (SCR-9) fylls pillen med styrkans intensitet och har styrkans prickar först, och TalkBack läser
 * styrkan efter namnet ("stark"). [onClick], [trailingIcon] och [onClickLabel] som i [InfoPill].
 */
@Composable
internal fun FeelingPill(
    feeling: Feeling,
    modifier: Modifier = Modifier,
    strength: FeelingStrength? = null,
    onClick: (() -> Unit)? = null,
    @DrawableRes trailingIcon: Int? = null,
    onClickLabel: String? = null
) {
    val state = strength?.let { stringResource(strengthName(it)) }
    InfoPill(
        feelingsLabel(feeling),
        modifier.semantics { state?.let { stateDescription = it } },
        icon = strength?.let(::strengthIcon),
        colors = AppColors.extended.feelingTones.forCore(feeling.core, strength),
        onClick = onClick,
        trailingIcon = trailingIcon,
        onClickLabel = onClickLabel
    )
}

/**
 * Känslor som pills för visning – de första [max] som [FeelingPill] i given ordning, därefter en neutral pill "+N" för
 * resten (TalkBack: "N känslor till"); [max] `null` visar alla (Idags Dagens känslor, Trenders valda dag). Läggs i
 * anroparens [ChipRow], efter postens värden; tom lista ger inget. Styrkan (SCR-9) ur [extras].
 */
@Composable
fun FeelingPills(feelings: List<Feeling>, max: Int? = FEELING_PILLS_MAX, extras: FeelingExtras = FeelingExtras.None) {
    val shown = max?.let(feelings::take) ?: feelings
    shown.forEach { FeelingPill(it, strength = extras.strengthOf(it)) }
    MorePill(feelings.size - shown.size)
}

/**
 * Postkortets känslor (HIST-10, SCR-10): som [FeelingPills] i en [ChipRow]; har någon av de visade känslorna en
 * kommentar ([FeelingExtras.noteOf]) står i stället varje känsla på en egen rad med sin kommentar under.
 */
@Composable
internal fun FeelingPillsWithNotes(feelings: List<Feeling>, extras: FeelingExtras) {
    val shown = feelings.take(FEELING_PILLS_MAX)
    if (shown.all { extras.noteOf(it).isBlank() }) {
        ChipRow { FeelingPills(feelings, extras = extras) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        shown.forEach { feeling ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FeelingPill(feeling, strength = extras.strengthOf(feeling))
                extras.noteOf(feeling).takeIf { it.isNotBlank() }?.let { note ->
                    Text(note.trim(), style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        MorePill(feelings.size - shown.size)
    }
}

/** "+N" för känslor som inte visas (TalkBack: "N känslor till"); 0 ger inget. */
@Composable
private fun MorePill(rest: Int) {
    if (rest <= 0) return
    val description = pluralStringResource(R.plurals.feelings_more_description, rest, rest)
    InfoPill(
        stringResource(R.string.feelings_more_format, rest),
        Modifier.clearAndSetSemantics { contentDescription = description },
        tone = Tone.Neutral
    )
}

/** Styrkans prickar (SCR-9) bredvid ett ord i [color]; ingen styrka ger inget. Läses inte – ordets beskrivning har styrkan. */
@Composable
internal fun StrengthDots(strength: FeelingStrength?, color: Color) {
    strength ?: return
    Icon(painterResource(strengthIcon(strength)), null, Modifier.size(STRENGTH_DOTS), tint = color)
}

private val STRENGTH_DOTS = 18.dp

/** Postkort och tillfällesrad visar högst tre känslor (HIST-10, HEM-21); resten som "+N". */
const val FEELING_PILLS_MAX = 3
