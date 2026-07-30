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
 * Hemskärmswidget för att logga en vid behov-dos direkt (#162) — samma skrivväg som appens
 * "Ta dos" ([se.partee71.dagboken.domain.usecase.LogVidBehovDosUseCase]). Visar
 * favoritmarkerade mediciner direkt; en "Fler"-rad expanderar till alla vid behov-mediciner
 * (favoriter först, sedan bokstavsordning), #164. Cooldown-träffar visas som ett
 * bekräftelsesteg i widgeten eftersom Glance inte har dialoger.
 */
class VidBehovWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = context.widgetEntryPoint()
        val allFavoriter = entryPoint.medicinerRepository().allFavoriter.first()

        val widgetPrefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val draft = widgetPrefs.toVidBehovDraft()
        // Meddelandet (loggad/dagsgräns) ska bara synas en gång — nollställs direkt efter läsning.
        if (draft.message != null) {
            updateAppWidgetState(context, id) { prefs ->
                prefs.toMutablePreferences().apply { remove(VidBehovWidgetKeys.MESSAGE) }
            }
        }
        val displayed = if (draft.showAll) allVidBehovSorted(allFavoriter) else favoriteVidBehov(allFavoriter)
        val pendingFavorit = draft.pending?.let { pending -> allFavoriter.find { it.id == pending.favoritId } }
        val tapCount = widgetPrefs[WIDGET_TAP_COUNT] ?: 0

        val strings = VidBehovWidgetStrings(
            title = context.getString(R.string.widget_vidbehov_title),
            empty = context.getString(R.string.widget_vidbehov_empty),
            emptyFavorites = context.getString(R.string.widget_vidbehov_empty_favorites),
            confirmFormat = context.getString(R.string.widget_vidbehov_confirm_format),
            logAnyway = context.getString(R.string.widget_vidbehov_log_anyway),
            cancel = context.getString(R.string.widget_screening_cancel),
            showMore = context.getString(R.string.widget_vidbehov_show_more),
            showFavorites = context.getString(R.string.widget_vidbehov_show_favorites),
        )

        provideContent {
            VidBehovWidgetContent(
                displayed, draft.showAll, allFavoriter.isNotEmpty(), draft.pending, pendingFavorit, draft.message,
                strings, tapCount,
            )
        }
    }
}

private data class VidBehovWidgetStrings(
    val title: String,
    val empty: String,
    val emptyFavorites: String,
    val confirmFormat: String,
    val logAnyway: String,
    val cancel: String,
    val showMore: String,
    val showFavorites: String,
)

@Composable
private fun VidBehovWidgetContent(
    favoriter: List<Favorit>,
    showAll: Boolean,
    hasAnyMedicine: Boolean,
    pending: VidBehovPendingConfirm?,
    pendingFavorit: Favorit?,
    message: String?,
    strings: VidBehovWidgetStrings,
    tapCount: Int,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(12.dp),
    ) {
        // Diagnostik (tillfällig, se recordWidgetTap).
        Text(
            text = "${strings.title} · tryck: $tapCount",
            style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold),
        )
        if (message != null) {
            Text(text = message, style = TextStyle(color = WidgetOnBackground))
        }

        if (pending != null && pendingFavorit != null) {
            ConfirmCooldown(pending, pendingFavorit, strings)
        } else {
            FavoritList(favoriter, showAll, hasAnyMedicine, strings)
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
private fun FavoritList(favoriter: List<Favorit>, showAll: Boolean, hasAnyMedicine: Boolean, strings: VidBehovWidgetStrings) {
    if (!hasAnyMedicine) {
        Text(text = strings.empty, style = TextStyle(color = WidgetOnBackground))
        return
    }
    if (favoriter.isEmpty()) {
        Text(text = strings.emptyFavorites, style = TextStyle(color = WidgetOnBackground))
    } else {
        LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
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
    // Alltid tillgänglig när det finns någon vid behov-medicin, även om just den valda
    // vyn (favoriter) råkar vara tom — annars finns ingen väg till de icke-favoriserade.
    WidgetButton(
        if (showAll) strings.showFavorites else strings.showMore,
        actionRunCallback<SetVidBehovShowAllAction>(
            actionParametersOf(SetVidBehovShowAllAction.KEY_SHOW_ALL to !showAll),
        ),
        modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
    )
}
