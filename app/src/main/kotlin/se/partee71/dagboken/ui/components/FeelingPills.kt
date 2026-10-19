package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.ui.common.feelingsLabel
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.Tone

/**
 * En känsla som pill i kärnans ton ("Lugn › Tacksam", `feelingsLabel`) – en gång för känslostegets valda känslor
 * ([FeelingPicker], SCR-7), postkortet ([DagbokenEntryCard], HIST-10) och tillfällesraden ([OccasionRow], HEM-21).
 * [onClick], [trailingIcon] och [onClickLabel] som i [InfoPill].
 */
@Composable
internal fun FeelingPill(
    feeling: Feeling,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    @DrawableRes trailingIcon: Int? = null,
    onClickLabel: String? = null
) {
    InfoPill(
        feelingsLabel(feeling),
        modifier,
        colors = AppColors.extended.feelingTones.forCore(feeling.core),
        onClick = onClick,
        trailingIcon = trailingIcon,
        onClickLabel = onClickLabel
    )
}

/**
 * En posts känslor som pills för visning – de första [max] som [FeelingPill] i vald ordning, därefter en neutral pill
 * "+N" för resten (TalkBack: "N känslor till"). Läggs i anroparens [ChipRow], efter postens värden; tom lista ger inget.
 */
@Composable
fun FeelingPills(feelings: List<Feeling>, max: Int = FEELING_PILLS_MAX) {
    feelings.take(max).forEach { FeelingPill(it) }
    val rest = feelings.size - max
    if (rest > 0) {
        val description = pluralStringResource(R.plurals.feelings_more_description, rest, rest)
        InfoPill(
            stringResource(R.string.feelings_more_format, rest),
            Modifier.clearAndSetSemantics { contentDescription = description },
            tone = Tone.Neutral
        )
    }
}

/** Postkort och tillfällesrad visar högst tre känslor (HIST-10, HEM-21); resten som "+N". */
const val FEELING_PILLS_MAX = 3
