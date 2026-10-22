package se.partee71.dagboken.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.DayPart
import se.partee71.dagboken.core.engine.Due
import se.partee71.dagboken.core.engine.EnergyTrend
import se.partee71.dagboken.core.engine.FeelingDay
import se.partee71.dagboken.core.engine.OccasionState
import se.partee71.dagboken.core.engine.OccasionStatus
import se.partee71.dagboken.core.engine.OngoingIllness
import se.partee71.dagboken.core.engine.OpenDose
import se.partee71.dagboken.core.engine.WeekSummary
import se.partee71.dagboken.core.engine.boostEnd
import se.partee71.dagboken.core.engine.boostFor
import se.partee71.dagboken.core.engine.groupedByLogged
import se.partee71.dagboken.core.engine.isScheduled
import se.partee71.dagboken.core.engine.isScheduledPrescription
import se.partee71.dagboken.core.engine.latest
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.FeelingSentenceKind
import se.partee71.dagboken.ui.common.deleteAction
import se.partee71.dagboken.ui.common.doseText
import se.partee71.dagboken.ui.common.feelingSentence
import se.partee71.dagboken.ui.common.illnessDayText
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.common.periodText
import se.partee71.dagboken.ui.common.title
import se.partee71.dagboken.ui.common.toMessage
import se.partee71.dagboken.ui.components.AccountAvatar
import se.partee71.dagboken.ui.components.DateStrip
import se.partee71.dagboken.ui.components.DayDoneCard
import se.partee71.dagboken.ui.components.FeelingMiniWheel
import se.partee71.dagboken.ui.components.FeelingPills
import se.partee71.dagboken.ui.components.ProgressBar
import se.partee71.dagboken.ui.components.SECTION_GAP
import se.partee71.dagboken.ui.components.SectionFoldout
import se.partee71.dagboken.ui.components.StatPill
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
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Neutral
import se.partee71.dagboken.ui.theme.Positive
import se.partee71.dagboken.ui.theme.Sun
import se.partee71.dagboken.ui.theme.Warning
import se.partee71.ui.common.DetailUiState
import se.partee71.ui.common.UiError
import se.partee71.ui.components.AppButton
import se.partee71.ui.components.AppCard
import se.partee71.ui.components.AppMenuItem
import se.partee71.ui.components.ButtonVariant
import se.partee71.ui.components.CheckRow
import se.partee71.ui.components.ChipRow
import se.partee71.ui.components.ConfirmDialog
import se.partee71.ui.components.DeleteConfirmDialog
import se.partee71.ui.components.EmptyState
import se.partee71.ui.components.EntityDetailScreen
import se.partee71.ui.components.GroupLabel
import se.partee71.ui.components.InfoPill
import se.partee71.ui.components.ItemRow
import se.partee71.ui.components.MessageSnackbar
import se.partee71.ui.components.SectionHeader
import se.partee71.ui.components.UndoRequest
import se.partee71.ui.components.UndoSnackbar
import se.partee71.ui.theme.Spacing
import se.partee71.ui.theme.Tone

@Composable
fun TodayRoute(
    account: AuthUser?,
    onAccount: () -> Unit,
    onEditPrn: (String) -> Unit,
    onOpenTrends: () -> Unit,
    onScreening: (LogEvent) -> Unit,
    onLogLater: (prnId: String, date: LocalDate) -> Unit = { _, _ -> },
    onOpenIllness: (episodeId: String, date: LocalDate) -> Unit = { _, _ -> },
    onEditPrescription: (String) -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
    healthViewModel: HealthTodayViewModel = hiltViewModel()
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
        onEditPrescription = onEditPrescription,
        health = health,
        onHealthEvent = healthViewModel::onEvent
    ) {
        AccountAvatar(account?.name ?: account?.email, onAccount, photoUrl = account?.photoUrl)
    }
}

