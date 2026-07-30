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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import java.time.LocalTime

/**
 * Hemskärmswidget för dagens schemalagda medicinchecklista (#120/#156, #159, #161).
 * Uppdelad från den ursprungliga kombinerade widgeten (#161) — screening loggas numera i
 * en egen widget ([ScreeningWidget]). Tillståndslös: läser bara dagens doser och ritar om.
 */
class DagbokenWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val medicinerRepo = context.widgetEntryPoint().medicinerRepository()
        // Säkrar att dagens doser finns genererade även vid kallstart (enheten precis
        // omstartad, appen aldrig öppnad) — samma metod som Idag-vyn använder.
        medicinerRepo.ensureTodayEntries()
        val items = widgetChecklistItems(medicinerRepo.todayFlow().first())
        val medsSummary = widgetMedsSummary(items, LocalTime.now().hour)

        val strings = MedsWidgetStrings(
            title = context.getString(R.string.widget_title),
            emptyState = context.getString(R.string.widget_empty_state),
            allDone = context.getString(R.string.widget_all_done),
            doseLabelFormat = context.getString(R.string.widget_dose_label),
        )
        val summaryLabel = buildMedsSummaryLabel(context, medsSummary)

        provideContent {
            MedsWidgetContent(items, strings, summaryLabel)
        }
    }
}

private fun buildMedsSummaryLabel(context: Context, summary: WidgetMedsSummary): String {
    val base = context.getString(R.string.widget_meds_summary_format, summary.taken, summary.total)
    if (summary.overdue == 0) return base
    val overdueText = if (summary.overdue == 1) {
        context.getString(R.string.widget_meds_overdue_one)
    } else {
        context.getString(R.string.widget_meds_overdue_many, summary.overdue)
    }
    return "$base · $overdueText"
}

private data class MedsWidgetStrings(
    val title: String,
    val emptyState: String,
    val allDone: String,
    val doseLabelFormat: String,
)

@Composable
private fun MedsWidgetContent(items: List<Medicin>, strings: MedsWidgetStrings, summaryLabel: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(12.dp),
    ) {
        Text(
            text = strings.title,
            style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold),
        )
        Text(text = summaryLabel, style = TextStyle(color = WidgetOnBackground))

        val actionable = widgetActionableItems(items)
        when {
            items.isEmpty() -> Text(text = strings.emptyState, style = TextStyle(color = WidgetOnBackground))
            actionable.isEmpty() -> Text(text = strings.allDone, style = TextStyle(color = WidgetOnBackground))
            // Vanlig Column, inte LazyColumn: i en LazyColumn blir varje rad ett
            // RemoteViews-collection-item, där per-rad-klick kräver PendingIntent-template
            // + fill-in-intents — och bara den första radens kryssruta reagerade på tryck.
            // En Column ger varje rad en egen vanlig PendingIntent. Listan är kort (tagna
            // doser döljs, #164) så den saknade scrollningen är inte ett problem i praktiken.
            else -> Column(modifier = GlanceModifier.fillMaxWidth()) {
                actionable.forEach { medicin -> MedicinRow(medicin, strings.doseLabelFormat) }
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
        style = TextStyle(color = WidgetOnBackground),
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
