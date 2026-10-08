package se.partee71.dagboken.ui.illness

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.entryDeleteAction
import se.partee71.dagboken.ui.common.message
import se.partee71.dagboken.ui.common.nonBlank
import se.partee71.dagboken.ui.common.periodText
import se.partee71.dagboken.ui.common.title
import se.partee71.dagboken.ui.components.AppButton
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppDivider
import se.partee71.dagboken.ui.components.AppMenuItem
import se.partee71.dagboken.ui.components.ButtonVariant
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.DagbokenEntryCard
import se.partee71.dagboken.ui.components.DateField
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.EntityDetailScreen
import se.partee71.dagboken.ui.components.InfoPill
import se.partee71.dagboken.ui.components.ItemRow
import se.partee71.dagboken.ui.components.SectionHeader
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Sjukdomsdetaljen för episoden [id] – underskärm från Dagbok (HIST-9) och Idags pågående sjukdom (HEM-12).
 * [onEdit] öppnar episodens formulär (SJ-12), [onCheckin] en incheckning (`null` = ny, SJ-2) eller en befintlig
 * (SJ-11); [onBack] också när episoden raderats.
 */
@Composable
fun IllnessDetailRoute(id: String, onBack: () -> Unit, onEdit: () -> Unit, onCheckin: (checkinId: String?) -> Unit) {
    val viewModel = hiltViewModel<IllnessDetailViewModel, IllnessDetailViewModel.Factory> { it.create(id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()
    LaunchedEffect(closed) { if (closed) onBack() }
    IllnessDetailScreen(state, prompt, failure, viewModel::onEvent, onBack, onEdit, onCheckin)
}

/**
 * Sjukdomsdetaljen på `EntityDetailScreen` (SJ-4, SJ-5, SJ-9, SJ-10, SJ-12, SJ-13): episodens namn i toppraden med
 * Redigera och menyn (Radera), huvudet med "Pågår"/"Avslutad", start, slut, varaktighet, senaste svårighet och
 * anteckningen; "Ny incheckning" och "Avsluta episod" medan den pågår; incheckningarna som postkort (NFR-15/16) med
 * "Dag N", eller ett tomt tillstånd.
 */
@Composable
fun IllnessDetailScreen(
    state: DetailUiState<IllnessDetail>,
    prompt: IllnessPrompt?,
    failure: Failure?,
    onEvent: (IllnessDetailEvent) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCheckin: (checkinId: String?) -> Unit,
) {
    val loaded = (state as? DetailUiState.Content)?.value
    val title = loaded?.summary?.episode?.title() ?: stringResource(R.string.log_illness)
    val delete = stringResource(R.string.delete)
    // Under en radering finns inga åtgärder – inget som skriver till episoden som försvinner (SJ-9).
    val busy = loaded?.deleting == true
    EntityDetailScreen(
        state = state,
        header = null,
        onBack = onBack,
        title = title,
        onEdit = onEdit.takeUnless { busy },
        menu = { detail -> if (detail.deleting) emptyList() else listOf(AppMenuItem(delete, { onEvent(IllnessDetailEvent.Delete) }, R.drawable.ic_delete, destructive = true)) },
        onRetry = { onEvent(IllnessDetailEvent.Retry) },
        failure = failure,
        onErrorShown = { onEvent(IllnessDetailEvent.ErrorShown) },
    ) { detail ->
        val summary = detail.summary
        EpisodeCard(detail)
        if (summary.ongoing && !detail.deleting) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AppButton(stringResource(R.string.checkin_new), { onCheckin(null) }, icon = R.drawable.ic_add)
                // SJ-4: en episod som börjar efter idag går inte att avsluta.
                if (detail.canFinish) AppButton(stringResource(R.string.illness_finish), { onEvent(IllnessDetailEvent.Finish) }, variant = ButtonVariant.Secondary)
            }
        }
        val checkins = summary.checkins
        SectionHeader(stringResource(R.string.illness_checkins), count = checkins.size.takeIf { it > 0 }?.toString(), tone = Tone.Neutral)
        if (checkins.isEmpty()) {
            EmptyState(R.drawable.ic_thermometer, stringResource(R.string.illness_checkins_empty_title), stringResource(R.string.illness_checkins_empty_message))
        }
        checkins.forEach { checkin -> key(checkin.id) { CheckinCard(checkin, detail, title, onEvent, onCheckin) } }
        Prompt(prompt, detail, title, onEvent)
    }
}

