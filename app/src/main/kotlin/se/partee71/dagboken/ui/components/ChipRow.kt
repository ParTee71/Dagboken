package se.partee71.dagboken.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import se.partee71.dagboken.ui.theme.Spacing

/**
 * En rad av [AppFilterChip] eller [PersonChip] med samma avstånd överallt. [wrap] bryter
 * raden (formulär); annars rullar den i sidled (filter överst i en lista).
 */
@Composable
fun ChipRow(modifier: Modifier = Modifier, wrap: Boolean = true, content: @Composable () -> Unit) {
    // Flyttbart, så att chipsens tillstånd följer med om [wrap] byts.
    val chips = remember(content) { movableContentOf(content) }
    if (wrap) {
        FlowRow(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) { chips() }
    } else {
        Row(
            modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) { chips() }
    }
}
