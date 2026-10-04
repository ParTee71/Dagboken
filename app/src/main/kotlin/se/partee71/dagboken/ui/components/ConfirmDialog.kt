package se.partee71.dagboken.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography

/**
 * Bekräftelse. Permanent radering bekräftas alltid här med [destructive] = true – aldrig med
 * svep (skill shared-ui-components). [onDecline] är nej-knappen när den betyder något annat än
 * att stänga (t.ex. "Behåll listan" – spara utan att räkna om); tryck utanför är alltid [onDismiss].
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    dismissLabel: String = stringResource(R.string.cancel),
    onDecline: () -> Unit = onDismiss,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppButton(confirmLabel, onConfirm, variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Primary)
        },
        modifier = modifier,
        dismissButton = { AppButton(dismissLabel, onDecline, variant = ButtonVariant.Text) },
        title = { Text(title, style = AppTypography.headline) },
        text = { Text(message, style = AppTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        shape = AppShapes.card,
        containerColor = AppColors.extended.card,
    )
}
