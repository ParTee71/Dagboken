package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/** +/- stepper (0..10) för energi/stress-steget i screeningguiden. */
class AdjustScreeningValueAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        recordWidgetTap(context, glanceId)
        val field = parameters[KEY_FIELD] ?: return
        val delta = parameters[KEY_DELTA] ?: return
        val key = if (field == FIELD_ENERGY) ScreeningWidgetKeys.ENERGY else ScreeningWidgetKeys.STRESS
        updateAppWidgetState(context, glanceId) { prefs -> prefs.adjustScreeningValue(key, delta) }
        ScreeningWidget().update(context, glanceId)
    }

    companion object {
        const val FIELD_ENERGY = "energy"
        const val FIELD_STRESS = "stress"
        val KEY_FIELD: ActionParameters.Key<String> = ActionParameters.Key("field")
        val KEY_DELTA: ActionParameters.Key<Int> = ActionParameters.Key("delta")
    }
}
