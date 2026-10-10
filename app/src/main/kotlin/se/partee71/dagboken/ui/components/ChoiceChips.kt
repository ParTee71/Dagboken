package se.partee71.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Ett val bland några få: en [ChipRow] med en [AppFilterChip] per alternativ, det valda markerat
 * (form, enhet, kategori). [label] ger alternativets text. Med [onClear] avmarkerar ett tryck på det
 * valda alternativet valet (anropar [onClear] i stället för [onSelect]) – för val som får vara ej angivna.
 */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    ChipRow(modifier) {
        options.forEach { option ->
            val isSelected = option == selected
            AppFilterChip(label(option), selected = isSelected, onClick = { if (isSelected && onClear != null) onClear() else onSelect(option) })
        }
    }
}
