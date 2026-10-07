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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.DayPart
import se.partee71.dagboken.core.engine.Due
import se.partee71.dagboken.core.engine.EnergyTrend
import se.partee71.dagboken.core.engine.OccasionStatus
import se.partee71.dagboken.core.engine.OngoingIllness
import se.partee71.dagboken.core.engine.WeekSummary
import se.partee71.dagboken.core.engine.latest
import se.partee71.dagboken.core.engine.OpenDose
import se.partee71.dagboken.core.engine.boostEnd
import se.partee71.dagboken.core.engine.boostFor
import se.partee71.dagboken.core.engine.doseFor
import se.partee71.dagboken.core.engine.isPrescribed
import se.partee71.dagboken.core.engine.isScheduled
import se.partee71.dagboken.core.engine.isScheduledPrescription
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.title
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.doseText
import se.partee71.dagboken.ui.common.entryDeleteAction
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
import se.partee71.dagboken.ui.components.DeleteConfirmDialog
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.EntityDetailScreen
import se.partee71.dagboken.ui.components.InfoPill
import se.partee71.dagboken.ui.components.ItemRow
import se.partee71.dagboken.ui.components.inactive
import se.partee71.dagboken.ui.components.MessageSnackbar
import se.partee71.dagboken.ui.components.NoticeBanner
import se.partee71.dagboken.ui.components.ProgressBar
import se.partee71.dagboken.ui.components.SectionHeader
import se.partee71.dagboken.ui.components.StatPill
import se.partee71.dagboken.ui.components.UndoRequest
import se.partee71.dagboken.ui.components.UndoSnackbar
import se.partee71.dagboken.ui.health.HealthEvent
import se.partee71.dagboken.ui.health.HealthTodayCard
import se.partee71.dagboken.ui.health.HealthTodayUiState
import se.partee71.dagboken.ui.health.HealthTodayViewModel
import se.partee71.dagboken.ui.health.WeekTrendsCard
import se.partee71.dagboken.ui.log.CooldownDialog
import se.partee71.dagboken.ui.log.CooldownPrompt
import se.partee71.dagboken.ui.log.LogEvent
import se.partee71.dagboken.ui.log.OccasionStateRow
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

@Composable
fun TodayRoute(
    account: AuthUser?,
    onAccount: () -> Unit,
    onEditPrn: (String) -> Unit,
    onOpenTrends: () -> Unit,
    onScreening: (LogEvent) -> Unit,
    onLogLater: (prnId: String, date: LocalDate) -> Unit = { _, _ -> },
    onOpenIllness: (episodeId: String, date: LocalDate) -> Unit = { _, _ -> },
    viewModel: TodayViewModel = hiltViewModel(),
    healthViewModel: HealthTodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val health by healthViewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val cooldown by viewModel.cooldown.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    TodayScreen(
        state,
        viewModel::onEvent,
        onEditPrn,
        failure = failure,
        undo = undo,
        cooldown = cooldown,
        notice = notice,
        onOpenTrends = onOpenTrends,
        onScreening = onScreening,
        onLogLater = onLogLater,
        onOpenIllness = onOpenIllness,
        health = health,
        onHealthEvent = healthViewModel::onEvent,
    ) {
        AccountAvatar(account?.name ?: account?.email, onAccount, photoUrl = account?.photoUrl)
    }
}

