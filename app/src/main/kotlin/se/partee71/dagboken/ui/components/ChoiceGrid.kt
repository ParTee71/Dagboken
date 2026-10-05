package se.partee71.dagboken.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Ett rutnät av runda val där precis ett är valt – grunden för [EmojiPicker] och
 * [ColorSwatchPicker]. Valmarkeringen ägs här, så att den är densamma i båda: en ring i teal runt den
 * valda cellen, med luft mellan ringen och innehållet. TalkBack läser varje cell som ett alternativ
 * ([label]) i en grupp och säger vilket som är valt.
 */
@Composable
internal fun <T> ChoiceGrid(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    columns: Int = COLUMNS,
    cell: @Composable (item: T) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Så många per rad som får plats med full tryckyta, högst [columns].
        val perRow = (maxWidth / TOUCH_TARGET).toInt().coerceIn(1, columns)
        ChoiceRows(items.chunked(perRow), perRow, selected, onSelect, label, cell)
    }
}

@Composable
private fun <T> ChoiceRows(
    rows: List<List<T>>,
    perRow: Int,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    cell: @Composable (item: T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { item ->
                    val isSelected = item == selected
                    val description = label(item)
                    // Tryckytan är 48 dp (NFR-11); den synliga cirkeln är mindre.
                    Box(
                        Modifier.size(TOUCH_TARGET)
                            .clip(AppShapes.pill)
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(item) })
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.Center,
                    ) { SelectableCell(isSelected) { cell(item) } }
                }
                // En kortare sista rad följer rutnätet i stället för att spridas ut.
                repeat(perRow - row.size) { Spacer(Modifier.size(TOUCH_TARGET)) }
            }
        }
    }
}

/** Den synliga cellen; den valda har en ring i teal och luft mellan ringen och [content]. */
@Composable
private fun SelectableCell(selected: Boolean, content: @Composable () -> Unit) {
    val marker = if (selected) Modifier.border(RING, MaterialTheme.colorScheme.primary, AppShapes.pill).padding(RING_GAP) else Modifier
    Box(Modifier.size(CELL).then(marker), contentAlignment = Alignment.Center) { content() }
}

/** Som mest åtta per rad; på en smal telefon blir det färre, så att tryckytan räcker. */
private const val COLUMNS = 8

private val CELL = 40.dp
private val RING = 2.dp
private val RING_GAP = 4.dp
