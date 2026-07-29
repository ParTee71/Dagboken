package se.partee71.dagboken.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import se.partee71.dagboken.R
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.domain.model.Medicin
import java.time.LocalTime

/**
 * Hemskärmswidget (Glance): dagens status (screening + medicinsammanfattning) på
 * framsidan, full checklista och screeningguide ett klick bort (#120/#156, #157/#158,
 * #159). Egen opak bakgrund + explicita textfärger i stället för `GlanceTheme` — ett
 * försök att använda `androidx.glance.material3.GlanceTheme` gav "Unresolved reference"
 * i CI (#156); fasta färger undviker den typen av Glance-API-osäkerhet helt.
 */
class DagbokenWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = context.widgetEntryPoint()
        val medicinerRepo = entryPoint.medicinerRepository()
        // Säkrar att dagens doser finns genererade även vid kallstart (enheten precis
        // omstartad, appen aldrig öppnad) — samma metod som Idag-vyn använder.
        medicinerRepo.ensureTodayEntries()
        val items = widgetChecklistItems(medicinerRepo.todayFlow().first())
        val medsSummary = widgetMedsSummary(items, LocalTime.now().hour)

        val screeningToday = entryPoint.aktiviteterRepository().getScreeningToday().firstOrNull()
        val favoriteSymptoms = entryPoint.preferencesRepository().symptomOptions.first()
            .filter { it.isFavorite }

        val widgetPrefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val draft = widgetPrefs.toScreeningDraft()
        val showMeds = widgetPrefs.showMedsPage()

        val strings = WidgetStrings(
            title = context.getString(R.string.widget_title),
            emptyState = context.getString(R.string.widget_empty_state),
            allDone = context.getString(R.string.widget_all_done),
            doseLabelFormat = context.getString(R.string.widget_dose_label),
            screeningStart = context.getString(R.string.widget_screening_start),
            energyTitle = context.getString(R.string.widget_screening_energy_title),
            stressTitle = context.getString(R.string.widget_screening_stress_title),
            symptomTitle = context.getString(R.string.widget_screening_symptom_title),
            next = context.getString(R.string.widget_screening_next),
            back = context.getString(R.string.widget_screening_back),
            save = context.getString(R.string.widget_screening_save),
            cancel = context.getString(R.string.widget_screening_cancel),
            backToFront = context.getString(R.string.widget_back_to_front),
            decrease = context.getString(R.string.decrease),
            increase = context.getString(R.string.increase),
        )
        val screeningStatusLabel = screeningToday?.let {
            context.getString(R.string.widget_screening_logged_format, it.tid, it.energy, it.stress)
        }
        val medsSummaryLabel = buildMedsSummaryLabel(context, medsSummary)

        provideContent {
            WidgetContent(items, strings, screeningStatusLabel, medsSummaryLabel, favoriteSymptoms, draft, showMeds)
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

// Fasta färger i stället för GlanceTheme (se klassdokumentationen ovan) — garanterar
// läsbarhet mot valfri hemskärmstapet utan att bero på osäkra Glance-tema-API:er.
private val WidgetBackground = ColorProvider(Color(0xFF15151B))
private val WidgetOnBackground = ColorProvider(Color(0xFFF2F2F5))
private val WidgetButtonBackground = ColorProvider(Color(0xFF2A2A34))

private data class WidgetStrings(
    val title: String,
    val emptyState: String,
    val allDone: String,
    val doseLabelFormat: String,
    val screeningStart: String,
    val energyTitle: String,
    val stressTitle: String,
    val symptomTitle: String,
    val next: String,
    val back: String,
    val save: String,
    val cancel: String,
    val backToFront: String,
    val decrease: String,
    val increase: String,
)

/** Klickbar [Text] — Glances base-modul saknar en egen "Button"-komposabel. */
@Composable
private fun WidgetButton(text: String, action: Action, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text = text,
        style = TextStyle(color = WidgetOnBackground),
        modifier = modifier
            .background(WidgetButtonBackground)
            .clickable(action)
            .padding(8.dp),
    )
}

@Composable
private fun WidgetContent(
    items: List<Medicin>,
    strings: WidgetStrings,
    screeningStatusLabel: String?,
    medsSummaryLabel: String,
    favoriteSymptoms: List<SymptomOption>,
    draft: ScreeningDraft,
    showMeds: Boolean,
) {
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

        when (draft.step) {
            SCREENING_STEP_ENERGY -> EnergyStep(draft.energy, strings)
            SCREENING_STEP_STRESS -> StressStep(draft.stress, favoriteSymptoms.isNotEmpty(), strings)
            SCREENING_STEP_SYMPTOM -> SymptomStep(favoriteSymptoms, draft.symptomScores, strings)
            else -> if (showMeds) {
                MedsPage(items, strings)
            } else {
                FrontPage(strings, screeningStatusLabel, medsSummaryLabel)
            }
        }
    }
}