/**
 * Fliken Idag (HEM-16) på `EntityDetailScreen` i flikläge: hälsningen och den visade dagen överst med
 * avataren (HEM-1, HEM-2, NAV-9), datumremsan (HEM-14), framstegsraden (HEM-18), i belöningsläget kortet
 * "Allt klart för idag" med konfetti (HEM-19) och sedan korten Mediciner (MED-1–3, MED-5, MED-13) och Vid
 * behov (FAV-2, FAV-11) med Mående emellan (HEM-4, HEM-5), sedan pågående sjukdom (HEM-12), hälsokortet (HEM-15,
 * [health] ur `HealthTodayViewModel`, `null` = ingen klocka) och "Senaste veckan" med steg-, vilopuls- och energitrenden
 * (HEM-17, HEM-7). En söndag eller måndag står "Din vecka" överst (HEM-13). [onHealthEvent] tar hälsokortets "Ge åtkomst".
 * Ångra och bekräftelserna visas i ramens meddelandeyta. [onScreening] öppnar måendearket – samma ark som plusknappen
 * (`LogViewModel`, ovanpå flikarna) – med en ny logg eller en loggad för ändring. [onEditPrn] öppnar vid
 * behov-formuläret från långtrycksmenyn (HEM-11), [onLogLater] dosformuläret för medicinen i efterhand mot den visade
 * dagen (FAV-10, MED-16), [onOpenIllness] sjukdomsdetaljen för den pågående sjukdomen med den visade dagen (HEM-12, HEM-14) och [onOpenTrends]
 * fliken Trender (TRD-5).
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
    onOpenTrends: () -> Unit = {},
    onScreening: (LogEvent) -> Unit = {},
    onLogLater: (prnId: String, date: LocalDate) -> Unit = { _, _ -> },
    onOpenIllness: (episodeId: String, date: LocalDate) -> Unit = { _, _ -> },
    health: HealthTodayUiState? = null,
    onHealthEvent: (HealthEvent) -> Unit = {},
    avatar: @Composable () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    UndoSnackbar(undo, snackbar, { onEvent(TodayEvent.Undo) }, {}, onRequestDismissed = { onEvent(TodayEvent.UndoDismissed(it.id)) })
    MessageSnackbar(notice?.let { stringResource(it.text, *it.args) }, snackbar, key = notice) { onEvent(TodayEvent.NoticeShown) }
    cooldown?.let { CooldownDialog(it, { onEvent(TodayEvent.ConfirmCooldown) }, { onEvent(TodayEvent.DismissCooldown) }) }
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
    ) { TodayCards(it, onEvent, TodayLinks(onEditPrn, onOpenTrends, onScreening, onLogLater, onOpenIllness), health, onHealthEvent) }
}

/** Det Idag öppnar utanför fliken – formulär, arket och Trender. */
private class TodayLinks(
    val onEditPrn: (String) -> Unit,
    val onOpenTrends: () -> Unit,
    val onScreening: (LogEvent) -> Unit,
    val onLogLater: (String, LocalDate) -> Unit,
    val onOpenIllness: (String, LocalDate) -> Unit,
)

@Composable
private fun TodayCards(content: TodayContent, onEvent: (TodayEvent) -> Unit, links: TodayLinks, health: HealthTodayUiState?, onHealthEvent: (HealthEvent) -> Unit) {
    content.weekSummary?.let { WeekSummaryCard(it) }
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
    MoodCard(content, links.onScreening)
    AsNeededCard(content, onEvent, links)
    content.illness?.let { illness -> IllnessCard(illness) { links.onOpenIllness(illness.episode.id, content.date) } }
    health?.let { HealthTodayCard(it, onHealthEvent) }
    WeekTrendsCard(content.energyDays, content.energy, health, links.onOpenTrends)
}

/** "Din vecka" (HEM-13): energin mot förra veckan ("Energi · Uppåt") och andelen tagna doser – utan doser bara energin. */
@Composable
private fun WeekSummaryCard(summary: WeekSummary) {
    AppCard {
        SectionHeader(stringResource(R.string.today_week_title), icon = R.drawable.ic_calendar)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val (trend, tone) = when (summary.energyTrend) {
                EnergyTrend.UP -> R.string.today_week_energy_up to Tone.Positive
                EnergyTrend.DOWN -> R.string.today_week_energy_down to Tone.Warning
                EnergyTrend.SAME -> R.string.today_week_energy_same to Tone.Neutral
            }
            StatPill(R.drawable.ic_trend, stringResource(trend), stringResource(R.string.energy), Modifier.weight(1f), tone = tone)
            summary.dosesTakenPercent?.let { percent ->
                StatPill(R.drawable.ic_pill, stringResource(R.string.today_week_doses_format, percent), stringResource(R.string.today_week_doses), Modifier.weight(1f))
            }
        }
    }
}

