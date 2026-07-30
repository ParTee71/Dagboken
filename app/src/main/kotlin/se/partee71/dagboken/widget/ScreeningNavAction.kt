package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.flow.first

/** Steg fram/tillbaka/avbryt i screeningguiden — se [nextScreeningStep]. */
class ScreeningNavAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        recordWidgetTap(context, glanceId)
        val direction = parameters[KEY_DIRECTION] ?: return
        val hasFavoriteSymptoms = context.widgetEntryPoint().preferencesRepository()
            .symptomOptions.first().any { it.isFavorite }
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.stepScreeningDraft(direction, hasFavoriteSymptoms)
        }
        ScreeningWidget().update(context, glanceId)
    }

    companion object {
        val KEY_DIRECTION: ActionParameters.Key<String> = ActionParameters.Key("direction")
    }
}
