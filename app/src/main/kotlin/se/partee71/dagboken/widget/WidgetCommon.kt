package se.partee71.dagboken.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Delat mellan appens tre hemskärmswidgets (medicin, screening, vid behov, #161/#162):
 * fasta färger i stället för `GlanceTheme` (ett försök att använda
 * `androidx.glance.material3.GlanceTheme` gav "Unresolved reference" i CI, #156).
 */
val WidgetBackground = ColorProvider(Color(0xFF15151B))
val WidgetOnBackground = ColorProvider(Color(0xFFF2F2F5))
val WidgetButtonBackground = ColorProvider(Color(0xFF2A2A34))

/**
 * Glances base-modul saknar en egen "Button", så knappen byggs av en klickbar container.
 *
 * Klicket sitter på en [Box] och `clickable` appliceras **först** i modifier-kedjan. Tidigare
 * satt `clickable` mitt i kedjan på en `Text` (`background().clickable().padding()`), och då
 * reagerade knapparna inte på tryck alls i screening-/vid behov-widgeten medan
 * medicinwidgetens `CheckBox` (en riktig RemoteViews-compound-knapp) fungerade — enda
 * strukturella skillnaden mellan dem.
 */
@Composable
fun WidgetButton(text: String, action: Action, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .clickable(action)
            .background(WidgetButtonBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = TextStyle(color = WidgetOnBackground))
    }
}

/**
 * Diagnostik (tillfällig): räknar varje gång en `ActionCallback` faktiskt körs, oavsett om
 * dess kropp lyckas. Widgeten visar räknaren, så nästa build skiljer "trycket når aldrig
 * fram" från "trycket når fram men åtgärden misslyckas" — utan att behöva logcat på telefon.
 * Tas bort när screening-/vid behov-trycken är verifierade.
 */
val WIDGET_TAP_COUNT = intPreferencesKey("widget_tap_count")

suspend fun recordWidgetTap(context: Context, glanceId: GlanceId) {
    runCatching {
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[WIDGET_TAP_COUNT] ?: 0
            prefs.toMutablePreferences().apply { this[WIDGET_TAP_COUNT] = current + 1 }
        }
    }
}
