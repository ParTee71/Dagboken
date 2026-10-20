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
    onClear: (() -> Unit)? = null
) {
    Chips(options, { it == selected }, { option, isSelected -> if (isSelected && onClear != null) onClear() else onSelect(option) }, label, modifier)
}

/**
 * Flera val bland några få (kroppen och sammanhanget i måendet, SCR-9): samma rad som envalsvarianten, varje alternativ i
 * [selected] markerat. Ett tryck anropar [onToggle] med alternativet – vad som då väljs eller avmarkeras (t.ex. ett
 * alternativ som utesluter de andra) avgör anroparen.
 */
@Composable
fun <T> MultiChoiceChips(
    options: List<T>,
    selected: Collection<T>,
    onToggle: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    Chips(options, { it in selected }, { option, _ -> onToggle(option) }, label, modifier)
}

/** Raden för [ChoiceChips] och [MultiChoiceChips]: en [AppFilterChip] per alternativ, markerat när [isSelected]. */
@Composable
private fun <T> Chips(
    options: List<T>,
    isSelected: (T) -> Boolean,
    onClick: (option: T, selected: Boolean) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    ChipRow(modifier) {
        options.forEach { option ->
            val selected = isSelected(option)
            AppFilterChip(label(option), selected = selected, onClick = { onClick(option, selected) })
        }
    }
}
