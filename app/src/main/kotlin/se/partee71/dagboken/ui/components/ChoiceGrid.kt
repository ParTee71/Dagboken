package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
 * [ColorSwatchPicker]. TalkBack läser varje cell som ett alternativ ([label]) i en grupp.
 */
@Composable
internal fun <T> ChoiceGrid(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    columns: Int = COLUMNS,
    cell: @Composable (item: T, selected: Boolean) -> Unit,
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
    cell: @Composable (item: T, selected: Boolean) -> Unit,
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
                    ) { Box(Modifier.size(CELL), contentAlignment = Alignment.Center) { cell(item, isSelected) } }
                }
                // En kortare sista rad följer rutnätet i stället för att spridas ut.
                repeat(perRow - row.size) { Spacer(Modifier.size(TOUCH_TARGET)) }
            }
        }
    }
}

/** Som mest åtta per rad; på en smal telefon blir det färre, så att tryckytan räcker. */
private const val COLUMNS = 8

private val CELL = 40.dp
