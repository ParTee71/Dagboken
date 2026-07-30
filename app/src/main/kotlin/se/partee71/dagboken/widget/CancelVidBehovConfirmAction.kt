package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/** Avbryter en väntande cooldown-bekräftelse utan att logga dosen. */
class CancelVidBehovConfirmAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        recordWidgetTap(context, glanceId)
        updateAppWidgetState(context, glanceId) { prefs -> prefs.clearVidBehovPendingConfirm() }
        VidBehovWidget().update(context, glanceId)
    }
}
