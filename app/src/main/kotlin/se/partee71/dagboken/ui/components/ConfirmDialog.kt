package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Bekräftelse. Permanent radering bekräftas alltid här med [destructive] = true – aldrig med
 * svep (skill shared-ui-components). [onDecline] är nej-knappen när den betyder något annat än
 * att stänga (t.ex. "Behåll listan" – spara utan att räkna om); tryck utanför är alltid [onDismiss].
 * Med [dismissLabel] = `null` visas bara en knapp – för ett meddelande som bara ska läsas (anteckningen
 * bakom postkortets anteckningsikon, MED-12). [content] står under texten – ett fält som frågan behöver (slutdatumet när
 * en sjukdomsepisod avslutas, SJ-4); `null` (standard) = bara texten.
 *
 * **Bredden med [content]** skiljer sig från övriga dialoger: i stället för plattformens fönsterbredd
 * (`usePlatformDefaultWidth`) fyller dialogen skärmen minus `Spacing.xl` på var sida, högst `Spacing.dialogMaxWidth`
 * (M3:s tak). Skälet är Robolectric: ett textfält (`DateField`) i ett fönster med plattformens WRAP_CONTENT-bredd mäts
 * med flera bredder per layout, ändrar sin rullning vid varje mätning och begär ny layout – testernas väntan på vila
 * tar aldrig slut. Med en bredd som inte beror på fönstrets mätning blir det deterministiskt. Utan [content] är dialogen
 * som förut.
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
    dismissLabel: String? = stringResource(R.string.cancel),
    onDecline: () -> Unit = onDismiss,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppButton(confirmLabel, onConfirm, variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Primary)
        },
        modifier = if (content == null) modifier else modifier.padding(horizontal = Spacing.xl).widthIn(max = Spacing.dialogMaxWidth),
        dismissButton = dismissLabel?.let { { AppButton(it, onDecline, variant = ButtonVariant.Text) } },
        title = { Text(title, style = AppTypography.headline) },
        text = {
            val text = @Composable { Text(message, style = AppTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (content == null) {
                text()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    text()
                    content()
                }
            }
        },
        shape = AppShapes.card,
        containerColor = AppColors.extended.card,
        properties = if (content == null) DialogProperties() else DialogProperties(usePlatformDefaultWidth = false),
    )
}

/**
 * Permanent radering efter bekräftelse (NFR-15, HIST-5): [action]s titel och text och "Radera" i felfärg; "Radera"
 * stänger dialogen ([onDismiss]) och raderar. En gång för postkortet, redigeraskärmen och Idags doserad.
 */
@Composable
internal fun DeleteConfirmDialog(action: DeleteAction, onDismiss: () -> Unit) {
    ConfirmDialog(
        action.title,
        action.message,
        stringResource(R.string.delete),
        onConfirm = {
            onDismiss()
            action.onConfirm()
        },
        onDismiss = onDismiss,
        destructive = true,
    )
}
