package se.partee71.dagboken.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.DayPart
import se.partee71.dagboken.core.engine.Due
import se.partee71.dagboken.core.engine.OpenDose
import se.partee71.dagboken.core.engine.boostEnd
import se.partee71.dagboken.core.engine.boostFor
import se.partee71.dagboken.core.engine.doseFor
import se.partee71.dagboken.core.engine.isScheduled
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.doseText
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.common.medicineTitle
import se.partee71.dagboken.ui.common.periodText
import se.partee71.dagboken.ui.components.AccountAvatar
import se.partee71.dagboken.ui.components.AppButton
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppFilterChip
import se.partee71.dagboken.ui.components.AppMenuItem
import se.partee71.dagboken.ui.components.AppMenuPopup
import se.partee71.dagboken.ui.components.ButtonVariant
import se.partee71.dagboken.ui.components.CheckRow
import se.partee71.dagboken.ui.components.ChipRow
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.DateStrip
import se.partee71.dagboken.ui.components.DayDoneCard
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.EntityDetailScreen
import se.partee71.dagboken.ui.components.InfoPill
import se.partee71.dagboken.ui.components.inactive
import se.partee71.dagboken.ui.components.MessageSnackbar
import se.partee71.dagboken.ui.components.NoticeBanner
import se.partee71.dagboken.ui.components.ProgressBar
import se.partee71.dagboken.ui.components.SectionHeader
import se.partee71.dagboken.ui.components.UndoRequest
import se.partee71.dagboken.ui.components.UndoSnackbar
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

@Composable
fun TodayRoute(
    account: AuthUser?,
    onAccount: () -> Unit,
    onEditPrn: (String) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val cooldown by viewModel.cooldown.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    TodayScreen(state, viewModel::onEvent, onEditPrn, failure = failure, undo = undo, cooldown = cooldown, notice = notice) {
        AccountAvatar(account?.name ?: account?.email, onAccount, photoUrl = account?.photoUrl)
    }
}

/**
 * Fliken Idag (HEM-16) på `EntityDetailScreen` i flikläge: hälsningen och den visade dagen överst med
 * avataren (HEM-1, HEM-2, NAV-9), datumremsan (HEM-14), framstegsraden (HEM-18), i belöningsläget kortet
 * "Allt klart för idag" med konfetti (HEM-19) och sedan korten Mediciner (MED-1–3, MED-5, MED-13) och Vid
 * behov (FAV-2, FAV-11). Mående, sjukdom, Hälsa idag och trenden kommer i den ordningen under korten (HEM-16).
 * Ångra och bekräftelserna visas i ramens meddelandeyta. [onEditPrn] öppnar vid behov-formuläret från
 * långtrycksmenyn (HEM-11).
 */
@Composable
fun TodayScreen(
    state: DetailUiState<TodayContent>,
    onEvent: (TodayEvent) -> Unit,
    onEditPrn: (String) -> Unit,
    modifier: Modifier = Modifier,
    failure: Failure? = null,
    undo: UndoRequest? = null,
    cooldown: CooldownPrompt? = null,
    notice: TodayNotice? = null,
    avatar: @Composable () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    UndoSnackbar(undo, snackbar, { onEvent(TodayEvent.Undo) }, {}, onRequestDismissed = { onEvent(TodayEvent.UndoDismissed(it.id)) })
    MessageSnackbar(notice?.let { stringResource(it.text, *it.args) }, snackbar, key = notice) { onEvent(TodayEvent.NoticeShown) }
    cooldown?.let { CooldownDialog(it, onEvent) }
    val content = (state as? DetailUiState.Content)?.value
    EntityDetailScreen(
        state = state,
        header = null,
        onBack = null,
        modifier = modifier,
        title = when {
            content == null -> stringResource(R.string.tab_today)
            content.dayDone != null -> stringResource(R.string.day_done_title)
            else -> stringResource(content.dayPart.greeting())
        },
        subtitle = content?.let { stringResource(R.string.today_heading_format, DateFormat.dayAndMonth(it.date), DateFormat.isoWeek(it.date)) },
        onRetry = { onEvent(TodayEvent.Retry) },
        failure = failure,
        onErrorShown = { onEvent(TodayEvent.ErrorShown) },
        actions = { avatar() },
        snackbar = snackbar,
    ) { TodayCards(it, onEvent, onEditPrn) }
}

