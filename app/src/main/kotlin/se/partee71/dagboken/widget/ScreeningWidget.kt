package se.partee71.dagboken.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
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
import kotlinx.coroutines.flow.first
import se.partee71.dagboken.R
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.domain.usecase.ScreeningEventStatus
import se.partee71.dagboken.domain.usecase.activeScreeningEventLabels
import se.partee71.dagboken.domain.usecase.computeScreeningEvents
import java.time.LocalTime

/**
 * Hemskärmswidget för att logga dagens screening (#157/#158, #161) — uppdelad ur den
 * ursprungliga kombinerade widgeten så screeningflödet inte delar yta med
 * medicinchecklistan ([DagbokenWidget]). Visar nästa ej loggade screeningtillfälle;
 * loggning namnges efter tillfället så det markeras som klart i appens Idag-vy.
 */
class ScreeningWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = context.widgetEntryPoint()
        val prefsRepo = entryPoint.preferencesRepository()
        val activeEvents = activeScreeningEventLabels(prefsRepo.screeningEventConfigs.first())
        val screeningsToday = entryPoint.aktiviteterRepository().getScreeningToday()
        val events = computeScreeningEvents(activeEvents, screeningsToday, LocalTime.now(), isToday = true)
        val nextEvent = events.firstOrNull { !it.logged }
        val allLogged = events.isNotEmpty() && nextEvent == null

        val favoriteSymptoms = prefsRepo.symptomOptions.first().filter { it.isFavorite }
        val widgetPrefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val draft = widgetPrefs.toScreeningDraft()
        val tapCount = widgetPrefs[WIDGET_TAP_COUNT] ?: 0

        val strings = ScreeningWidgetStrings(
            title = context.getString(R.string.widget_screening_title),
            allLogged = context.getString(R.string.widget_screening_all_logged),
            startFormat = context.getString(R.string.widget_screening_start_format),
            startStandalone = context.getString(R.string.widget_screening_start),
            energyTitle = context.getString(R.string.widget_screening_energy_title),
            stressTitle = context.getString(R.string.widget_screening_stress_title),
            symptomTitle = context.getString(R.string.widget_screening_symptom_title),
            next = context.getString(R.string.widget_screening_next),
            back = context.getString(R.string.widget_screening_back),
            save = context.getString(R.string.widget_screening_save),
            cancel = context.getString(R.string.widget_screening_cancel),
            decrease = context.getString(R.string.decrease),
            increase = context.getString(R.string.increase),
        )

        provideContent {
            ScreeningWidgetContent(strings, events, nextEvent, allLogged, favoriteSymptoms, draft, tapCount)
        }
    }
}

private data class ScreeningWidgetStrings(
    val title: String,
    val allLogged: String,
    val startFormat: String,
    val startStandalone: String,
    val energyTitle: String,
    val stressTitle: String,
    val symptomTitle: String,
    val next: String,
    val back: String,
    val save: String,
    val cancel: String,
    val decrease: String,
    val increase: String,
)

@Composable
private fun ScreeningWidgetContent(
    strings: ScreeningWidgetStrings,
    events: List<ScreeningEventStatus>,
    nextEvent: ScreeningEventStatus?,
    allLogged: Boolean,
    favoriteSymptoms: List<SymptomOption>,
    draft: ScreeningDraft,
    tapCount: Int,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(12.dp),
    ) {
        // Diagnostik (tillfällig, se recordWidgetTap): visar hur många gånger en
        // ActionCallback faktiskt körts från den här widgeten.
        Text(
            text = "${strings.title} · tryck: $tapCount",
            style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold),
        )

        when (draft.step) {
            SCREENING_STEP_ENERGY -> EnergyStep(draft.energy, strings)
            SCREENING_STEP_STRESS -> StressStep(draft.stress, favoriteSymptoms.isNotEmpty(), strings)
            SCREENING_STEP_SYMPTOM -> SymptomStep(favoriteSymptoms, draft.symptomScores, strings)
            else -> FrontStatus(events, nextEvent, allLogged, strings)
        }
    }
}

@Composable
private fun FrontStatus(
    events: List<ScreeningEventStatus>,
    nextEvent: ScreeningEventStatus?,
    allLogged: Boolean,
    strings: ScreeningWidgetStrings,
) {
    when {
        allLogged -> Text(text = strings.allLogged, style = TextStyle(color = WidgetOnBackground))
        nextEvent != null -> WidgetButton(
            String.format(strings.startFormat, nextEvent.label, nextEvent.time),
            actionRunCallback<StartScreeningAction>(
                actionParametersOf(StartScreeningAction.KEY_LABEL to nextEvent.label),
            ),
        )
        events.isEmpty() -> WidgetButton(
            strings.startStandalone,
            actionRunCallback<StartScreeningAction>(
                actionParametersOf(StartScreeningAction.KEY_LABEL to ""),
            ),
        )
        else -> Unit
    }
}

@Composable
private fun ValueStepperRow(value: Int, field: String, strings: ScreeningWidgetStrings) {
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
private fun EnergyStep(value: Int, strings: ScreeningWidgetStrings) {
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
private fun StressStep(value: Int, hasFavoriteSymptoms: Boolean, strings: ScreeningWidgetStrings) {
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
private fun SymptomStep(
    favoriteSymptoms: List<SymptomOption>,
    scores: Map<String, Int>,
    strings: ScreeningWidgetStrings,
) {
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
private fun SymptomStepperRow(name: String, value: Int, strings: ScreeningWidgetStrings) {
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
