package se.partee71.dagboken.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Favorit
import java.util.Locale

/**
 * Hemskärmswidget för att logga en favoritmarkerad vid behov-dos direkt (#162) — samma
 * skrivväg som appens "Ta dos" ([se.partee71.dagboken.domain.usecase.LogVidBehovDosUseCase]).
 * Cooldown-träffar visas som ett bekräftelsesteg i widgeten eftersom Glance inte har dialoger.
 */
class VidBehovWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = context.widgetEntryPoint()
        val favoriter = entryPoint.medicinerRepository().allFavoriter.first().filter { it.isFavorite }

        val widgetPrefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val draft = widgetPrefs.toVidBehovDraft()
        // Meddelandet (loggad/dagsgräns) ska bara synas en gång — nollställs direkt efter läsning.
        if (draft.message != null) {
            updateAppWidgetState(context, id) { prefs ->
                prefs.toMutablePreferences().apply { remove(VidBehovWidgetKeys.MESSAGE) }
            }
        }
        val pendingFavorit = draft.pending?.let { pending -> favoriter.find { it.id == pending.favoritId } }

        val strings = VidBehovWidgetStrings(
            title = context.getString(R.string.widget_vidbehov_title),
            empty = context.getString(R.string.widget_vidbehov_empty),
            confirmFormat = context.getString(R.string.widget_vidbehov_confirm_format),
            logAnyway = context.getString(R.string.widget_vidbehov_log_anyway),
            cancel = context.getString(R.string.widget_screening_cancel),
        )

        provideContent {
            VidBehovWidgetContent(favoriter, draft.pending, pendingFavorit, draft.message, strings)
        }
    }
}

private data class VidBehovWidgetStrings(
    val title: String,
    val empty: String,
    val confirmFormat: String,
    val logAnyway: String,
    val cancel: String,
)

@Composable
private fun VidBehovWidgetContent(
    favoriter: List<Favorit>,
    pending: VidBehovPendingConfirm?,
    pendingFavorit: Favorit?,
    message: String?,
    strings: VidBehovWidgetStrings,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(12.dp),
    ) {
        Text(text = strings.title, style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold))
        if (message != null) {
            Text(text = message, style = TextStyle(color = WidgetOnBackground))
        }

        if (pending != null && pendingFavorit != null) {
            ConfirmCooldown(pending, pendingFavorit, strings)
        } else {
            FavoritList(favoriter, strings)
        }
    }
}

@Composable
private fun ConfirmCooldown(pending: VidBehovPendingConfirm, favorit: Favorit, strings: VidBehovWidgetStrings) {
    Text(
        text = String.format(Locale.getDefault(), strings.confirmFormat, favorit.namn, pending.remainingHours),
        style = TextStyle(color = WidgetOnBackground),
    )
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
        WidgetButton(strings.cancel, actionRunCallback<CancelVidBehovConfirmAction>())
        WidgetButton(
            strings.logAnyway,
            actionRunCallback<LogVidBehovAction>(
                actionParametersOf(
                    LogVidBehovAction.KEY_FAVORIT_ID to favorit.id,
                    LogVidBehovAction.KEY_FORCE to true,
                ),
            ),
        )
    }
}

@Composable
private fun FavoritList(favoriter: List<Favorit>, strings: VidBehovWidgetStrings) {
    if (favoriter.isEmpty()) {
        Text(text = strings.empty, style = TextStyle(color = WidgetOnBackground))
        return
    }
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(favoriter, itemId = { it.id.hashCode().toLong() }) { favorit ->
            WidgetButton(
                "${favorit.namn} ${favorit.dos} ${favorit.enhet}",
                actionRunCallback<LogVidBehovAction>(
                    actionParametersOf(
                        LogVidBehovAction.KEY_FAVORIT_ID to favorit.id,
                        LogVidBehovAction.KEY_FORCE to false,
                    ),
                ),
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
            )
        }
    }
}
