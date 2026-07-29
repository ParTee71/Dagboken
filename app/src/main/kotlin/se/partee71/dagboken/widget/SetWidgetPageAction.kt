package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/** Växlar widgetens framsida mellan statusöversikt och full medicinchecklista (#159). */
class SetWidgetPageAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val showMeds = parameters[KEY_SHOW_MEDS] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetPageKeys.SHOW_MEDS] = showMeds
            }
        }
        DagbokenWidget().update(context, glanceId)
    }

    companion object {
        val KEY_SHOW_MEDS: ActionParameters.Key<Boolean> = ActionParameters.Key("show_meds")
    }
}
