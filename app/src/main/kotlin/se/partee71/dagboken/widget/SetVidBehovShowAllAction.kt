package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/** Växlar vid behov-widgeten mellan favoriter och alla vid behov-mediciner (#164). */
class SetVidBehovShowAllAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val showAll = parameters[KEY_SHOW_ALL] ?: return
        updateAppWidgetState(context, glanceId) { prefs -> prefs.setVidBehovShowAll(showAll) }
        VidBehovWidget().update(context, glanceId)
    }

    companion object {
        val KEY_SHOW_ALL: ActionParameters.Key<Boolean> = ActionParameters.Key("show_all")
    }
}