@Composable
private fun TodayCards(content: TodayContent, onEvent: (TodayEvent) -> Unit, onEditPrn: (String) -> Unit) {
    DateStrip(
        week = content.week,
        selectedDate = content.date,
        onSelect = { onEvent(TodayEvent.SelectDate(it)) },
        onWeekChange = { onEvent(TodayEvent.ShowWeek(it)) },
        today = content.today,
        datesWithEntries = content.datesWithEntries,
        todayDone = content.todayComplete,
    )
    if (content.progress.total > 0) ProgressBar(content.progress.done, content.progress.total, celebrate = content.isToday)
    content.dayDone?.let { done ->
        DayDoneCard(
            averageEnergy = done.comparison?.average?.toDouble(),
            versusYesterday = done.comparison?.changeFromYesterday?.toDouble(),
            streakDays = done.streak,
            play = done.playConfetti,
            onConfettiFinished = { onEvent(TodayEvent.ConfettiShown) },
        )
    }
    MedicinesCard(content, onEvent)
    AsNeededCard(content, onEvent, onEditPrn)
}

/**
 * Mediciner (MED-1, MED-5, MED-13): det som är att göra, sedan kommande och tagna när de visas, och
 * knapparna som visar och döljer dem med antal. Hela raden växlar tagen; "Hoppa över" i radens meny (MED-3).
 */
@Composable
private fun MedicinesCard(content: TodayContent, onEvent: (TodayEvent) -> Unit) {
    var skipping by remember { mutableStateOf<Dose?>(null) }
    val list = content.checklist
    AppCard {
        val doses = content.doseProgress
        SectionHeader(
            stringResource(R.string.tab_medicines),
            count = if (doses.total > 0) stringResource(R.string.today_medicines_count_format, doses.done, doses.total) else null,
            tone = if (content.isToday && doses.isComplete) Tone.Positive else Tone.Primary,
        )
        val rows = list.shown + (if (content.showUpcoming) list.upcoming else emptyList()) +
            (if (content.showDone) list.done.map { OpenDose(it, null) } else emptyList())
        // Radens tillstånd (meny, anteckning, krysset) följer dosen när listan ändras.
        rows.forEach { row -> key(row.dose.id) { DoseRow(row, content, onEvent) { skipping = it } } }
        when {
            list.isEmpty -> EmptyState(R.drawable.ic_pill, stringResource(R.string.today_medicines_none_title), stringResource(R.string.today_medicines_none), compact = true)
            list.shown.isEmpty() && list.upcoming.isEmpty() && !content.showDone ->
                EmptyState(R.drawable.ic_check, stringResource(R.string.today_medicines_all_done_title), stringResource(R.string.today_medicines_all_done), compact = true)
            // Allt som återstår ligger mer än tre timmar fram och är dolt (MED-13): säg när nästa dos är.
            rows.isEmpty() && list.upcoming.isNotEmpty() -> list.upcoming.first().dose.let { next ->
                EmptyState(
                    R.drawable.ic_clock,
                    stringResource(R.string.today_next_dose_title),
                    stringResource(R.string.today_next_dose_format, DateFormat.time(next.plannedTime ?: next.slot.defaultTime)),
                    compact = true,
                )
            }
        }
        if (list.upcoming.isNotEmpty() || list.done.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (list.upcoming.isNotEmpty()) {
                    val label = if (content.showUpcoming) stringResource(R.string.today_hide_upcoming) else stringResource(R.string.today_show_upcoming_format, list.upcoming.size)
                    AppButton(label, { onEvent(TodayEvent.ToggleUpcoming) }, variant = ButtonVariant.Text, compact = true)
                }
                if (list.done.isNotEmpty()) {
                    val label = if (content.showDone) stringResource(R.string.today_hide_done) else stringResource(R.string.today_show_done_format, list.done.size)
                    AppButton(label, { onEvent(TodayEvent.ToggleDone) }, variant = ButtonVariant.Text, compact = true)
                }
            }
        }
    }
    skipping?.let { dose ->
        val close = { skipping = null }
        ConfirmDialog(
            stringResource(R.string.today_skip_title),
            dose.date.let { day ->
                val title = medicineTitle(dose.name, dose.dose, dose.unit)
                if (day == null || day == content.today) stringResource(R.string.today_skip_message, title) else stringResource(R.string.today_skip_message_day, title, DateFormat.display(day))
            },
            stringResource(R.string.today_skip),
            onConfirm = { close(); onEvent(TodayEvent.Skip(dose)) },
            onDismiss = close,
        )
    }
}

