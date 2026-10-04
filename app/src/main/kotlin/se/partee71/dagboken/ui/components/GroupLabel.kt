package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/** Liten etikett över en grupp kort, t.ex. "UTSEENDE" i Inställningar. Läses som rubrik. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AppTypography.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.s).semantics { heading() },
    )
}

/**
 * Ett formulärfält som inte är ett textfält (val, väljare, dosering) med sin [GroupLabel] ovanför
 * och valfri [helper] under, i samma stil som hjälptexten under `AppTextField`; [error] (från
 * formulärets validering) ersätter hjälptexten.
 */
@Composable
fun LabeledGroup(
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        GroupLabel(label)
        content()
        if (error != null) {
            FieldError(error, Modifier.padding(horizontal = Spacing.l))
        } else if (helper != null) {
            Text(helper, Modifier.padding(horizontal = Spacing.l), style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
