package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import se.partee71.dagboken.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Sparar dagens screening från widgetens draft-state via samma mappning som appen
 * ([se.partee71.dagboken.domain.usecase.BuildScreeningAktivitetUseCase]), sen återgår
 * widgeten till checklistan — som nu visar screeningen som loggad (bekräftelse, WID-4).
 */
class SaveScreeningAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val draft = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId).toScreeningDraft()
        if (draft.step == SCREENING_STEP_INACTIVE) return

        val entryPoint = context.widgetEntryPoint()
        val entry = entryPoint.buildScreeningAktivitetUseCase().build(
            aktivitetName = context.getString(R.string.widget_screening_name),
            datum = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            tid = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
            energy = draft.energy,
            stress = draft.stress,
            symptomScores = draft.symptomScores,
        )
        entryPoint.aktiviteterRepository().save(entry)

        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[ScreeningWidgetKeys.STEP] = SCREENING_STEP_INACTIVE
                remove(ScreeningWidgetKeys.ENERGY)
                remove(ScreeningWidgetKeys.STRESS)
                remove(ScreeningWidgetKeys.SYMPTOM_SCORES)
            }
        }
        DagbokenWidget().update(context, glanceId)
    }
}
