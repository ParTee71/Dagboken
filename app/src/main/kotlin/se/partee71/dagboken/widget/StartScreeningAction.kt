package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/** Startar screeningguiden i widgeten: nollställer draft-state och visar energisteget. */
class StartScreeningAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[ScreeningWidgetKeys.STEP] = SCREENING_STEP_ENERGY
                this[ScreeningWidgetKeys.ENERGY] = 5
                this[ScreeningWidgetKeys.STRESS] = 5
                this[ScreeningWidgetKeys.SYMPTOM_SCORES] = ""
            }
        }
        DagbokenWidget().update(context, glanceId)
    }
}
