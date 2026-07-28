package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Bockar av/på en medicindos från widgeten via samma skrivväg som
 * [se.partee71.dagboken.ui.home.HomeViewModel.toggleMedicinTagen]
 * (`MedicinerRepository.toggleTagen`) — ingen duplicerad logik (regel 4).
 */
class ToggleMedicinAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[KEY_MEDICIN_ID] ?: return
        val currentlyTagen = parameters[KEY_CURRENTLY_TAGEN] ?: false
        context.widgetEntryPoint().medicinerRepository().toggleTagen(id, !currentlyTagen)
        DagbokenWidget().update(context, glanceId)
    }

    companion object {
        val KEY_MEDICIN_ID: ActionParameters.Key<String> = ActionParameters.Key("medicin_id")
        val KEY_CURRENTLY_TAGEN: ActionParameters.Key<Boolean> = ActionParameters.Key("currently_tagen")
    }
}
