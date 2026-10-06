package se.partee71.dagboken.ui.diary

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.res.Resources
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.DayLabel
import se.partee71.dagboken.core.engine.DiaryEntry
import se.partee71.dagboken.core.engine.DiaryType
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.navigation.DiaryEntryKind
import se.partee71.dagboken.ui.common.ArchiveEvent
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.common.color
import se.partee71.dagboken.ui.common.durationText
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.common.medicineTitle
import se.partee71.dagboken.ui.common.nonBlank
import se.partee71.dagboken.ui.common.scaleLevel
import se.partee71.dagboken.ui.common.scaleValueText
import se.partee71.dagboken.ui.common.title
import se.partee71.dagboken.ui.components.AccountAvatar
import se.partee71.dagboken.ui.components.AppButton
import se.partee71.dagboken.ui.components.AppLoading
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppFilterChip
import se.partee71.dagboken.ui.components.AppIconButton
import se.partee71.dagboken.ui.components.ButtonVariant
import se.partee71.dagboken.ui.components.ChipRow
import se.partee71.dagboken.ui.components.DagbokenCalendar
import se.partee71.dagboken.ui.components.DagbokenEntryCard
import se.partee71.dagboken.ui.components.DeleteAction
import se.partee71.dagboken.ui.components.EmptyContent
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.EntityListScreen
import se.partee71.dagboken.ui.components.IconButtonVariant
import se.partee71.dagboken.ui.components.InfoPill
import se.partee71.dagboken.ui.components.ListArchive
import se.partee71.dagboken.ui.components.ListGroup
import se.partee71.dagboken.ui.components.UpcomingScreen
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

@Composable
fun DiaryRoute(
    account: AuthUser?,
    onAccount: () -> Unit,
    onOpen: (DiaryEntry) -> Unit,
    viewModel: DiaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val controls by viewModel.controls.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    DiaryScreen(state, controls, failure, viewModel::onEvent, onOpen) {
        AccountAvatar(account?.name ?: account?.email, onAccount, photoUrl = account?.photoUrl)
    }
}

/**
 * Fliken Dagbok (HIST-1…HIST-9) på `EntityListScreen`: stor topprad med Listvy och Kalendervy (den valda
 * markerad) och avataren (NAV-9), som övriga flikar; filterchipsen (HIST-2) står fast under rubriken i alla
 * lägen. Listvyn har en rubrik per dag och "Visa äldre" sist (HIST-8); kalendervyn har kalendern överst och
 * den valda dagens poster under (HIST-6). Posterna är postkort (NFR-15/16) – tryck öppnar ([onOpen]),
 * Redigera och Ta bort i menyn, svep begär radering. Ingen lägg till: plusknappen i verktygsraden loggar.
 */
@Composable
fun DiaryScreen(
    state: ListUiState<DiaryRow>,
    controls: DiaryControls,
    failure: Failure?,
    onEvent: (DiaryEvent) -> Unit,
    onOpen: (DiaryEntry) -> Unit,
    avatar: @Composable () -> Unit = {},
) {
    val resources = LocalResources.current
    // Rubriken formateras en gång per dag, inte per rad; lambdan är stabil så länge dagen och resurserna är det.
    val dayGroup = remember(controls.today, resources) {
        val groups = mutableMapOf<LocalDate, ListGroup>()
        val group: (DiaryRow) -> ListGroup = { row ->
            groups.getOrPut(row.date) { ListGroup(dayTitle(row, controls.today, resources), tone = Tone.Neutral, cards = true, showCount = false) }
        }
        group
    }
    val calendar = controls.view == DiaryView.CALENDAR
    EntityListScreen(
        title = stringResource(R.string.tab_diary),
        state = state,
        empty = emptyContent(controls),
        add = null,
        key = { it.key },
        onRetry = { onEvent(DiaryEvent.Retry) },
        group = dayGroup,
        archive = ListArchive(failure = failure, showToggle = false) { if (it == ArchiveEvent.ErrorShown) onEvent(DiaryEvent.ErrorShown) },
        actions = {
            ViewButton(DiaryView.LIST, controls.view, R.drawable.ic_list, R.string.diary_view_list, onEvent)
            ViewButton(DiaryView.CALENDAR, controls.view, R.drawable.ic_calendar, R.string.diary_view_calendar, onEvent)
            avatar()
        },
        filter = { FilterChips(controls, onEvent) },
        header = if (calendar) ({ Calendar(controls, onEvent) }) else null,
        footer = if (calendar) null else ({ ShowOlder(controls.years, controls.loadingOlder, onEvent) }),
    ) { row ->
        when (row) {
            is DiaryRow.Entry -> EntryCard(row, onEvent, onOpen)
            is DiaryRow.LoadingDay -> AppLoading(Modifier.padding(vertical = Spacing.xl))
            is DiaryRow.EmptyDay -> EmptyState(R.drawable.ic_calendar, stringResource(R.string.diary_day_empty_title), stringResource(R.string.diary_day_empty_message))
        }
    }
}

