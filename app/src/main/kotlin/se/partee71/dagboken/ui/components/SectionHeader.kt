package se.partee71.dagboken.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Appens enda sektionsrubrik utanför kort (regel 4) — samma textstil som
 * [DagbokenCard]s inbyggda `title`-slot. Valfri [color] ger en färgaccent
 * (t.ex. `colorScheme.primary`) för rubriker som ska sticka ut.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color      = color,
        modifier   = modifier,
    )
}
