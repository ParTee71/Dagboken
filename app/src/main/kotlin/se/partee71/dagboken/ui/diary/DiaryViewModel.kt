package se.partee71.dagboken.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.partee71.dagboken.core.engine.DayLabel
import se.partee71.dagboken.core.engine.DiaryEntry
import se.partee71.dagboken.core.engine.DiaryFilter
import se.partee71.dagboken.core.engine.DiarySources
import se.partee71.dagboken.core.engine.DiaryType
import se.partee71.dagboken.core.engine.DiaryWindow
import se.partee71.dagboken.core.engine.dates
import se.partee71.dagboken.core.engine.dayLabel
import se.partee71.dagboken.core.engine.diaryDays
import se.partee71.dagboken.core.engine.diaryEntries
import se.partee71.dagboken.core.engine.on
import se.partee71.dagboken.core.engine.shownBy
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.data.common.combineByKey
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.repository.ActivityRepository
import se.partee71.dagboken.data.repository.DoseRepository
import se.partee71.dagboken.data.repository.EventRepository
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.ui.common.DetailLoader
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.dagboken.ui.common.days
import se.partee71.dagboken.ui.common.failureOrNull
import se.partee71.dagboken.ui.common.toListUiState

/** Listvy eller kalendervy (HIST-6). */
enum class DiaryView { LIST, CALENDAR }

/** En rad i Dagboken: en post under sin dag, eller – i kalendervyn – den valda dagen utan poster. */
sealed interface DiaryRow {
    val key: String
    val date: LocalDate
    val label: DayLabel

    /** [optionName] är aktivitetens eller händelsens namn ur Listor (`null` för övriga, och tills listan lästs). */
    data class Entry(val entry: DiaryEntry, override val label: DayLabel, val optionName: String? = null) : DiaryRow {
        override val key: String get() = entry.id
        override val date: LocalDate get() = entry.date
    }

    /** Den valda dagen i kalendervyn utan poster – en rad under kalendern, inte skärmens tomma tillstånd. */
    data class EmptyDay(override val date: LocalDate, override val label: DayLabel) : DiaryRow {
        override val key: String get() = "empty:$date"
    }

    /** Den valda dagen ligger i ett år som läses just nu (HIST-8). */
    data class LoadingDay(override val date: LocalDate, override val label: DayLabel) : DiaryRow {
        override val key: String get() = "loading:$date"
    }
}

/**
 * Det som styr vyn: [filter] (HIST-2), [view] (HIST-6), kalenderns [month] (första dagen) och valda dag
 * [selected], dagarna med poster för filtret ([datesWithEntries], kalenderns punkter) och hur många år som
 * lästs ([years], HIST-8).
 */
data class DiaryControls(
    val today: LocalDate,
    val filter: DiaryFilter = DiaryFilter(),
    val view: DiaryView = DiaryView.LIST,
    val month: LocalDate = LocalDate(today.year, today.month, 1),
    val selected: LocalDate = today,
    val datesWithEntries: Set<LocalDate> = emptySet(),
    val years: Int = 1,
    /** Ett äldre år läses ("Visa äldre" visar laddning, HIST-8). */
    val loadingOlder: Boolean = false,
)

sealed interface DiaryEvent {
    /** Chippet "Alla" (HIST-2). */
    data object ShowAll : DiaryEvent

    /** Ett typchip (HIST-2): [DiaryFilter.toggle]. */
    data class Toggle(val type: DiaryType) : DiaryEvent

    data class ShowView(val view: DiaryView) : DiaryEvent

    /** En dag i kalendern; framtida dagar går inte att välja (HIST-6). */
    data class SelectDate(val date: LocalDate) : DiaryEvent

    /** Kalenderns månadsbyte – en äldre månad läser in så många år till som behövs (HIST-8). */
    data class ShowMonth(val month: LocalDate) : DiaryEvent

    /** "Visa äldre" (HIST-8): ett år till. */
    data object ShowOlder : DiaryEvent

    /** Bekräftad radering (HIST-5). */
    data class Delete(val entry: DiaryEntry) : DiaryEvent

    data object Retry : DiaryEvent

    data object ErrorShown : DiaryEvent
}

