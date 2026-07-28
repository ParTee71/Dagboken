package se.partee71.dagboken.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Medicin

/**
 * Hemskärmswidget (Glance) som visar dagens medicinchecklista, #120. Screening-loggning
 * (WID-3) är avsiktligt inte med i detta steg — se uppföljande issue.
 */
class DagbokenWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.widgetEntryPoint().medicinerRepository()
        // Säkrar att dagens doser finns genererade även vid kallstart (enheten precis
        // omstartad, appen aldrig öppnad) — samma metod som Idag-vyn använder.
        repo.ensureTodayEntries()
        val items = widgetChecklistItems(repo.todayFlow().first())
        val strings = WidgetStrings(
            title = context.getString(R.string.widget_title),
            emptyState = context.getString(R.string.widget_empty_state),
            allDone = context.getString(R.string.widget_all_done),
            doseLabelFormat = context.getString(R.string.widget_dose_label),
        )

        provideContent {
            WidgetContent(items, strings)
        }
    }
}

private data class WidgetStrings(
    val title: String,
    val emptyState: String,
    val allDone: String,
    val doseLabelFormat: String,
)

@Composable
private fun WidgetContent(items: List<Medicin>, strings: WidgetStrings) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
        Text(
            text = strings.title,
            style = TextStyle(fontWeight = FontWeight.Bold),
        )

        when {
            items.isEmpty() -> Text(text = strings.emptyState)
            items.all { it.tagen } -> Text(text = strings.allDone)
            else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items, itemId = { it.id.hashCode().toLong() }) { medicin ->
                    MedicinRow(medicin, strings.doseLabelFormat)
                }
            }
        }
    }
}

@Composable
private fun MedicinRow(medicin: Medicin, labelFormat: String) {
    val label = String.format(labelFormat, medicin.namn, medicin.dos, medicin.enhet, medicin.tidpunkt)
    CheckBox(
        checked = medicin.tagen,
        onCheckedChange = actionRunCallback<ToggleMedicinAction>(
            actionParametersOf(
                ToggleMedicinAction.KEY_MEDICIN_ID to medicin.id,
                ToggleMedicinAction.KEY_CURRENTLY_TAGEN to medicin.tagen,
            ),
        ),
        text = label,
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
