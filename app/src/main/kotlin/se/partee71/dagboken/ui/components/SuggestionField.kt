package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/** Ett förslag under ett [SuggestionField]: titel med den del som matchar ([highlight]) och undertext. */
data class Suggestion(val title: String, val subtitle: String? = null, val highlight: IntRange? = null)

/**
 * Textfält med förslag medan man skriver: ett [AppTextField] och, när [suggestions] inte
 * är tom, ett kort direkt under med en [ItemRow] per förslag – det som matchar är färgmarkerat –
 * och en valfri [footer] (varifrån förslagen kommer). Tryck på ett förslag anropar [onPick] med
 * dess index; vad som fylls i och när förslagen stängs bestämmer anroparen. Antalet förslag
 * läses upp av TalkBack när det ändras.
 */
@Composable
fun SuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<Suggestion>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    footer: String? = null,
    error: String? = null,
    helper: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AppTextField(value, onValueChange, label, error = error, helper = helper)
        if (suggestions.isNotEmpty()) {
            val count = pluralStringResource(R.plurals.suggestions_count, suggestions.size, suggestions.size)
            AppCard(
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = count
                },
            ) {
                suggestions.forEachIndexed { index, suggestion ->
                    ItemRow(
                        title = suggestion.title,
                        subtitle = suggestion.subtitle,
                        titleHighlight = suggestion.highlight,
                        onClick = { onPick(index) },
                    )
                }
                footer?.let {
                    Text(
                        it,
                        style = AppTypography.itemSubtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    )
                }
            }
        }
    }
}