/**
 * Fliken Idag (HEM-16) på `EntityDetailScreen` i flikläge: hälsningen och den visade dagen överst med
 * avataren (HEM-1, HEM-2, NAV-9), datumremsan (HEM-14), framstegsraden (HEM-18), i belöningsläget kortet
 * "Allt klart för idag" med konfetti (HEM-19), Dagens känslor när dagens måendeloggar har känslor (HEM-21), och sedan korten Mediciner (MED-1–3, MED-5, MED-13) och Vid
 * behov (FAV-2, FAV-11) med Mående emellan (HEM-4, HEM-5), sedan pågående sjukdom (HEM-12), hälsokortet (HEM-15,
 * [health] ur `HealthTodayViewModel`, `null` = ingen klocka) och "Senaste veckan" med steg-, vilopuls- och energitrenden
 * (HEM-17, HEM-7). Korten från Mediciner och nedåt fälls ihop på titelraden, utfällda som standard (HEM-22). En söndag eller måndag står "Din vecka" överst (HEM-13). [onHealthEvent] tar hälsokortets "Ge åtkomst".
 * Ångra och bekräftelserna visas i ramens meddelandeyta. [onScreening] öppnar måendearket – samma ark som plusknappen
 * (`LogViewModel`, ovanpå flikarna) – med en ny logg eller en loggad för ändring. [onEditPrn] öppnar vid
 * behov-formuläret och [onEditPrescription] receptformuläret från långtrycksmenyn (HEM-11, FAV-12), [onLogLater] dosformuläret för medicinen i efterhand mot den visade
 * dagen (FAV-10, MED-16), [onOpenIllness] sjukdomsdetaljen för den pågående sjukdomen med den visade dagen (HEM-12, HEM-14) och [onOpenTrends]
 * fliken Trender (TRD-5).
 */
@Composable
fun TodayScreen(
    state: DetailUiState<TodayContent>,
    onEvent: (TodayEvent) -> Unit,
    onEditPrn: (String) -> Unit,
    modifier: Modifier = Modifier,
    failure: UiError? = null,
    undo: UndoRequest? = null,
    cooldown: CooldownPrompt? = null,
    notice: TodayNotice? = null,
    onOpenTrends: () -> Unit = {},
    onScreening: (LogEvent) -> Unit = {},
    onLogLater: (prnId: String, date: LocalDate) -> Unit = { _, _ -> },
    onOpenIllness: (episodeId: String, date: LocalDate) -> Unit = { _, _ -> },
    onEditPrescription: (String) -> Unit = {},
    health: HealthTodayUiState? = null,
    onHealthEvent: (HealthEvent) -> Unit = {},
    avatar: @Composable () -> Unit = {}
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
        error = failure,
        onErrorShown = { onEvent(TodayEvent.ErrorShown) },
        actions = { avatar() },
        snackbar = snackbar,
        sectionGap = SECTION_GAP
    ) { TodayCards(it, onEvent, TodayLinks(onEditPrn, onOpenTrends, onScreening, onLogLater, onOpenIllness, onEditPrescription), health, onHealthEvent) }
}

