package se.kodhast.dagboken.ui.diary

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import se.kodhast.dagboken.core.engine.DayLabel
import se.kodhast.dagboken.core.engine.DiaryEntry
import se.kodhast.dagboken.core.engine.DiaryFilter
import se.kodhast.dagboken.core.engine.DiarySources
import se.kodhast.dagboken.core.engine.DiaryType
import se.kodhast.dagboken.core.engine.DiaryWindow
import se.kodhast.dagboken.core.engine.dates
import se.kodhast.dagboken.core.engine.diaryDays
import se.kodhast.dagboken.core.engine.diaryEntries
import se.kodhast.dagboken.core.engine.jumpTarget
import se.kodhast.dagboken.core.engine.shownBy
import se.kodhast.dagboken.core.model.Checkin
import se.kodhast.dagboken.core.model.OptionKind
import se.kodhast.dagboken.core.time.recentDays
import se.kodhast.dagboken.data.common.combineByKey
import se.kodhast.dagboken.data.common.withFallback
import se.kodhast.dagboken.data.repository.ActivityRepository
import se.kodhast.dagboken.data.repository.DoseRepository
import se.kodhast.dagboken.data.repository.EventRepository
import se.kodhast.dagboken.data.repository.IllnessRepository
import se.kodhast.dagboken.data.repository.OptionsRepository
import se.kodhast.dagboken.data.repository.ScreeningRepository
import se.kodhast.dagboken.ui.common.AppErrors
import se.kodhast.dagboken.ui.common.days
import se.kodhast.dagboken.ui.common.names
import se.kodhast.ui.common.DetailLoader
import se.kodhast.ui.common.DetailUiState
import se.kodhast.ui.common.ListUiState
import se.kodhast.ui.common.UiError
import se.kodhast.ui.common.WriteFailures
import se.kodhast.ui.common.screenSharing
import se.kodhast.ui.common.toListUiState

/** En rad i Dagboken: en post under sin dag. */
sealed interface DiaryRow {
    val key: String
    val date: LocalDate
    val label: DayLabel

    /** [optionName] är aktivitetens eller händelsens namn ur Listor (`null` för övriga, och tills listan lästs). */
    data class Entry(val entry: DiaryEntry, override val label: DayLabel, val optionName: String? = null) : DiaryRow {
        override val key: String get() = entry.id
        override val date: LocalDate get() = entry.date
    }
}

/**
 * Det som styr vyn: [filter] (HIST-2), dagarna med poster för filtret ([datesWithEntries], punkterna i Välj dag,
 * HIST-6), hur många år som lästs ([years], HIST-8) och raden som listan ska rulla till ([jump], HIST-6).
 */
@Immutable
data class DiaryControls(
    val today: LocalDate,
    val filter: DiaryFilter = DiaryFilter(),
    val datesWithEntries: ImmutableSet<LocalDate> = persistentSetOf(),
    val years: Int = 1,
    /** Ett äldre år läses ("Visa äldre" visar laddning, HIST-8). */
    val loadingOlder: Boolean = false,
    val jump: DiaryJump? = null
)

/** HIST-6: rulla listan till raden [key]; [serial] skiljer två val av samma dag åt; [emptyDay] är den valda dagen när den saknar poster. */
data class DiaryJump(val key: String, val serial: Int, val emptyDay: LocalDate? = null)

sealed interface DiaryEvent {
    /** Chippet "Alla" (HIST-2). */
    data object ShowAll : DiaryEvent

    /** Ett typchip (HIST-2): [DiaryFilter.toggle]. */
    data class Toggle(val type: DiaryType) : DiaryEvent

    /** Välj dag (HIST-6): rulla till dagen – ett äldre år läses in först (HIST-8). */
    data class JumpTo(val date: LocalDate) : DiaryEvent