/** En dos: tidpunkt, klockslag eller tagningstid och dagens höjning (REC-12) som undertext; "Försenat"/"Snart" (MED-13). */
@Composable
private fun DoseRow(item: OpenDose, content: TodayContent, onEvent: (TodayEvent) -> Unit, onSkip: (Dose) -> Unit) {
    val dose = item.dose
    val skip = stringResource(R.string.today_skip)
    CheckRow(
        title = medicineTitle(dose.name, dose.dose, dose.unit),
        checked = dose.status == DoseStatus.TAKEN,
        onCheckedChange = { onEvent(TodayEvent.SetTaken(dose, it)) },
        subtitle = doseSubtitle(dose, dose.prescriptionId?.let(content.prescriptions::get), content.date, content.zone),
        below = when (item.due) {
            Due.LATE -> ({ InfoPill(stringResource(R.string.occasion_late), tone = Tone.Warning) })
            Due.SOON -> ({ InfoPill(stringResource(R.string.occasion_soon), tone = Tone.Sun) })
            else -> null
        },
        note = dose.note.orEmpty(),
        // En loggad vid behov- eller extrados visas som tagen men växlas inte – den tas bort i dosformuläret (#239).
        enabled = dose.isScheduled,
        menu = if (dose.status == DoseStatus.PLANNED && dose.isScheduled) listOf(AppMenuItem(skip, { onSkip(dose) }, R.drawable.ic_close)) else emptyList(),
    )
}

/** "Morgon · tagen 07:12 · 50 mg + 25 mg höjning t.o.m. 12 okt" – det som saknas utelämnas. */
@Composable
private fun doseSubtitle(dose: Dose, prescription: Prescription?, date: LocalDate, zone: TimeZone): String {
    val time = when (dose.status) {
        DoseStatus.TAKEN -> dose.takenAt?.let { stringResource(R.string.today_dose_taken_format, DateFormat.time(it.toLocalDateTime(zone).time)) }
        DoseStatus.SKIPPED -> stringResource(R.string.today_dose_skipped)
        DoseStatus.PLANNED -> dose.plannedTime?.let(DateFormat::time)
    }
    val boost = prescription?.boostFor(date)?.let { boost ->
        stringResource(R.string.today_dose_boost_format, doseText(prescription.dose, prescription.unit), doseText(boost.dose, prescription.unit), periodText(null, prescription.boostEnd(boost)))
    }
    return listOfNotNull(stringResource(dose.slot.label()), time, boost).joinToString(" · ")
}

/**
 * Vid behov (FAV-2, FAV-11): favoriterna som chips – tryck loggar, långtryck ger Redigera, Favorit, Visa
 * anteckning och Radera (HEM-11, FAV-3, FAV-8, FAV-9) – och "Fler" med övriga vid behov-mediciner och
 * receptens under "Recept". Utan vid behov-mediciner och recept visas inget kort.
 */
