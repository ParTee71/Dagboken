package se.partee71.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Ett val bland några få: en [ChipRow] med en [AppFilterChip] per alternativ, det valda markerat
 * (form, enhet, kategori). [label] ger alternativets text.
 */
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String, modifier: Modifier = Modifier) {
    ChipRow(modifier) {
        options.forEach { option -> AppFilterChip(label(option), selected = option == selected, onClick = { onSelect(option) }) }
    }
}