/** Det Idag öppnar utanför fliken – formulär, arket och Trender. */
internal class TodayLinks(
    val onEditPrn: (String) -> Unit,
    val onOpenTrends: () -> Unit,
    val onScreening: (LogEvent) -> Unit,
    val onLogLater: (String, LocalDate) -> Unit,
    val onOpenIllness: (String, LocalDate) -> Unit,
    val onEditPrescription: (String) -> Unit
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
        todayDone = content.todayComplete
    )
    if (content.progress.total > 0) ProgressBar(content.progress.done, content.progress.total, celebrate = content.isToday)
    content.dayDone?.let { done ->
        DayDoneCard(
            averageEnergy = done.comparison?.average?.toDouble(),
            versusYesterday = done.comparison?.changeFromYesterday?.toDouble(),
            streakDays = done.streak,
            play = done.playConfetti,
            onConfettiFinished = { onEvent(TodayEvent.ConfettiShown) }
        )
    }
    content.feelings?.let { FeelingsCard(it) }
    val open = { card: TodayCard -> card !in content.collapsed }
    val toggle = { card: TodayCard -> { onEvent(TodayEvent.ToggleCard(card)) } }
    MedicinesCard(content, onEvent, open(TodayCard.MEDICINES), toggle(TodayCard.MEDICINES))
    AsNeededCard(content, onEvent, links, open(TodayCard.AS_NEEDED), toggle(TodayCard.AS_NEEDED))
    MoodCard(content, links.onScreening, open(TodayCard.MOOD), toggle(TodayCard.MOOD))
    content.illness?.let { illness -> IllnessCard(illness, open(TodayCard.ILLNESS), toggle(TodayCard.ILLNESS)) { links.onOpenIllness(illness.episode.id, content.date) } }
    health?.let { HealthTodayCard(it, onHealthEvent, open(TodayCard.HEALTH), toggle(TodayCard.HEALTH)) }
    WeekTrendsCard(content.energyDays, content.energy, health, links.onOpenTrends, open(TodayCard.WEEK_TRENDS), toggle(TodayCard.WEEK_TRENDS))
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
 * Dagens känslor (HEM-21): minihjulet till vänster – TalkBack läser meningen där, en gång – med titeln och meningen i ord
 * ("Mest lugn och glad. Lite rädd vid lunch.", samma räkning och `feelingSentence` som Trender), under dem alla dagens
 * ord i tidsordning som `FeelingPills`. Bara visning; anroparen visar kortet bara när dagen har känslor.
 */
@Composable
private fun FeelingsCard(day: FeelingDay) {
    val sentence = feelingSentence(day.summary, FeelingSentenceKind.DAY)
    AppCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            FeelingMiniWheel(day.tally, sentence)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionHeader(stringResource(R.string.today_feelings_title))
                Text(sentence, Modifier.clearAndSetSemantics {}, style = AppTypography.body)
            }
        }
        ChipRow { FeelingPills(day.words, max = null) }
    }
}

/**
 * Mående (HEM-4, HEM-5, NOT-4): en tillfällesrad per aktiverat tillfälle med status och räknaren loggade av
 * tillfällena – först de ej loggade under "Kvar idag" (en tidigare dag "Ej loggat"), sedan de loggade under "Loggat";
 * etiketterna bara när båda grupperna har rader. Hela raden är radens åtgärd (NFR-17): ett loggat tillfälle öppnar loggen för ändring, annars
 * öppnar raden – liksom "Logga nu" – arket med en ny logg för den visade dagen (SCR-6). Utan aktiverade
 * tillfällen visas inget kort.
 */
@Composable
private fun MoodCard(content: TodayContent, onScreening: (LogEvent) -> Unit, expanded: Boolean, onToggle: () -> Unit) {
    val states = content.occasions
    if (states.isEmpty()) return
    AppCard {
        val logged = states.count { it.status == OccasionStatus.LOGGED }
        SectionFoldout(
            stringResource(R.string.log_mood),
            expanded,
            onToggle,
            count = stringResource(R.string.today_count_format, logged, states.size),
            tone = if (content.isToday && logged == states.size) Tone.Positive else Tone.Primary
        ) {
            val groups = remember(states) { states.groupedByLogged() }
            val openLabel = if (content.isToday) R.string.occasion_group_remaining else R.string.occasion_group_not_logged
            MoodGroup(groups.open, stringResource(openLabel).takeIf { groups.bothShown }, content, onScreening)
            MoodGroup(groups.logged, stringResource(R.string.occasion_group_logged).takeIf { groups.bothShown }, content, onScreening)
        }
    }
}

