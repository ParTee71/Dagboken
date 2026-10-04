package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography

/** Förvalda emojier – aktiviteter och vardag, sådant som går att känna igen i en lista. */
val DEFAULT_EMOJIS: List<String> = listOf(
    "🚶", "🏃", "🚴", "🏊", "🧘", "🏋️", "⚽", "🎾",
    "🧹", "🛒", "🍳", "🧺", "🌱", "🐕", "📚", "💻",
    "🎵", "🎨", "🧩", "🛁", "😴", "☕", "🍽️", "🚗",
    "🌞", "🌧️", "❄️", "🌙", "⭐", "❤️", "🌈", "🎉",
)

/** Välj en emoji ur [emojis]; den valda har primärton och kant. */
@Composable
fun EmojiPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, emojis: List<String> = DEFAULT_EMOJIS) {
    ChoiceGrid(emojis, selected, onSelect, label = { it }, modifier = modifier) { emoji, isSelected ->
        val cell = if (isSelected) {
            Modifier.background(MaterialTheme.colorScheme.primaryContainer, AppShapes.pill)
                .border(SELECTED_BORDER, MaterialTheme.colorScheme.primary, AppShapes.pill)
        } else {
            Modifier
        }
        Box(cell.fillMaxSize(), contentAlignment = Alignment.Center) { Text(emoji, style = AppTypography.sectionTitle) }
    }
}

private val SELECTED_BORDER = 2.5.dp