/** HIST-1: "Idag · tisdag 6 oktober", "Igår · måndag 5 oktober", "Lördag 3 oktober" – med år för en dag ett annat år än idag. */
private fun dayTitle(row: DiaryRow, today: LocalDate, resources: Resources): String = when (row.label) {
    DayLabel.TODAY -> resources.getString(R.string.diary_day_today_format, DateFormat.weekdayDayAndMonth(row.date))
    DayLabel.YESTERDAY -> resources.getString(R.string.diary_day_yesterday_format, DateFormat.weekdayDayAndMonth(row.date))
    DayLabel.OTHER -> DateFormat.dayAndMonth(row.date, withYear = row.date.year != today.year)
}

/** Listvy/Kalendervy i toppraden: den valda tonad och läst som vald (HIST-6). */
@Composable
private fun ViewButton(view: DiaryView, current: DiaryView, icon: Int, @StringRes label: Int, onEvent: (DiaryEvent) -> Unit) {
    val chosen = view == current
    AppIconButton(
        icon,
        stringResource(label),
        { onEvent(DiaryEvent.ShowView(view)) },
        Modifier.semantics { selected = chosen },
        variant = if (chosen) IconButtonVariant.Tonal else IconButtonVariant.Plain,
    )
}

/** HIST-2: Alla först, sedan en per typ; en typ är markerad bara när inte alla visas. Raden bryts (som 3.x). */
@Composable
private fun FilterChips(controls: DiaryControls, onEvent: (DiaryEvent) -> Unit) {
    val filter = controls.filter
    ChipRow {
        AppFilterChip(stringResource(R.string.diary_filter_all), filter.showsAll, { onEvent(DiaryEvent.ShowAll) })
        DiaryType.entries.forEach { type ->
            AppFilterChip(stringResource(type.chip), !filter.showsAll && filter.shows(type), { onEvent(DiaryEvent.Toggle(type)) })
        }
    }
}

@get:StringRes
private val DiaryType.chip: Int
    get() = when (this) {
        DiaryType.SCREENING -> R.string.log_mood
        DiaryType.ACTIVITY -> R.string.diary_filter_activity
        DiaryType.DOSE -> R.string.diary_filter_dose
        DiaryType.EVENT -> R.string.diary_filter_event
        DiaryType.ILLNESS -> R.string.log_illness
    }

@get:StringRes
private val DiaryType.emptyTitle: Int
    get() = when (this) {
        DiaryType.SCREENING -> R.string.diary_empty_screening
        DiaryType.ACTIVITY -> R.string.diary_empty_activity
        DiaryType.DOSE -> R.string.diary_empty_dose
        DiaryType.EVENT -> R.string.diary_empty_event
        DiaryType.ILLNESS -> R.string.diary_empty_illness
    }

/** HIST-2: tomt för filtret – vilka typer och hur långt bakåt som lästs. */
@Composable
private fun emptyContent(controls: DiaryControls): EmptyContent {
    val filter = controls.filter
    val period = pluralStringResource(R.plurals.diary_period, controls.years, controls.years)
    val single = filter.types.singleOrNull()
    return when {
        filter.showsAll -> EmptyContent(R.drawable.ic_book, stringResource(R.string.diary_empty_title), stringResource(R.string.diary_empty_message, period))
        single != null -> EmptyContent(
            R.drawable.ic_book,
            stringResource(single.emptyTitle),
            stringResource(R.string.diary_empty_type_message, stringResource(single.chip), period),
        )
        else -> EmptyContent(R.drawable.ic_book, stringResource(R.string.diary_empty_title), stringResource(R.string.diary_empty_selected_message, period))
    }
}

