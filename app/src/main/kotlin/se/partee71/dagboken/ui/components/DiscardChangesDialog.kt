package se.partee71.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R

/**
 * "Släng ändringar?" (NFR-10) – den enda frågan när ett formulär med osparade ändringar ska stängas:
 * `EntityEditScreen` (bakåt, arkivera, återställ) och `AppBottomSheet(dirty = true)`. [onConfirm] slänger
 * och går vidare; [onDismiss] är "Fortsätt redigera".
 */
@Composable
internal fun DiscardChangesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.discard_title),
        message = stringResource(R.string.discard_message),
        confirmLabel = stringResource(R.string.discard_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.keep_editing),
    )
}