/** Huvudet (SJ-4, SJ-5): status, start, slut (bara en avslutad), varaktighet i dagar, senaste svårighet och anteckningen. */
@Composable
private fun EpisodeCard(detail: IllnessDetail) {
    val summary = detail.summary
    val episode = summary.episode
    val missing = stringResource(R.string.value_missing)
    val latest = summary.checkins.firstOrNull()
    AppCard {
        InfoPill(stringResource(if (summary.ongoing) R.string.illness_ongoing else R.string.illness_ended), tone = if (summary.ongoing) Tone.Warning else Tone.Neutral)
        Fact(stringResource(R.string.illness_started), episode.start?.let(DateFormat::display) ?: missing)
        episode.end?.let { Fact(stringResource(R.string.illness_ended_on), DateFormat.display(it)) }
        Fact(stringResource(R.string.event_duration), summary.durationDays?.let { pluralStringResource(R.plurals.prescription_days_count, it, it) } ?: missing)
        val severity = summary.latestSeverity?.let { value ->
            latest?.date?.let { stringResource(R.string.illness_latest_severity_format, value, DateFormat.weekdayDay(it)) } ?: value.toString()
        }
        Fact(stringResource(R.string.illness_latest_severity), severity ?: missing)
        episode.note.nonBlank()?.let { Text(it, style = AppTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** En rad i huvudet: etiketten och värdet, med avdelare ovanför. */
@Composable
private fun Fact(label: String, value: String) {
    AppDivider()
    ItemRow(label, trailing = { Text(value, style = AppTypography.itemTitle) })
}

/** En incheckning som postkort (SJ-11, SJ-13, NFR-15/16): dagen, klockslag · svårighet · symptom, "Dag N" och anteckningsikonen. */
@Composable
private fun CheckinCard(checkin: Checkin, detail: IllnessDetail, episodeTitle: String, onEvent: (IllnessDetailEvent) -> Unit, onCheckin: (String?) -> Unit) {
    val resources = LocalResources.current
    val busy = detail.deleting
    DagbokenEntryCard(
        title = checkin.date?.let { DateFormat.weekdayDay(it, capitalized = true) } ?: stringResource(R.string.diary_checkin),
        onClick = { if (!busy) onCheckin(checkin.id) },
        subtitle = checkinSubtitle(checkin, detail.symptomNames, resources),
        status = detail.summary.dayOf(checkin)?.let { day -> { InfoPill(stringResource(R.string.today_illness_day_format, day), tone = Tone.Warning) } },
        note = checkin.note.orEmpty(),
        onEdit = if (busy) null else ({ onCheckin(checkin.id) }),
        delete = if (busy) null else entryDeleteAction(R.string.diary_subject_checkin, episodeTitle, checkin.date, checkin.time) { onEvent(IllnessDetailEvent.DeleteCheckin(checkin.id)) },
    )
}

/** "09:00 · Svårighet 3 · Snuva 3 · Hosta 4" – klockslaget först (som Dagbok, HIST-7), symptomen med namn ur Listor. */
private fun checkinSubtitle(checkin: Checkin, names: Map<String, String>, resources: Resources): String {
    val symptoms = checkin.symptoms.mapNotNull { score ->
        (score.customText.nonBlank() ?: names[score.optionId])?.let { name -> score.score?.let { resources.getString(R.string.occasion_value_format, name, it) } ?: name }
    }
    val parts = listOfNotNull(checkin.time?.let(DateFormat::time), resources.getString(R.string.diary_severity_format, checkin.severity)) + symptoms
    return parts.reduce { text, part -> resources.getString(R.string.diary_subtitle_format, text, part) }
}

/** Frågan som är öppen: avsluta med slutdatum (SJ-4) eller radera med antalet incheckningar (SJ-9). */
@Composable
private fun Prompt(prompt: IllnessPrompt?, detail: IllnessDetail, title: String, onEvent: (IllnessDetailEvent) -> Unit) {
    val dismiss = { onEvent(IllnessDetailEvent.DismissPrompt) }
    when (prompt) {
        is IllnessPrompt.Finish -> ConfirmDialog(
            stringResource(R.string.illness_finish_title),
            stringResource(R.string.illness_finish_message, title),
            stringResource(R.string.illness_finish_confirm),
            onConfirm = { onEvent(IllnessDetailEvent.ConfirmFinish) },
            onDismiss = dismiss,
        ) {
            DateField(
                stringResource(R.string.prescription_end),
                prompt.end,
                { onEvent(IllnessDetailEvent.FinishDate(it)) },
                error = prompt.error?.let { stringResource(it.message) },
            )
        }
        is IllnessPrompt.Delete -> {
            val episode = detail.summary.episode
            val subject = episode.start?.let { stringResource(R.string.illness_delete_subject_format, title, periodText(it, episode.end ?: detail.today)) } ?: title
            val message = if (prompt.checkins == 0) {
                stringResource(R.string.illness_delete_message_none, subject)
            } else {
                pluralStringResource(R.plurals.illness_delete_message, prompt.checkins, subject, prompt.checkins)
            }
            ConfirmDialog(
                stringResource(R.string.illness_delete_title),
                message,
                stringResource(R.string.delete),
                onConfirm = { onEvent(IllnessDetailEvent.ConfirmDelete) },
                onDismiss = dismiss,
                destructive = true,
            )
        }
        null -> Unit
    }
}
