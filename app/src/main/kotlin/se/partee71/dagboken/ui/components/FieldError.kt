package se.partee71.dagboken.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import se.partee71.dagboken.ui.theme.AppTypography

/** Ett valideringsfel under ett fält som inte är ett textfält (t.ex. en grupp val), eller ett sparfel i en panel. */
@Composable
fun FieldError(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { error(text) }, style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.error)
}
