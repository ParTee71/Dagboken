package se.partee71.dagboken.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import se.partee71.dagboken.ui.theme.AppTypography

/** Förvalda emojier – aktiviteter och vardag, sådant som går att känna igen i en lista. */
val DEFAULT_EMOJIS: List<String> = listOf(
    "🚶", "🏃", "🚴", "🏊", "🧘", "🏋️", "⚽", "🎾",
    "🧹", "🛒", "🍳", "🧺", "🌱", "🐕", "📚", "💻",
    "🎵", "🎨", "🧩", "🛁", "😴", "☕", "🍽️", "🚗",
    "🌞", "🌧️", "❄️", "🌙", "⭐", "❤️", "🌈", "🎉",
)

/** Välj en emoji ur [emojis]; den valda har [ChoiceGrid]s valmarkering. */
@Composable
fun EmojiPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, emojis: List<String> = DEFAULT_EMOJIS) {
    ChoiceGrid(emojis, selected, onSelect, label = { it }, modifier = modifier) { emoji -> Text(emoji, style = AppTypography.sectionTitle) }
}
