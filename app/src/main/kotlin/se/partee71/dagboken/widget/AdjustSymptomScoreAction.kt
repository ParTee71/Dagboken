package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import se.partee71.dagboken.domain.usecase.SymptomUtils

/** +/- stepper (0..10) för ett enskilt favoritsymptom i screeningguidens symptomsteg. */
class AdjustSymptomScoreAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val name = parameters[KEY_SYMPTOM_NAME] ?: return
        val delta = parameters[KEY_DELTA] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            val scores = SymptomUtils.decode(prefs[ScreeningWidgetKeys.SYMPTOM_SCORES] ?: "")
            val updated = adjustSymptomScore(scores, name, delta)
            prefs.toMutablePreferences().apply {
                this[ScreeningWidgetKeys.SYMPTOM_SCORES] = SymptomUtils.encode(updated)
            }
        }
        DagbokenWidget().update(context, glanceId)
    }

    companion object {
        val KEY_SYMPTOM_NAME: ActionParameters.Key<String> = ActionParameters.Key("symptom_name")
        val KEY_DELTA: ActionParameters.Key<Int> = ActionParameters.Key("delta")
    }
}
