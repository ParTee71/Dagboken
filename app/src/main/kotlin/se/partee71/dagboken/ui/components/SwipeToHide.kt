package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Arkivera/dölj med svep åt vänster: raden döljs direkt och [onHide] anropas; ramen visar
 * sedan `UndoSnackbar`. Permanent radering sker aldrig med svep.
 *
 * Samma åtgärd finns för TalkBack ("Arkivera"), eftersom svep inte går att göra med
 * skärmläsare: [content] får en modifier som raden ska använda, så att åtgärden hamnar på
 * den nod som TalkBack läser. Står raden kvar efter svepet (t.ex. när arkiverade visas)
 * glider den tillbaka. Med [enabled] = `false` (en redan arkiverad rad) visas raden utan svep.
 */
@Composable
fun SwipeToHide(
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.archive),
    enabled: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    if (!enabled) {
        content(Modifier)
        return
    }
    val hide by rememberUpdatedState(onHide)
    val state = rememberSwipeToDismissBoxState()
    val tone = AppColors.extended.primaryTone
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().clip(AppShapes.row).background(tone.container).padding(horizontal = Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_archive), contentDescription = null, tint = tone.content)
                Text(label, style = AppTypography.button, color = tone.content)
            }
        },
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                hide()
                // Försvinner raden avbryts detta med den; står den kvar glider den tillbaka.
                scope.launch {
                    delay(RESET_DELAY_MILLIS)
                    state.reset()
                }
            }
        },
    ) {
        // Ogenomskinlig, så att bakgrunden bara syns där raden svepts undan.
        Box(Modifier.clip(AppShapes.row).background(AppColors.extended.card)) {
            content(Modifier.semantics { customActions = listOf(CustomAccessibilityAction(label) { hide(); true }) })
        }
    }
}

/** Hur länge en svept rad som inte försvinner ligger undan innan den glider tillbaka. */
internal const val RESET_DELAY_MILLIS = 1_000L
