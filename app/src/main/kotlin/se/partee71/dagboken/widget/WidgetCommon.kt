package se.partee71.dagboken.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Delat mellan appens tre hemskärmswidgets (medicin, screening, vid behov, #161/#162):
 * fasta färger i stället för `GlanceTheme` (ett försök att använda
 * `androidx.glance.material3.GlanceTheme` gav "Unresolved reference" i CI, #156) och den
 * enda "knappen" Glances base-modul har — en klickbar [Text].
 */
val WidgetBackground = ColorProvider(Color(0xFF15151B))
val WidgetOnBackground = ColorProvider(Color(0xFFF2F2F5))
val WidgetButtonBackground = ColorProvider(Color(0xFF2A2A34))

@Composable
fun WidgetButton(text: String, action: Action, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text = text,
        style = TextStyle(color = WidgetOnBackground),
        modifier = modifier
            .background(WidgetButtonBackground)
            .clickable(action)
            .padding(8.dp),
    )
}