@Composable
private fun AsNeededCard(content: TodayContent, onEvent: (TodayEvent) -> Unit, onEditPrn: (String) -> Unit) {
    val choices = content.choices
    if (choices.favorites.isEmpty() && choices.moreCount == 0) return
    var menuFor by remember { mutableStateOf<String?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PrnMedicine?>(null) }
    var noteOf by remember { mutableStateOf<PrnMedicine?>(null) }
    val canLog = content.isToday
    val todayOnly = stringResource(R.string.today_prn_today_only)
    // En tidigare dag: chipen är tonade och trycket gör ingenting, men de är aktiva så att långtrycksmenyn
    // nås också med TalkBack (ett inaktivt chip får ingen långtrycksåtgärd).
    val pastDay = if (canLog) Modifier else Modifier.inactive(true).semantics { stateDescription = todayOnly }
    AppCard {
        SectionHeader(stringResource(R.string.medicines_as_needed))
        if (!canLog) {
            NoticeBanner(
                stringResource(R.string.today_prn_past_day),
                R.drawable.ic_info,
                { onEvent(TodayEvent.SelectDate(content.today)) },
                tone = Tone.Neutral,
                onClickLabel = stringResource(R.string.today_show_today),
            )
        }
        ChipRow {
            choices.favorites.forEach { medicine ->
                Box {
                    AppFilterChip(
                        medicineTitle(medicine.name, medicine.dose, medicine.unit),
                        selected = false,
                        onClick = { if (canLog) onEvent(TodayEvent.LogAsNeeded(medicine)) },
                        modifier = pastDay,
                        icon = if (medicine.note.isNullOrBlank()) null else R.drawable.ic_note,
                        onLongClick = { menuFor = medicine.id },
                        onLongClickLabel = stringResource(R.string.more_options),
                    )
                    val items = medicineMenu(medicine, onEvent, { onEditPrn(medicine.id) }, { noteOf = medicine }) { deleting = medicine }
                    AppMenuPopup(items, expanded = menuFor == medicine.id, onDismiss = { menuFor = null })
                }
            }
            if (choices.moreCount > 0) {
                Box {
                    AppFilterChip(
                        stringResource(R.string.today_more_format, choices.moreCount),
                        selected = false,
                        onClick = { if (canLog) moreOpen = true },
                        modifier = pastDay,
                        icon = R.drawable.ic_expand_more,
                    )
                    val recipes = stringResource(R.string.today_more_prescriptions)
                    val items = choices.others.map { AppMenuItem(medicineTitle(it.name, it.dose, it.unit), { onEvent(TodayEvent.LogAsNeeded(it)) }) } +
                        choices.prescriptions.map { AppMenuItem(medicineTitle(it.name, it.doseFor(content.today), it.unit), { onEvent(TodayEvent.LogExtra(it)) }, section = recipes) }
                    AppMenuPopup(items, expanded = moreOpen, onDismiss = { moreOpen = false })
                }
            }
        }
    }
    deleting?.let { medicine ->
        val close = { deleting = null }
        ConfirmDialog(
            stringResource(R.string.delete_named_title, medicine.name),
            stringResource(R.string.prn_delete_message),
            stringResource(R.string.delete),
            onConfirm = { close(); onEvent(TodayEvent.DeleteMedicine(medicine)) },
            onDismiss = close,
            destructive = true,
        )
    }
    noteOf?.let { medicine ->
        val close = { noteOf = null }
        ConfirmDialog(medicine.name, medicine.note.orEmpty(), stringResource(R.string.close), onConfirm = close, onDismiss = close, dismissLabel = null)
    }
}

/** Långtrycksmenyn på ett snabbval i ordningen Redigera, Favorit, Visa anteckning, Radera sist (NFR-16, HEM-11). */
@Composable
private fun medicineMenu(medicine: PrnMedicine, onEvent: (TodayEvent) -> Unit, onEdit: () -> Unit, onNote: () -> Unit, onDelete: () -> Unit): List<AppMenuItem> =
    listOfNotNull(
        AppMenuItem(stringResource(R.string.edit), onEdit, R.drawable.ic_edit),
        AppMenuItem(
            stringResource(if (medicine.favorite) R.string.favorite_remove else R.string.favorite_add, medicine.name),
            { onEvent(TodayEvent.ToggleFavorite(medicine)) },
            // Samma ikon som stjärnan i Mediciner (FavoriteStar): fylld = favorit.
            if (medicine.favorite) R.drawable.ic_star_filled else R.drawable.ic_star,
        ),
        if (medicine.note.isNullOrBlank()) null else AppMenuItem(stringResource(R.string.note_show), onNote, R.drawable.ic_note),
        AppMenuItem(stringResource(R.string.delete), onDelete, R.drawable.ic_delete, destructive = true),
    )

/** "För tidigt" (FAV-4): kvarvarande tid och "Ta ändå". */
@Composable
private fun CooldownDialog(prompt: CooldownPrompt, onEvent: (TodayEvent) -> Unit) {
    val (hours, minutes) = prompt.remaining.hoursAndMinutes()
    ConfirmDialog(
        stringResource(R.string.today_cooldown_title),
        stringResource(R.string.today_cooldown_message, hours.toString(), minutes.toString(), prompt.medicine.name),
        stringResource(R.string.today_take_anyway),
        onConfirm = { onEvent(TodayEvent.ConfirmCooldown) },
        onDismiss = { onEvent(TodayEvent.DismissCooldown) },
    )
}

/** Hela timmar och påbörjade minuter – "0h 1m" hellre än "0h 0m" när några sekunder återstår. */
private fun Duration.hoursAndMinutes(): Pair<Long, Int> =
    ceil(toDouble(DurationUnit.MINUTES)).toLong().minutes.toComponents { hours, minutes, _, _ -> hours to minutes }

private fun DayPart.greeting(): Int = when (this) {
    DayPart.MORNING -> R.string.greeting_morning
    DayPart.AFTERNOON -> R.string.greeting_afternoon
    DayPart.EVENING -> R.string.greeting_evening
    DayPart.NIGHT -> R.string.greeting_night
}