@Composable
private fun FrontPage(strings: WidgetStrings, screeningStatusLabel: String?, medsSummaryLabel: String) {
    if (screeningStatusLabel != null) {
        Text(text = screeningStatusLabel, style = TextStyle(color = WidgetOnBackground))
    } else {
        WidgetButton(strings.screeningStart, actionRunCallback<StartScreeningAction>())
    }
    WidgetButton(
        medsSummaryLabel,
        actionRunCallback<SetWidgetPageAction>(actionParametersOf(SetWidgetPageAction.KEY_SHOW_MEDS to true)),
        modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun MedsPage(items: List<Medicin>, strings: WidgetStrings) {
    WidgetButton(
        strings.backToFront,
        actionRunCallback<SetWidgetPageAction>(actionParametersOf(SetWidgetPageAction.KEY_SHOW_MEDS to false)),
    )
    ChecklistSection(items, strings)
}

@Composable
private fun ChecklistSection(items: List<Medicin>, strings: WidgetStrings) {
    when {
        items.isEmpty() -> Text(text = strings.emptyState, style = TextStyle(color = WidgetOnBackground))
        items.all { it.tagen } -> Text(text = strings.allDone, style = TextStyle(color = WidgetOnBackground))
        else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(items, itemId = { it.id.hashCode().toLong() }) { medicin ->
                MedicinRow(medicin, strings.doseLabelFormat)
            }
        }
    }
}

@Composable
private fun ValueStepperRow(value: Int, field: String, strings: WidgetStrings) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
        WidgetButton(
            strings.decrease,
            actionRunCallback<AdjustScreeningValueAction>(
                actionParametersOf(
                    AdjustScreeningValueAction.KEY_FIELD to field,
                    AdjustScreeningValueAction.KEY_DELTA to -1,
                ),
            ),
        )
        Text(
            text = value.toString(),
            style = TextStyle(color = WidgetOnBackground),
            modifier = GlanceModifier.padding(horizontal = 12.dp),
        )
        WidgetButton(
            strings.increase,
            actionRunCallback<AdjustScreeningValueAction>(
                actionParametersOf(
                    AdjustScreeningValueAction.KEY_FIELD to field,
                    AdjustScreeningValueAction.KEY_DELTA to 1,
                ),
            ),
        )
    }
}

@Composable
private fun EnergyStep(value: Int, strings: WidgetStrings) {
    Text(text = strings.energyTitle, style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold))
    ValueStepperRow(value, AdjustScreeningValueAction.FIELD_ENERGY, strings)
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
        WidgetButton(
            strings.cancel,
            actionRunCallback<ScreeningNavAction>(
                actionParametersOf(ScreeningNavAction.KEY_DIRECTION to "cancel"),
            ),
        )
        WidgetButton(
            strings.next,
            actionRunCallback<ScreeningNavAction>(
                actionParametersOf(ScreeningNavAction.KEY_DIRECTION to "next"),
            ),
        )
    }
}

@Composable
private fun StressStep(value: Int, hasFavoriteSymptoms: Boolean, strings: WidgetStrings) {
    Text(text = strings.stressTitle, style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold))
    ValueStepperRow(value, AdjustScreeningValueAction.FIELD_STRESS, strings)
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
        WidgetButton(
            strings.back,
            actionRunCallback<ScreeningNavAction>(
                actionParametersOf(ScreeningNavAction.KEY_DIRECTION to "back"),
            ),
        )
        if (hasFavoriteSymptoms) {
            WidgetButton(
                strings.next,
                actionRunCallback<ScreeningNavAction>(
                    actionParametersOf(ScreeningNavAction.KEY_DIRECTION to "next"),
                ),
            )
        } else {
            WidgetButton(strings.save, actionRunCallback<SaveScreeningAction>())
        }
    }
}

@Composable
private fun SymptomStep(favoriteSymptoms: List<SymptomOption>, scores: Map<String, Int>, strings: WidgetStrings) {
    Text(text = strings.symptomTitle, style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold))
    LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
        items(favoriteSymptoms, itemId = { it.name.hashCode().toLong() }) { option ->
            Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(text = option.name, style = TextStyle(color = WidgetOnBackground))
                SymptomStepperRow(option.name, scores[option.name] ?: 0, strings)
            }
        }
    }
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
        WidgetButton(
            strings.back,
            actionRunCallback<ScreeningNavAction>(
                actionParametersOf(ScreeningNavAction.KEY_DIRECTION to "back"),
            ),
        )
        WidgetButton(strings.save, actionRunCallback<SaveScreeningAction>())
    }
}

@Composable
private fun SymptomStepperRow(name: String, value: Int, strings: WidgetStrings) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        WidgetButton(
            strings.decrease,
            actionRunCallback<AdjustSymptomScoreAction>(
                actionParametersOf(
                    AdjustSymptomScoreAction.KEY_SYMPTOM_NAME to name,
                    AdjustSymptomScoreAction.KEY_DELTA to -1,
                ),
            ),
        )
        Text(
            text = value.toString(),
            style = TextStyle(color = WidgetOnBackground),
            modifier = GlanceModifier.padding(horizontal = 12.dp),
        )
        WidgetButton(
            strings.increase,
            actionRunCallback<AdjustSymptomScoreAction>(
                actionParametersOf(
                    AdjustSymptomScoreAction.KEY_SYMPTOM_NAME to name,
                    AdjustSymptomScoreAction.KEY_DELTA to 1,
                ),
            ),
        )
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