/** HIST-6: kalendern med punkter för filtrets dagar, idag med ring och framtiden nedtonad. */
@Composable
private fun Calendar(controls: DiaryControls, onEvent: (DiaryEvent) -> Unit) {
    AppCard {
        DagbokenCalendar(
            month = controls.month,
            onMonthChange = { onEvent(DiaryEvent.ShowMonth(it)) },
            datesWithEntries = controls.datesWithEntries,
            selectedDate = controls.selected,
            onDateClick = { onEvent(DiaryEvent.SelectDate(it)) },
            today = controls.today,
            dimFuture = true,
        )
    }
}

/** HIST-8: ett år till. */
@Composable
private fun ShowOlder(years: Int, loading: Boolean, onEvent: (DiaryEvent) -> Unit) {
    AppButton(
        pluralStringResource(R.plurals.diary_show_older, years, years),
        { onEvent(DiaryEvent.ShowOlder) },
        Modifier.fillMaxWidth(),
        variant = ButtonVariant.Secondary,
        icon = R.drawable.ic_expand_more,
        loading = loading,
    )
}

/** Postkortets innehåll per typ: titel, undertext med klockslaget först, värdepill och vänsteraccent. */
private class CardText(val title: String, val subtitle: String, val pill: String? = null, val tone: Tone = Tone.Neutral)

@Composable
private fun cardText(row: DiaryRow.Entry): CardText = when (val entry = row.entry) {
    is DiaryEntry.Mood -> {
        val s = entry.screening
        val title = s.occasion?.let { stringResource(it.label()) } ?: s.customText.nonBlank() ?: stringResource(R.string.log_mood)
        // Typen står i titeln när posten saknar tillfälle; undertexten har stressen, inte "Mående" en gång till.
        CardText(title, subtitle(entry.time, stringResource(R.string.diary_stress_format, s.stress)), stringResource(R.string.diary_energy_format, scaleValueText(s.energy)), scaleLevel(s.energy).tone)
    }
    is DiaryEntry.Action -> {
        val a = entry.activity
        val title = a.customText.nonBlank() ?: row.optionName ?: stringResource(R.string.log_activity)
        val detail = a.minutes?.let { durationText(it) } ?: stringResource(R.string.log_activity)
        CardText(title, subtitle(entry.time, detail), stringResource(R.string.diary_energy_format, scaleValueText(a.energy, DocumentRules.ACTIVITY_ENERGY)), scaleLevel(a.energy, DocumentRules.ACTIVITY_ENERGY).tone)
    }
    is DiaryEntry.TakenDose -> {
        val d = entry.dose
        CardText(medicineTitle(d.name, d.dose, d.unit), subtitle(entry.time, stringResource(d.slot.label())))
    }
    is DiaryEntry.Happening -> {
        val e = entry.event
        val detail = e.durationMinutes.takeIf { it > 0 }?.let { durationText(it) } ?: stringResource(R.string.log_event)
        val level = scaleLevel(e.severity, higherIsBetter = false)
        CardText(row.optionName ?: stringResource(R.string.log_event), subtitle(entry.time, detail), stringResource(R.string.diary_severity_format, e.severity), level.tone)
    }
    is DiaryEntry.CheckIn -> CardText(
        entry.episode.title(),
        subtitle(entry.time, stringResource(R.string.diary_subtitle_format, stringResource(R.string.diary_checkin), stringResource(R.string.diary_severity_format, entry.checkin.severity))),
        entry.day?.let { stringResource(R.string.today_illness_day_format, it) },
        Tone.Warning,
    )
    is DiaryEntry.EpisodeStart -> CardText(entry.episode.title(), stringResource(R.string.diary_episode_started), entry.day?.let { stringResource(R.string.today_illness_day_format, it) }, Tone.Warning)
    is DiaryEntry.EpisodeEnd -> CardText(entry.episode.title(), stringResource(R.string.diary_episode_ended), entry.day?.let { stringResource(R.string.today_illness_day_format, it) }, Tone.Warning)
}