/**
 * Fliken Dagbok (HIST-1…HIST-9): alla posttyper i ett flöde, ett år i taget. Tidslinjen, filterregeln och
 * fönstret räknas i `:core` (`DiaryTimeline`); radering går till samma repository-metod som postens
 * domänskärm. Varje år läses för sig och delas ([year]), så att "Visa äldre" bara läser det nya året –
 * de tidigare lyssnarna står kvar, och listan byts aldrig mot laddning (scrollpositionen behålls).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val screenings: ScreeningRepository,
    private val activities: ActivityRepository,
    private val doses: DoseRepository,
    private val events: EventRepository,
    private val illnesses: IllnessRepository,
    options: OptionsRepository,
    private val clock: Clock,
    private val zone: Provider<TimeZone>,
) : ViewModel() {
    /** Idag ur klockan vid varje prenumeration (inget gammalt startvärde efter en paus), igen vid midnatt. */
    private val today: Flow<LocalDate> = clock.days { zone.get() }.distinctUntilChanged().shareIn(viewModelScope, sharing, replay = 1)

    /** Årsgränsernas ankare (HIST-8): dagen ViewModeln skapades – midnatt flyttar bara år 0:s slut. */
    private val anchor: LocalDate = clock.todayIn(zone.get())

    private val filter = MutableStateFlow(DiaryFilter())
    private val view = MutableStateFlow(DiaryView.LIST)

    /** `null` = idag (följer med över midnatt), respektive den valda dagens månad. */
    private val selected = MutableStateFlow<LocalDate?>(null)
    private val month = MutableStateFlow<LocalDate?>(null)
    private val years = MutableStateFlow(1)

    private val window: Flow<DiaryWindow> = combine(today, years) { today, years -> DiaryWindow(anchor, years, maxOf(today, anchor)) }.distinctUntilChanged()

    /** Ett års läsning per intervall – delad, så att ett nytt fönster eller en ny dag inte läser om de fasta åren. */
    private val yearReads = mutableMapOf<ClosedRange<LocalDate>, Flow<Result<DiarySources>>>()

    private fun year(window: DiaryWindow, index: Int): Flow<Result<DiarySources>> {
        val range = window.year(index)
        // Bara år 0 slutar på ankaret eller senare; gårdagens år 0 stängs av sig självt när ingen lyssnar.
        if (index == 0) yearReads.keys.removeAll { it.endInclusive >= anchor && it != range }
        return yearReads.getOrPut(range) {
            val (from, to) = range.start to range.endInclusive
            // HIST-7: en dos planerad dagen före året men tagen inom det hör till året – `within` avgör.
            val doseFrom = from.minus(1, DateTimeUnit.DAY)
            combine(screenings.observeDays(from, to), activities.observeDays(from, to), doses.observeDays(doseFrom, to), events.observeDays(from, to)) { s, a, d, e ->
                Result.success(DiarySources(screenings = s, activities = a, doses = d, events = e))
            }
                // Ett läsfel delas som värde – ett fel i en delad läsning får inte fälla ViewModeln; det kastas i tidslinjen.
                .catch { emit(Result.failure(it)) }
                .shareIn(viewModelScope, sharing, replay = 1)
        }
    }

    /** Fönstrets år, äldsta sist; ett år till lägger bara till en läsning, och det som visas står kvar tills den kommit. */
    private val windowed: Flow<Pair<DiaryWindow, DiarySources>> = window.flatMapLatest { window ->
        combine(List(window.years) { year(window, it) }) { reads -> window to reads.fold(DiarySources()) { all, read -> all + read.getOrThrow() } }
    }

    /** HIST-9: alla episoder (en användare har få) med sina incheckningar; lyssnas om bara när episoderna byts. */
    private val illness: Flow<DiarySources> = illnesses.observeEpisodes().combineByKey(
        { episodes -> episodes.map { it.id } },
        { ids -> if (ids.isEmpty()) flowOf(emptyMap()) else combine(ids.map { id -> illnesses.observeCheckins(id).map { id to it } }) { it.toMap<String, List<Checkin>>() } },
    ) { episodes, checkins -> DiarySources(episodes = episodes, checkins = checkins) }

    /** Namnen på aktiviteter och händelser ur Listor – arkiverade med; ett läsfel ger bara typens namn (`withFallback`). */
    private val names: Flow<Map<String, String>> = combine(
        options.observe(OptionKind.ACTIVITY).withFallback(emptyList()),
        options.observe(OptionKind.EVENT).withFallback(emptyList()),
    ) { a, e -> (a + e).associate { it.id to it.name } }.distinctUntilChanged()

    /** Tidslinjen för det lästa fönstret [window] (alla typer) med namnen ur Listor. */
    private data class Timeline(val window: DiaryWindow, val entries: List<DiaryEntry>, val names: Map<String, String>)

    /** Posterna räknas **en gång** per läsning (`:core`); laddning, fel och "Försök igen" via den delade [DetailLoader]. */
    private val loader = DetailLoader(
        combine(windowed, illness, names) { (window, sources), illness, names -> Timeline(window, window.within(diaryEntries(sources + illness, zone.get())), names) },
        viewModelScope,
    )

    /** Tidslinjen med filtret (HIST-2) – **ett** flöde som både listans rader och kalenderns punkter läser. */
    private val filtered: StateFlow<DetailUiState<Timeline>> = combine(loader.state, filter) { state, filter ->
        if (state is DetailUiState.Content) DetailUiState.Content(state.value.copy(entries = state.value.entries.shownBy(filter))) else state
    }.stateIn(viewModelScope, sharing, DetailUiState.Loading)

    private data class Choice(val view: DiaryView, val selected: LocalDate?, val today: LocalDate)

    private val choice = combine(view, selected, today, ::Choice)

    /** Listans tillstånd: laddning och fel ur läsningen, annars raderna för vyn. */
    val state: StateFlow<ListUiState<DiaryRow>> = combine(filtered, choice) { state, choice ->
        when (state) {
            DetailUiState.Loading -> ListUiState.Loading
            is DetailUiState.Error -> ListUiState.Error(state.error)
            is DetailUiState.Content -> rows(state.value, choice).toListUiState()
        }
    }.stateIn(viewModelScope, sharing, ListUiState.Loading)

    private fun rows(timeline: Timeline, choice: Choice): List<DiaryRow> {
        val today = choice.today
        return when (choice.view) {
            DiaryView.LIST -> diaryDays(timeline.entries, today).flatMap { day -> day.entries.map { row(it, day.label, timeline.names) } }
            DiaryView.CALENDAR -> {
                val day = choice.selected ?: today
                val label = dayLabel(day, today)
                if (day !in timeline.window) {
                    // Ett äldre år som inte lästs än: laddning, inte "Inga poster".
                    listOf(DiaryRow.LoadingDay(day, label))
                } else {
                    timeline.entries.on(day).map { row(it, label, timeline.names) }.ifEmpty { listOf(DiaryRow.EmptyDay(day, label)) }
                }
            }
        }
    }

    private fun row(entry: DiaryEntry, label: DayLabel, names: Map<String, String>): DiaryRow.Entry {
        val optionId = when (entry) {
            is DiaryEntry.Action -> entry.activity.optionId
            is DiaryEntry.Happening -> entry.event.optionId
            else -> null
        }
        return DiaryRow.Entry(entry, label, optionId?.let(names::get))
    }

    private data class Shown(val filter: DiaryFilter, val view: DiaryView, val selected: LocalDate?, val month: LocalDate?, val years: Int)

    /** Det som styr vyn, med kalenderns punkter ur samma filtrerade tidslinje som listan. */
    val controls: StateFlow<DiaryControls> = combine(
        combine(filter, view, selected, month, years, ::Shown),
        today,
        filtered,
    ) { shown, today, filtered ->
        val day = shown.selected ?: today
        val loaded = (filtered as? DetailUiState.Content)?.value
        DiaryControls(
            today = today,
            filter = shown.filter,
            view = shown.view,
            month = shown.month ?: LocalDate(day.year, day.month, 1),
            selected = day,
            datesWithEntries = loaded?.entries?.dates().orEmpty(),
            years = shown.years,
            loadingOlder = loaded != null && loaded.window.years < shown.years,
        )
    }.stateIn(viewModelScope, sharing, DiaryControls(anchor))

    private val _failure = MutableStateFlow<Failure?>(null)
    val failure: StateFlow<Failure?> = _failure.asStateFlow()

    fun onEvent(event: DiaryEvent) {
        when (event) {
            DiaryEvent.ShowAll -> filter.value = filter.value.showAll()
            is DiaryEvent.Toggle -> filter.value = filter.value.toggle(event.type)
            is DiaryEvent.ShowView -> view.value = event.view
            is DiaryEvent.SelectDate -> selectDate(event.date)
            is DiaryEvent.ShowMonth -> showMonth(event.month)
            DiaryEvent.ShowOlder -> years.value += 1
            is DiaryEvent.Delete -> delete(event.entry)
            DiaryEvent.Retry -> {
                // Ett delat läsfel ligger kvar i sin läsning – "Försök igen" läser om alla år.
                yearReads.clear()
                loader.retry()
            }
            DiaryEvent.ErrorShown -> _failure.value = null
        }
    }

    private fun selectDate(date: LocalDate) {
        val today = clock.todayIn(zone.get())
        if (date > today) return
        selected.value = date.takeIf { it != today }
        month.value = null
        cover(date)
    }

    private fun showMonth(first: LocalDate) {
        month.value = first
        cover(first)
    }

    /** HIST-8: läser in så många år till att [date] ryms – aldrig färre än nu. */
    private fun cover(date: LocalDate) {
        val today = maxOf(clock.todayIn(zone.get()), anchor)
        years.value = maxOf(years.value, DiaryWindow(anchor, years.value, today).covering(date).years)
    }

    /** HIST-5: samma repository-metod som postens domänskärm. Episodens start och slut tas bort i sjukdomsdetaljen (SJ-9). */
    private fun delete(entry: DiaryEntry) {
        val action: suspend () -> Result<Unit> = when (entry) {
            is DiaryEntry.Mood -> ({ screenings.delete(entry.screening.id) })
            is DiaryEntry.Action -> ({ activities.delete(entry.activity.id) })
            is DiaryEntry.TakenDose -> ({ doses.remove(entry.dose) })
            is DiaryEntry.Happening -> ({ events.delete(entry.event.id) })
            is DiaryEntry.CheckIn -> ({ illnesses.deleteCheckin(entry.episode.id, entry.checkin.id) })
            is DiaryEntry.EpisodeStart, is DiaryEntry.EpisodeEnd -> return
        }
        viewModelScope.launch { action().failureOrNull()?.let { _failure.value = it } }
    }

    private companion object {
        /** Delade flöden följer skärmen och glömmer sitt senaste värde när de stoppats. */
        val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0)
    }
}