/**
 * Mående (HEM-4, HEM-5, NOT-4): en tillfällesrad per aktiverat tillfälle med status och räknaren loggade av
 * tillfällena. Hela raden är radens åtgärd (NFR-17): ett loggat tillfälle öppnar loggen för ändring, annars
 * öppnar raden – liksom "Logga nu" – arket med en ny logg för den visade dagen (SCR-6). Utan aktiverade
 * tillfällen visas inget kort.
 */
@Composable
private fun MoodCard(content: TodayContent, onScreening: (LogEvent) -> Unit) {
    val states = content.occasions
    if (states.isEmpty()) return
    AppCard {
        val logged = states.count { it.status == OccasionStatus.LOGGED }
        SectionHeader(
            stringResource(R.string.log_mood),
            count = stringResource(R.string.today_count_format, logged, states.size),
            tone = if (content.isToday && logged == states.size) Tone.Positive else Tone.Primary,
        )
        states.forEach { state ->
            key(state.occasion) {
                // Idag är dagen klockans när arket öppnas (`null`); en tidigare dag får tillfällets påminnelsetid (SCR-6).
                val log = { onScreening(LogEvent.LogScreening(state.occasion, content.date.takeUnless { content.isToday }, state.time)) }
                val latest = state.latest
                OccasionStateRow(state, onLog = log, onClick = if (latest != null) ({ onScreening(LogEvent.EditScreening(latest)) }) else log)
            }
        }
    }
}

/**
 * Pågående sjukdom (HEM-12): typen, "Dag N" och senaste incheckningen, med vänsterkanten i varningston
 * (status, NFR-16). Raden öppnar sjukdomsdetaljen ([onOpen], SJ-13), där man checkar in (SJ-2).
 */
@Composable
private fun IllnessCard(illness: OngoingIllness, onOpen: () -> Unit) {
    AppCard {
        SectionHeader(stringResource(R.string.today_illness_title), icon = R.drawable.ic_thermometer, tone = Tone.Warning)
        val checkin = illness.lastCheckin
        val checkinDate = checkin?.date
        ItemRow(
            title = illness.episode.title(),
            subtitle = when {
                checkin == null -> stringResource(R.string.today_illness_no_checkin)
                checkinDate == null -> stringResource(R.string.today_illness_checkin_undated)
                else -> stringResource(R.string.today_illness_checkin_format, DateFormat.short(checkinDate))
            },
            trailing = illness.day?.let { day -> { InfoPill(stringResource(R.string.today_illness_day_format, day), tone = Tone.Warning) } },
            accent = AppColors.tone(Tone.Warning).content,
            onClick = onOpen,
            navigates = true,
        )
    }
}

/**
 * Mediciner (MED-1, MED-5, MED-13): det som är att göra, sedan kommande och tagna när de visas, och
 * knapparna som visar och döljer dem med antal. Hela raden växlar tagen; "Hoppa över" i radens meny (MED-3).
 */
