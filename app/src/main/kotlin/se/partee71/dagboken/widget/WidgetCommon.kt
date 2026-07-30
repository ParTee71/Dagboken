package se.partee71.dagboken.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.padding
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Delat mellan appens tre hemskärmswidgets (medicin, screening, vid behov, #161/#162):
 * fasta färger i stället för `GlanceTheme` (ett försök att använda
 * `androidx.glance.material3.GlanceTheme` gav "Unresolved reference" i CI, #156).
 */
val WidgetBackground = ColorProvider(Color(0xFF15151B))
val WidgetOnBackground = ColorProvider(Color(0xFFF2F2F5))

/**
 * Widgetens tryckbara "knapp", byggd på [CheckBox].
 *
 * Det ser inte ut som en knapp — det är en kryssruta med etikett — men det är den enda
 * tryckmekanism som **bevisligen** fungerar i appens release-bygge. Vi mätte det: en
 * tryckräknare som skrivs allra först i varje `ActionCallback` rörde sig aldrig för knappar
 * byggda på `GlanceModifier.clickable(...)` (varken på `Text` eller på en `Box`, varken före
 * eller efter keep-regler för `androidx.glance.**`), medan medicinchecklistans `CheckBox`
 * körde sin action direkt. `CheckBox`/`Switch` är riktiga RemoteViews-compound-buttons och
 * går en annan väg internt än `clickable`.
 *
 * `checked` är alltid `false`: kryssrutan används som en momentan avtryckare, inte som en
 * tillståndsvisning. Utseendet får vika för att flödena faktiskt går att använda; en
 * snyggare primitiv kan införas när den är verifierad på samma sätt.
 */
@Composable
fun WidgetButton(text: String, action: Action, modifier: GlanceModifier = GlanceModifier) {
    CheckBox(
        checked = false,
        onCheckedChange = action,
        text = text,
        style = TextStyle(color = WidgetOnBackground),
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/**
 * Diagnostik (tillfällig): räknar varje gång en `ActionCallback` faktiskt körs, oavsett om
 * dess kropp lyckas. Widgeten visar räknaren, så vi kan skilja "trycket når aldrig fram"
 * från "trycket når fram men åtgärden misslyckas" — utan att behöva logcat på telefon.
 * Tas bort när screening-/vid behov-trycken är verifierade.
 */
val WIDGET_TAP_COUNT = intPreferencesKey("widget_tap_count")

/** Muterar mottagaren direkt — se resonemanget i `ScreeningWidgetState`. */
fun MutablePreferences.incrementWidgetTapCount() {
    this[WIDGET_TAP_COUNT] = (this[WIDGET_TAP_COUNT] ?: 0) + 1
}

suspend fun recordWidgetTap(context: Context, glanceId: GlanceId) {
    runCatching {
        updateAppWidgetState(context, glanceId) { prefs -> prefs.incrementWidgetTapCount() }
    }
}