/** En grupp i Mående-kortet (HEM-5): [label] över raderna när den visas, sedan en tillfällesrad per tillfälle. */
@Composable
private fun MoodGroup(states: List<OccasionState>, label: String?, content: TodayContent, onScreening: (LogEvent) -> Unit) {
    if (label != null) GroupLabel(label)
    states.forEach { state ->
        key(state.occasion) {
            // Idag är dagen klockans när arket öppnas (`null`); en tidigare dag får tillfällets påminnelsetid (SCR-6).
            val log = { onScreening(LogEvent.LogScreening(state.occasion, content.date.takeUnless { content.isToday }, state.time)) }
            val latest = state.latest
            OccasionStateRow(state, onLog = log, onClick = if (latest != null) ({ onScreening(LogEvent.EditScreening(latest)) }) else log)
        }
    }
}

/**
 * Pågående sjukdom (HEM-12): typen, "Dag N" och senaste incheckningen, med vänsterkanten i varningston
 * (status, NFR-16). Raden öppnar sjukdomsdetaljen ([onOpen], SJ-13), där man checkar in (SJ-2).
 */
@Composable
private fun IllnessCard(illness: OngoingIllness, expanded: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    AppCard {
        SectionFoldout(stringResource(R.string.today_illness_title), expanded, onToggle, icon = R.drawable.ic_thermometer, tone = Tone.Warning) {
            val checkin = illness.lastCheckin
            val checkinDate = checkin?.date
            ItemRow(
                title = illness.episode.title(),
                subtitle = when {
                    checkin == null -> stringResource(R.string.today_illness_no_checkin)
                    checkinDate == null -> stringResource(R.string.today_illness_checkin_undated)
                    else -> stringResource(R.string.today_illness_checkin_format, DateFormat.short(checkinDate))
                },
                trailing = illness.day?.let { day -> { InfoPill(illnessDayText(day), tone = Tone.Warning) } },
                accent = AppColors.tone(Tone.Warning).content,
                onClick = onOpen,
                navigates = true
            )
        }
    }
}

/**
 * Mediciner (MED-1, MED-5, MED-13): det som är att göra, sedan kommande och tagna när de visas, och
 * knapparna som visar och döljer dem med antal. Hela raden växlar tagen; "Hoppa över" i radens meny (MED-3).
 */