/** "08:15 · Mående" – klockslaget först (HIST-7); utan klockslag bara [detail]. */
@Composable
private fun subtitle(time: LocalTime?, detail: String): String =
    time?.let { stringResource(R.string.diary_subtitle_format, DateFormat.time(it), detail) } ?: detail

/** Postkortet (NFR-15/16): tryck öppnar, ⋮ och långtryck = Redigera · Ta bort, svep begär radering (HIST-5). */
@Composable
private fun EntryCard(row: DiaryRow.Entry, onEvent: (DiaryEvent) -> Unit, onOpen: (DiaryEntry) -> Unit) {
    val entry = row.entry
    val text = cardText(row)
    val accent = when (entry) {
        is DiaryEntry.Mood -> scaleLevel(entry.screening.energy).color
        is DiaryEntry.Action -> scaleLevel(entry.activity.energy, DocumentRules.ACTIVITY_ENERGY).color
        is DiaryEntry.Happening -> scaleLevel(entry.event.severity, higherIsBetter = false).color
        else -> null
    }
    DagbokenEntryCard(
        title = text.title,
        onClick = { onOpen(entry) },
        subtitle = text.subtitle,
        accent = accent,
        status = text.pill?.let { { InfoPill(it, tone = text.tone) } },
        note = entry.note.orEmpty(),
        onEdit = { onOpen(entry) },
        delete = deleteSubject(entry, text.title)?.let { subject ->
            // MED-15 (som 3.x): en receptdos markeras överhoppad i stället för att raderas.
            val skips = entry is DiaryEntry.TakenDose && entry.dose.prescriptionId != null
            val message = stringResource(if (skips) R.string.diary_delete_skip_message else R.string.diary_delete_message, subject)
            DeleteAction(stringResource(R.string.diary_delete_title), message) { onEvent(DiaryEvent.Delete(entry)) }
        },
    )
}

/**
 * HIST-5: posten i bekräftelsen – typen, namnet ([title]) och när ("Incheckningen för Förkylning, 5 okt kl. 09:00").
 * `null` för det som inte tas bort i Dagbok (episodens start och slut, SJ-9).
 */
@Composable
private fun deleteSubject(entry: DiaryEntry, title: String): String? {
    val format = when (entry) {
        is DiaryEntry.Mood -> R.string.diary_subject_screening
        is DiaryEntry.Action -> R.string.diary_subject_activity
        is DiaryEntry.TakenDose -> R.string.diary_subject_dose
        is DiaryEntry.Happening -> R.string.diary_subject_event
        is DiaryEntry.CheckIn -> R.string.diary_subject_checkin
        is DiaryEntry.EpisodeStart, is DiaryEntry.EpisodeEnd -> return null
    }
    val day = DateFormat.short(entry.date)
    val time = entry.time
    val whenText = if (time == null) day else stringResource(R.string.diary_when_format, day, DateFormat.time(time))
    return stringResource(format, title, whenText)
}

/**
 * Platshållaren när en post öppnas (HIST-3), tills redigeringen (#239) och sjukdomsdetaljen (#240) finns:
 * samma lilla topprad med tillbakapil som de andra underskärmarna.
 */
@Composable
fun DiaryEntryPlaceholder(kind: DiaryEntryKind, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val episode = kind == DiaryEntryKind.EPISODE
    val title = when (kind) {
        DiaryEntryKind.SCREENING -> R.string.log_mood
        DiaryEntryKind.ACTIVITY -> R.string.log_activity
        DiaryEntryKind.DOSE -> R.string.dose_label
        DiaryEntryKind.EVENT -> R.string.log_event
        DiaryEntryKind.EPISODE -> R.string.log_illness
        DiaryEntryKind.CHECKIN -> R.string.diary_checkin
    }
    UpcomingScreen(
        stringResource(title),
        if (episode) R.drawable.ic_thermometer else R.drawable.ic_edit,
        stringResource(if (episode) R.string.diary_episode_upcoming else R.string.diary_entry_upcoming),
        modifier,
        onBack = onBack,
    )
}
