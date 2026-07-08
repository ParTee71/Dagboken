package se.partee71.dagboken.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R

/**
 * Appens enda bekräftelsedialog för radera-liknande åtgärder (regel 4).
 * Alla radera-bekräftelser ska använda denna i stället för en egen AlertDialog.
 * Informationsdialoger (utan en åtgärd att bekräfta) omfattas inte.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = stringResource(R.string.delete),
    dismissLabel: String = stringResource(R.string.cancel),
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(title) },
        text             = { Text(text) },
        confirmButton    = {
            TextButton(onClick = onConfirm) {
                Text(
                    text  = confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
    )
}