@Composable
private fun MedicinesCard(content: TodayContent, onEvent: (TodayEvent) -> Unit, expanded: Boolean, onToggle: () -> Unit) {
    var skipping by remember { mutableStateOf<Dose?>(null) }
    var deleting by remember { mutableStateOf<Dose?>(null) }
    val list = content.checklist
    AppCard {
        val doses = content.doseProgress
        SectionFoldout(
            stringResource(R.string.tab_medicines),
            expanded,
            onToggle,
            count = if (doses.total > 0) stringResource(R.string.today_count_format, doses.done, doses.total) else null,
            tone = if (content.isToday && doses.isComplete && !content.dosesMissing) Tone.Positive else Tone.Primary
        ) {
            val rows = remember(list, content.showUpcoming, content.showDone) {
                list.shown + (if (content.showUpcoming) list.upcoming else emptyList()) +
                    (if (content.showDone) list.done.map { OpenDose(it, null) } else emptyList())
            }
            // Radens tillstånd (meny, anteckning, krysset) följer dosen när listan ändras.
            rows.forEach { row -> key(row.dose.id) { DoseRow(row, content, onEvent, onSkip = { skipping = it }, onDelete = { deleting = it }) } }
            when {
                // HEM-10, MED-4: doser kunde inte skapas – också när några finns; aldrig "Allt avklarat". Orsaken visas, och när det görs om.
                content.missingCause != null -> {
                    val offline = content.missingCause == DataError.Offline
                    EmptyState(
                        R.drawable.ic_warning,
                        stringResource(R.string.today_medicines_missing_title),
                        stringResource(if (offline) R.string.today_medicines_missing_offline else content.missingCause.toMessage()),
                        note = stringResource(if (offline) R.string.today_medicines_missing_offline_retry else R.string.today_medicines_missing_retry),
                        isError = true,
                        compact = true
                    )
                }

                list.isEmpty -> EmptyState(R.drawable.ic_pill, stringResource(R.string.today_medicines_none_title), stringResource(R.string.today_medicines_none), compact = true)

                list.shown.isEmpty() && list.upcoming.isEmpty() && !content.showDone ->
                    EmptyState(R.drawable.ic_check, stringResource(R.string.today_medicines_all_done_title), stringResource(R.string.today_medicines_all_done), compact = true)

                // Allt som återstår ligger mer än tre timmar fram och är dolt (MED-13): säg när nästa dos är – det tidigaste
                // klockslaget urvalet räknats med (`scheduledTime`; för en receptdos tidpunktens i Påminnelser, NOT-18).
                rows.isEmpty() && list.upcoming.isNotEmpty() -> list.upcoming.mapNotNull { it.time }.minOrNull()?.let { next ->
                    EmptyState(
                        R.drawable.ic_clock,
                        stringResource(R.string.today_next_dose_title),
                        stringResource(R.string.today_next_dose_format, DateFormat.time(next)),
                        compact = true
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
    }
    skipping?.let { dose ->
        val close = { skipping = null }
        ConfirmDialog(
            stringResource(R.string.today_skip_title),
            dose.date.let { day ->
                val title = dose.title()
                if (day == null || day == content.today) stringResource(R.string.today_skip_message, title) else stringResource(R.string.today_skip_message_day, title, DateFormat.display(day))
            },
            stringResource(R.string.today_skip),
            onConfirm = {
                close()
                onEvent(TodayEvent.Skip(dose))
            },
            onDismiss = close
        )
    }
    deleting?.let { dose ->
        val close = { deleting = null }
        // Samma beslut som `DoseRepository.remove`: en receptdos hoppas över (MED-15).
        val action = dose.deleteAction(dose.takenAt?.toLocalDateTime(content.zone)?.time) { onEvent(TodayEvent.Delete(dose)) }
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
        title = dose.title(),
        checked = dose.status == DoseStatus.TAKEN,
        onCheckedChange = { onEvent(TodayEvent.SetTaken(dose, it)) },
        subtitle = doseSubtitle(dose, item.time, dose.prescriptionId?.let(content.prescriptions::get), content.date, content.zone),
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
        }
    )
}

/**
 * "Morgon · tagen 07:12 · 50 mg + 25 mg höjning t.o.m. 12 okt" – det som saknas utelämnas. En planerad dos visar
 * [scheduled], klockslaget den väntar på ([OpenDose.time] – för en receptdos tidpunktens i Påminnelser, NOT-18).
 */
@Composable
private fun doseSubtitle(dose: Dose, scheduled: LocalTime?, prescription: Prescription?, date: LocalDate, zone: TimeZone): String {
    val time = when (dose.status) {
        DoseStatus.TAKEN -> dose.takenAt?.let { stringResource(R.string.today_dose_taken_format, DateFormat.time(it.toLocalDateTime(zone).time)) }
        DoseStatus.SKIPPED -> stringResource(R.string.today_dose_skipped)
        DoseStatus.PLANNED -> (scheduled ?: dose.plannedTime)?.let(DateFormat::time)
    }
    val boost = prescription?.boostFor(date)?.let { boost ->
        stringResource(R.string.today_dose_boost_format, doseText(prescription.dose, prescription.unit), doseText(boost.dose, prescription.unit), periodText(null, prescription.boostEnd(boost)))
    }
    return listOfNotNull(stringResource(dose.slot.label()), time, boost).joinToString(" · ")
}

private fun DayPart.greeting(): Int = when (this) {
    DayPart.MORNING -> R.string.greeting_morning
    DayPart.AFTERNOON -> R.string.greeting_afternoon
    DayPart.EVENING -> R.string.greeting_evening
    DayPart.NIGHT -> R.string.greeting_night
}