@Composable
private fun MedicinesCard(content: TodayContent, onEvent: (TodayEvent) -> Unit) {
    var skipping by remember { mutableStateOf<Dose?>(null) }
    var deleting by remember { mutableStateOf<Dose?>(null) }
    val list = content.checklist
    AppCard {
        val doses = content.doseProgress
        SectionHeader(
            stringResource(R.string.tab_medicines),
            count = if (doses.total > 0) stringResource(R.string.today_count_format, doses.done, doses.total) else null,
            tone = if (content.isToday && doses.isComplete) Tone.Positive else Tone.Primary,
        )
        val rows = list.shown + (if (content.showUpcoming) list.upcoming else emptyList()) +
            (if (content.showDone) list.done.map { OpenDose(it, null) } else emptyList())
        // Radens tillstånd (meny, anteckning, krysset) följer dosen när listan ändras.
        rows.forEach { row -> key(row.dose.id) { DoseRow(row, content, onEvent, onSkip = { skipping = it }, onDelete = { deleting = it }) } }
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
    deleting?.let { dose ->
        val close = { deleting = null }
        val action = entryDeleteAction(
            R.string.diary_subject_dose,
            medicineTitle(dose.name, dose.dose, dose.unit),
            dose.date,
            dose.takenAt?.toLocalDateTime(content.zone)?.time,
            // Samma beslut som `DoseRepository.remove`: en receptdos hoppas över (MED-15).
            skips = dose.isPrescribed,
        ) { onEvent(TodayEvent.Delete(dose)) }
        DeleteConfirmDialog(action, close)
    }
}

/**
 * En dos: tidpunkt, klockslag eller tagningstid och dagens höjning (REC-12) som undertext; "Försenat"/"Snart" (MED-13).
 * Menyn: "Hoppa över" på en planerad receptdos i schemat, "Radera" på en vid behov-, extra- eller engångsdos (MED-3) –
 * med samma text som `DoseRepository.remove` gör (en migrerad receptdos hoppas över, MED-15).
 */
@Composable
private fun DoseRow(item: OpenDose, content: TodayContent, onEvent: (TodayEvent) -> Unit, onSkip: (Dose) -> Unit, onDelete: (Dose) -> Unit) {
    val dose = item.dose
    val skip = stringResource(R.string.today_skip)
    val delete = stringResource(R.string.delete)
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
        // En loggad vid behov- eller extrados visas som tagen men växlas inte – den raderas i menyn (eller ångras).
        enabled = dose.isScheduled,
        menu = when {
            // MED-3: en receptdos i schemat hoppas över; allt annat – vid behov, extrados, engångsdos – raderas.
            !dose.isScheduledPrescription -> listOf(AppMenuItem(delete, { onDelete(dose) }, R.drawable.ic_delete, destructive = true))
            dose.status == DoseStatus.PLANNED -> listOf(AppMenuItem(skip, { onSkip(dose) }, R.drawable.ic_close))
            else -> emptyList()
        },
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
 * Vid behov (FAV-2, FAV-11): favoriterna som chips – tryck loggar, långtryck ger Redigera, Favorit, Logga i
 * efterhand, Visa anteckning och Radera (HEM-11, FAV-3, FAV-8, FAV-9, FAV-10) – och "Fler" med övriga vid
 * behov-mediciner och receptens under "Recept". Utan vid behov-mediciner och recept visas inget kort.
 */
@Composable
private fun AsNeededCard(content: TodayContent, onEvent: (TodayEvent) -> Unit, links: TodayLinks) {
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
                    val items = medicineMenu(
                        medicine,
                        onEvent,
                        onEdit = { links.onEditPrn(medicine.id) },
                        onLogLater = { links.onLogLater(medicine.id, content.date) },
                        onNote = { noteOf = medicine },
                    ) { deleting = medicine }
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

/** Långtrycksmenyn på ett snabbval i ordningen Redigera, Favorit, Logga i efterhand, Visa anteckning, Radera sist (NFR-16, HEM-11). */
@Composable
private fun medicineMenu(
    medicine: PrnMedicine,
    onEvent: (TodayEvent) -> Unit,
    onEdit: () -> Unit,
    onLogLater: () -> Unit,
    onNote: () -> Unit,
    onDelete: () -> Unit,
): List<AppMenuItem> =
    listOfNotNull(
        AppMenuItem(stringResource(R.string.edit), onEdit, R.drawable.ic_edit),
        AppMenuItem(
            stringResource(if (medicine.favorite) R.string.favorite_remove else R.string.favorite_add, medicine.name),
            { onEvent(TodayEvent.ToggleFavorite(medicine)) },
            // Samma ikon som stjärnan i Mediciner (FavoriteStar): fylld = favorit.
            if (medicine.favorite) R.drawable.ic_star_filled else R.drawable.ic_star,
        ),
        AppMenuItem(stringResource(R.string.dose_log_later), onLogLater, R.drawable.ic_clock),
        if (medicine.note.isNullOrBlank()) null else AppMenuItem(stringResource(R.string.note_show), onNote, R.drawable.ic_note),
        AppMenuItem(stringResource(R.string.delete), onDelete, R.drawable.ic_delete, destructive = true),
    )

private fun DayPart.greeting(): Int = when (this) {
    DayPart.MORNING -> R.string.greeting_morning
    DayPart.AFTERNOON -> R.string.greeting_afternoon
    DayPart.EVENING -> R.string.greeting_evening
    DayPart.NIGHT -> R.string.greeting_night
}
