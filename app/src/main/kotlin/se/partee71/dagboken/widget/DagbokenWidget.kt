package se.partee71.dagboken.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import se.partee71.dagboken.R
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.domain.model.Medicin

/**
 * Hemskärmswidget (Glance) som visar dagens medicinchecklista (#120/#156) och låter
 * dagens screening loggas stegvis (energi → stress → symptom), #157.
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

        val screeningLoggedToday = entryPoint.aktiviteterRepository().hasScreeningToday()
        val favoriteSymptoms = entryPoint.preferencesRepository().symptomOptions.first()
            .filter { it.isFavorite }
        val draft = getAppWidgetState(context, PreferencesGlanceStateDefinition, id).toScreeningDraft()

        val strings = WidgetStrings(
            title = context.getString(R.string.widget_title),
            emptyState = context.getString(R.string.widget_empty_state),
            allDone = context.getString(R.string.widget_all_done),
            doseLabelFormat = context.getString(R.string.widget_dose_label),
            screeningLogged = context.getString(R.string.widget_screening_logged),
            screeningStart = context.getString(R.string.widget_screening_start),
            energyTitle = context.getString(R.string.widget_screening_energy_title),
            stressTitle = context.getString(R.string.widget_screening_stress_title),
            symptomTitle = context.getString(R.string.widget_screening_symptom_title),
            next = context.getString(R.string.widget_screening_next),
            back = context.getString(R.string.widget_screening_back),
            save = context.getString(R.string.widget_screening_save),
            cancel = context.getString(R.string.widget_screening_cancel),
        )

        provideContent {
            WidgetContent(items, strings, screeningLoggedToday, favoriteSymptoms, draft)
        }
    }
}

private data class WidgetStrings(
    val title: String,
    val emptyState: String,
    val allDone: String,
    val doseLabelFormat: String,
    val screeningLogged: String,
    val screeningStart: String,
    val energyTitle: String,
    val stressTitle: String,
    val symptomTitle: String,
    val next: String,
    val back: String,
    val save: String,
    val cancel: String,
)

/**
 * Glances base-modul saknar en kompositbar "Button" (till skillnad från CheckBox/Switch/
 * RadioButton, som motsvarar riktiga RemoteViews-compound-views) — en klickbar [Text] är det
 * vedertagna sättet att bygga en knapp.
 */
@Composable
private fun WidgetButton(text: String, action: Action, modifier: GlanceModifier = GlanceModifier) {
    Text(text = text, modifier = modifier.clickable(action).padding(8.dp))
}

@Composable
private fun WidgetContent(
    items: List<Medicin>,
    strings: WidgetStrings,
    screeningLoggedToday: Boolean,
    favoriteSymptoms: List<SymptomOption>,
    draft: ScreeningDraft,
) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
        Text(text = strings.title, style = TextStyle(fontWeight = FontWeight.Bold))

        when (draft.step) {
            SCREENING_STEP_ENERGY -> EnergyStep(draft.energy, strings)
            SCREENING_STEP_STRESS -> StressStep(draft.stress, favoriteSymptoms.isNotEmpty(), strings)
            SCREENING_STEP_SYMPTOM -> SymptomStep(favoriteSymptoms, draft.symptomScores, strings)
            else -> {
                ChecklistSection(items, strings)
                ScreeningPrompt(screeningLoggedToday, strings)
            }
        }
    }
}

@Composable
private fun ChecklistSection(items: List<Medicin>, strings: WidgetStrings) {
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

@Composable
private fun ScreeningPrompt(screeningLoggedToday: Boolean, strings: WidgetStrings) {
    if (screeningLoggedToday) {
        Text(text = strings.screeningLogged)
    } else {
        WidgetButton(strings.screeningStart, actionRunCallback<StartScreeningAction>())
    }
}

@Composable
private fun ValueStepperRow(value: Int, field: String) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
        WidgetButton(
            "–",
            actionRunCallback<AdjustScreeningValueAction>(
                actionParametersOf(
                    AdjustScreeningValueAction.KEY_FIELD to field,
                    AdjustScreeningValueAction.KEY_DELTA to -1,
                ),
            ),
        )
        Text(text = value.toString(), modifier = GlanceModifier.padding(horizontal = 12.dp))
        WidgetButton(
            "+",
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
    Text(text = strings.energyTitle, style = TextStyle(fontWeight = FontWeight.Bold))
    ValueStepperRow(value, AdjustScreeningValueAction.FIELD_ENERGY)
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
    Text(text = strings.stressTitle, style = TextStyle(fontWeight = FontWeight.Bold))
    ValueStepperRow(value, AdjustScreeningValueAction.FIELD_STRESS)
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
    Text(text = strings.symptomTitle, style = TextStyle(fontWeight = FontWeight.Bold))
    LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
        items(favoriteSymptoms, itemId = { it.name.hashCode().toLong() }) { option ->
            Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(text = option.name)
                SymptomStepperRow(option.name, scores[option.name] ?: 0)
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
private fun SymptomStepperRow(name: String, value: Int) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        WidgetButton(
            "–",
            actionRunCallback<AdjustSymptomScoreAction>(
                actionParametersOf(
                    AdjustSymptomScoreAction.KEY_SYMPTOM_NAME to name,
                    AdjustSymptomScoreAction.KEY_DELTA to -1,
                ),
            ),
        )
        Text(text = value.toString(), modifier = GlanceModifier.padding(horizontal = 12.dp))
        WidgetButton(
            "+",
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
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
