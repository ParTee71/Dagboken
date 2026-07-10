package se.partee71.dagboken.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R

/**
 * Fångar back-navigering (systemets tillbaka-knapp och skärmens onBack) när
 * formuläret har osparade ändringar, och visar en bekräftelsedialog med
 * möjlighet att spara innan man går vidare (regel 4 — enda platsen för detta
 * mönster, återanvänds av alla formulärskärmar).
 *
 * Returnerar en "guardad" onBack-lambda: koppla den till skärmens
 * [DagbokenScaffold]-onBack i stället för den råa navigations-onBack.
 */
@Composable
fun UnsavedChangesBackHandler(
    isDirty: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    canSave: Boolean = true,
): () -> Unit {
    var showDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isDirty) { showDialog = true }

    if (showDialog) {
        ConfirmDialog(
            title        = stringResource(R.string.unsaved_changes_title),
            text         = stringResource(R.string.unsaved_changes_body),
            confirmLabel = stringResource(R.string.discard),
            dismissLabel = stringResource(R.string.cancel),
            destructive  = true,
            neutralLabel = if (canSave) stringResource(R.string.save) else null,
            onNeutral    = if (canSave) { { showDialog = false; onSave() } } else null,
            onConfirm    = { showDialog = false; onDiscard() },
            onDismiss    = { showDialog = false },
        )
    }

    return { if (isDirty) showDialog = true else onDiscard() }
}
