package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/** Startar screeningguiden: nollställer draft-state och visar energisteget. */
class StartScreeningAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val label = parameters[KEY_LABEL] ?: ""
        updateAppWidgetState(context, glanceId) { prefs -> prefs.startScreeningDraft(label) }
        ScreeningWidget().update(context, glanceId)
    }

    companion object {
        val KEY_LABEL: ActionParameters.Key<String> = ActionParameters.Key("label")
    }
}
