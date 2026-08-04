package se.partee71.dagboken.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.appwidget.CheckBox
import androidx.glance.layout.padding
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import se.partee71.dagboken.R

/**
 * Delat mellan appens tre hemskärmswidgets (medicin, screening, vid behov, #161/#162).
 *
 * `androidx.glance.material3.GlanceTheme` gick inte att använda ("Unresolved reference"
 * i CI, #156), men färgerna behöver ändå följa ljust/mörkt läge — widgetarna var
 * tidigare alltid mörka oavsett systemets och appens tema (WID-6).
 *
 * Färgerna ligger därför som resurser med en `values-night`-variant och slås upp mot
 * aktuell konfiguration när widgeten ritas. Glances `ColorProvider(resId)`-överlagring
 * används medvetet inte: den är `@RestrictTo(LIBRARY_GROUP)` och flaggas av lint.
 */
val WidgetBackground: ColorProvider
    @Composable get() = ColorProvider(Color(LocalContext.current.getColor(R.color.widget_background)))

val WidgetOnBackground: ColorProvider
    @Composable get() = ColorProvider(Color(LocalContext.current.getColor(R.color.widget_on_background)))

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

