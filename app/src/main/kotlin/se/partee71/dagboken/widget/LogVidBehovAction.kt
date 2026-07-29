package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.usecase.VidBehovLogResult

/**
 * Loggar en vid behov-dos via [se.partee71.dagboken.domain.usecase.LogVidBehovDosUseCase] —
 * samma väg som appens "Ta dos" (#162). Glance har inga dialoger, så en cooldown-träff
 * lagras som ett bekräftelsesteg i widgetens eget state i stället för `CooldownWarning`
 * (som appen visar som dialog).
 */
class LogVidBehovAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val favoritId = parameters[KEY_FAVORIT_ID] ?: return
        val force = parameters[KEY_FORCE] ?: false
        val entryPoint = context.widgetEntryPoint()
        val favorit = entryPoint.medicinerRepository().getFavoritById(favoritId) ?: return

        when (val result = entryPoint.logVidBehovDosUseCase().logDose(favorit, force)) {
            VidBehovLogResult.Logged -> updateAppWidgetState(context, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    remove(VidBehovWidgetKeys.PENDING_FAVORIT_ID)
                    remove(VidBehovWidgetKeys.PENDING_REMAINING_HOURS)
                    this[VidBehovWidgetKeys.MESSAGE] =
                        context.getString(R.string.widget_vidbehov_logged_format, favorit.namn)
                }
            }
            is VidBehovLogResult.CooldownWarning -> updateAppWidgetState(context, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[VidBehovWidgetKeys.PENDING_FAVORIT_ID] = favoritId
                    this[VidBehovWidgetKeys.PENDING_REMAINING_HOURS] = result.remainingHours
                }
            }
            VidBehovLogResult.DailyLimitReached -> updateAppWidgetState(context, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    remove(VidBehovWidgetKeys.PENDING_FAVORIT_ID)
                    remove(VidBehovWidgetKeys.PENDING_REMAINING_HOURS)
                    this[VidBehovWidgetKeys.MESSAGE] = context.getString(
                        R.string.widget_vidbehov_limit_format, favorit.maxDoserPerDag, favorit.namn,
                    )
                }
            }
        }
        VidBehovWidget().update(context, glanceId)
    }

    companion object {
        val KEY_FAVORIT_ID: ActionParameters.Key<String> = ActionParameters.Key("favorit_id")
        val KEY_FORCE: ActionParameters.Key<Boolean> = ActionParameters.Key("force")
    }
}