    /** Listan har rullat dit ([DiaryControls.jump]). */
    data object JumpDone : DiaryEvent

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
 * de tidigare läsningarna står kvar, och listan byts aldrig mot laddning (scrollpositionen behålls). Läsbudgeten
 * (NFR-24): bara de senaste 30 dagarna (`recentDays`) har lyssnare; resten av år 0 och de äldre åren läses i
 * historikläget, som följer cachen – en ändrad eller raderad äldre post syns direkt.
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
    private val zone: Provider<TimeZone>
) : ViewModel() {
    /** Idag ur klockan vid varje prenumeration (inget gammalt startvärde efter en paus), igen vid midnatt. */
    private val today: Flow<LocalDate> = clock.days { zone.get() }.distinctUntilChanged().shareIn(viewModelScope, screenSharing(), replay = 1)

    /** Årsgränsernas ankare (HIST-8): dagen ViewModeln skapades – midnatt flyttar bara år 0:s slut. */
    private val anchor: LocalDate = clock.todayIn(zone.get())

    private val filter = MutableStateFlow(DiaryFilter())

    /** Dagen Välj dag gav och valets löpnummer; `null` när listan har rullat dit. */
    private val jumpTo = MutableStateFlow<Pair<LocalDate, Int>?>(null)
    private var jumps = 0
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
            // NFR-24: lyssnare bara på år 0:s senaste 30 dagar; äldre dagar och år i historikläget.
            val live = if (index == 0) listOf(recentDays(to)) else emptyList()
            combine(
                screenings.observeDays(from, to, live),
                activities.observeDays(from, to, live),
                doses.observeDays(doseFrom, to, live),
                events.observeDays(from, to, live)
            ) { s, a, d, e ->
                Result.success(DiarySources(screenings = s, activities = a, doses = d, events = e))
            }
                // Ett läsfel delas som värde – ett fel i en delad läsning får inte fälla ViewModeln; det kastas i tidslinjen.
                .catch { emit(Result.failure(it)) }
                .shareIn(viewModelScope, screenSharing(), replay = 1)
        }
    }

    /** Fönstrets år, äldsta sist; ett år till lägger bara till en läsning, och det som visas står kvar tills den kommit. */
    private val windowed: Flow<Pair<DiaryWindow, DiarySources>> = window.flatMapLatest { window ->
        combine(List(window.years) { year(window, it) }) { reads -> window to reads.fold(DiarySources()) { all, read -> all + read.getOrThrow() } }
    }

    /** HIST-9: alla episoder (en användare har få) med sina incheckningar; lyssnas om bara när episoderna byts. */
    private val illness: Flow<DiarySources> = illnesses.observeEpisodes().combineByKey(
        { episodes -> episodes.map { it.id } },
        { ids -> if (ids.isEmpty()) flowOf(emptyMap()) else combine(ids.map { id -> illnesses.observeCheckins(id).map { id to it } }) { it.toMap<String, List<Checkin>>() } }
    ) { episodes, checkins -> DiarySources(episodes = episodes, checkins = checkins) }

    /** Namnen på aktiviteter och händelser ur Listor – arkiverade med; ett läsfel ger bara typens namn (`withFallback`). */
    private val names: Flow<Map<String, String>> = combine(
        options.names(OptionKind.ACTIVITY),
        options.names(OptionKind.EVENT)
    ) { activities, events -> activities + events }.distinctUntilChanged()

    /** Tidslinjen för det lästa fönstret [window] (alla typer) med namnen ur Listor. */
    private data class Timeline(val window: DiaryWindow, val entries: List<DiaryEntry>, val names: Map<String, String>)

    /** Posterna räknas **en gång** per läsning (`:core`); laddning, fel och "Försök igen" via den delade [DetailLoader]. */
    private val loader = DetailLoader(
        combine(windowed, illness, names) { (window, sources), illness, names -> Timeline(window, window.within(diaryEntries(sources + illness, zone.get())), names) },
        viewModelScope,
        AppErrors
    )

    /** Tidslinjen med filtret (HIST-2) – **ett** flöde som både listans rader och kalenderns punkter läser. */
    private val filtered: StateFlow<DetailUiState<Timeline>> = combine(loader.state, filter) { state, filter ->
        if (state is DetailUiState.Content) DetailUiState.Content(state.value.copy(entries = state.value.entries.shownBy(filter))) else state
    }.stateIn(viewModelScope, screenSharing(), DetailUiState.Loading)

    /** Listans tillstånd: laddning och fel ur läsningen, annars en rad per post under sin dag (HIST-1). */
    val state: StateFlow<ListUiState<DiaryRow>> = combine(filtered, today) { state, today ->
        when (state) {
            DetailUiState.Loading -> ListUiState.Loading
            is DetailUiState.Error -> ListUiState.Error(state.error)
            is DetailUiState.Content -> rows(state.value, today).toListUiState()
        }
    }.stateIn(viewModelScope, screenSharing(), ListUiState.Loading)

    private fun rows(timeline: Timeline, today: LocalDate): List<DiaryRow> = diaryDays(timeline.entries, today).flatMap { day -> day.entries.map { row(it, day.label, timeline.names) } }

    private fun row(entry: DiaryEntry, label: DayLabel, names: Map<String, String>): DiaryRow.Entry {
        val optionId = when (entry) {
            is DiaryEntry.Action -> entry.activity.optionId
            is DiaryEntry.Happening -> entry.event.optionId
            else -> null
        }
        return DiaryRow.Entry(entry, label, optionId?.let(names::get))
    }

    private data class Shown(val filter: DiaryFilter, val years: Int, val jumpTo: Pair<LocalDate, Int>?)

    /** Det som styr vyn, med kalenderns punkter ur samma filtrerade tidslinje som listan. */
    val controls: StateFlow<DiaryControls> = combine(
        combine(filter, years, jumpTo, ::Shown),
        today,
        filtered
    ) { shown, today, filtered ->
        val loaded = (filtered as? DetailUiState.Content)?.value
        // Rullningen väntar tills dagens år är inläst; sedan dagens första post, eller närmaste äldre dag.
        val jump = shown.jumpTo?.let { (date, serial) ->
            loaded?.takeIf { it.window.years >= shown.years }?.let { jumpTarget(it.entries, date) }?.let { DiaryJump(it.id, serial, date.takeIf { day -> day != it.date }) }
        }
        DiaryControls(
            today = today,
            filter = shown.filter,
            datesWithEntries = loaded?.entries?.dates().orEmpty().toImmutableSet(),
            years = shown.years,
            loadingOlder = loaded != null && loaded.window.years < shown.years,
            jump = jump
        )
    }.stateIn(viewModelScope, screenSharing(), DiaryControls(anchor))

    private val failures = WriteFailures(viewModelScope, AppErrors)
    val failure: StateFlow<UiError?> = failures.failure

    fun onEvent(event: DiaryEvent) {
        when (event) {
            DiaryEvent.ShowAll -> setFilter(filter.value.showAll())

            is DiaryEvent.Toggle -> setFilter(filter.value.toggle(event.type))

            is DiaryEvent.JumpTo -> jumpTo(event.date)

            DiaryEvent.JumpDone -> jumpTo.value = null

            DiaryEvent.ShowOlder -> years.value += 1

            is DiaryEvent.Delete -> delete(event.entry)

            DiaryEvent.Retry -> {
                // Ett delat läsfel ligger kvar i sin läsning – "Försök igen" läser om alla år.
                yearReads.clear()
                loader.retry()
            }

            DiaryEvent.ErrorShown -> failures.clear()
        }
    }

    /** Ett nytt filter släpper en väntande Välj dag – listan rullar aldrig av ett filterbyte. */
    private fun setFilter(value: DiaryFilter) {
        jumpTo.value = null
        filter.value = value
    }

    /** HIST-6: framtida dagar går inte att välja; ett äldre år läses in först (HIST-8). */
    private fun jumpTo(date: LocalDate) {
        if (date > clock.todayIn(zone.get())) return
        cover(date)
        jumpTo.value = date to ++jumps
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
        failures.write(action)
    }
}
