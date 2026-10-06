package se.partee71.dagboken.ui.trends

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.core.engine.EventIllnessTrend
import se.partee71.dagboken.core.engine.IntervalPoint
import se.partee71.dagboken.core.engine.StressSeries
import se.partee71.dagboken.core.engine.TrendRange
import se.partee71.dagboken.core.engine.TrendSerie
import se.partee71.dagboken.core.engine.dailyEnergyPoints
import se.partee71.dagboken.core.engine.earliestDate
import se.partee71.dagboken.core.engine.energyByOccasion
import se.partee71.dagboken.core.engine.eventIllnessTrend
import se.partee71.dagboken.core.engine.hasPreviousPeriod
import se.partee71.dagboken.core.engine.previousDays
import se.partee71.dagboken.core.engine.readFrom
import se.partee71.dagboken.core.engine.stressSeries
import se.partee71.dagboken.core.engine.symptomSeries
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.data.common.combineByKey
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.repository.ActivityRepository
import se.partee71.dagboken.data.repository.EventRepository
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.dagboken.ui.common.days

/** Diagramgrupperna i Trender (TRD-19). Klocka och Jämför byggs i #267. */
enum class TrendGroup { MOOD, WATCH, COMPARE }

/** Korten i gruppen Mående (TRD-1, TRD-21), i visningsordning. */
enum class TrendCard(val hasSeriesPicker: Boolean, val hasPreviousPeriod: Boolean) {
    ENERGY_DAY(hasSeriesPicker = false, hasPreviousPeriod = false),
    ENERGY_OCCASION(hasSeriesPicker = true, hasPreviousPeriod = true),
    STRESS(hasSeriesPicker = true, hasPreviousPeriod = true),
    SYMPTOMS(hasSeriesPicker = true, hasPreviousPeriod = true),
    EVENTS_ILLNESS(hasSeriesPicker = false, hasPreviousPeriod = false),
    ;

    /** Serierna som är valda när kortet öppnas första gången: frukost (TRD-1, som 3.x), stress; symptomen väljs. */
    val defaultSelection: Set<String>
        get() = when (this) {
            ENERGY_OCCASION -> setOf(Occasion.BREAKFAST.wire)
            STRESS -> setOf(StressSeries.STRESS.name)
            else -> emptySet()
        }
}

/**
 * Det användaren styr i ett kort: utfällt (TRD-14, stängt från början), period (TRD-3), "Föregående period"
 * (TRD-18, av från början) och valda serier (nycklar ur `TrendSerie.key`).
 */
data class CardControls(
    val expanded: Boolean = false,
    val range: TrendRange = TrendRange.DEFAULT,
    val previous: Boolean = false,
    val selected: Set<String> = emptySet(),
) {
    /** Tillvalet visas bara för kort som kan jämföra och en period som har en föregående (inte "Allt"). */
    fun showsPrevious(card: TrendCard): Boolean = card.hasPreviousPeriod && range.hasPreviousPeriod

    /** Föregående period ritas när tillvalet är på och finns. */
    fun comparesPrevious(card: TrendCard): Boolean = previous && showsPrevious(card)
}

/** En series namn ur Listor (symptomen; `null` också när alternativet saknas); `null` för fasta serier, vars namn skärmen slår upp på nyckeln. */
data class SeriesInfo(val key: String, val name: String?)

/** Det ett utfällt kort visar – räknat i `:core`, utlagt på periodens [days]. */
sealed interface CardData {
    val days: List<LocalDate>

    /** Energi per dag (TRD-8): dagens spann och dagsvärde, `null` = lucka. */
    data class EnergyDay(override val days: List<LocalDate>, val points: List<IntervalPoint?>) : CardData

    /**
     * Ett linjediagram (TRD-1, TRD-2, TRD-18): alla serier kortet erbjuder ([available]), de valda ([shown], i
     * samma ordning) och föregående periods motsvarigheter ([previous], tom när tillvalet är av).
     */
    data class Lines(
        override val days: List<LocalDate>,
        val available: List<SeriesInfo>,
        val shown: List<TrendSerie>,
        val previous: List<TrendSerie>,
    ) : CardData

    /** Händelser och sjukdom (TRD-21). */
    data class EventsIllness(override val days: List<LocalDate>, val trend: EventIllnessTrend) : CardData
}

/** Ett korts läge: det användaren styr och – när kortet lästs – datan, som står kvar när det fälls ihop. */
data class TrendCardState(val controls: CardControls, val data: CardData? = null)

data class TrendsUiState(
    val group: TrendGroup = TrendGroup.MOOD,
    val cards: Map<TrendCard, TrendCardState> = TrendCard.entries.associateWith { TrendCardState(CardControls(selected = it.defaultSelection)) },
)

sealed interface TrendsEvent {
    data class ShowGroup(val group: TrendGroup) : TrendsEvent

    /** Titelraden: fäll ut eller ihop (TRD-14). */
    data class Toggle(val card: TrendCard) : TrendsEvent

    /** Kortets period (TRD-3) – zoom och panorering nollställs i diagrammet när x-axeln byts (TRD-10). */
    data class SetRange(val card: TrendCard, val range: TrendRange) : TrendsEvent

    /** "Föregående period" (TRD-18). */
    data class SetPrevious(val card: TrendCard, val enabled: Boolean) : TrendsEvent

    /** En serie till eller från i kortets serieval (TRD-2). */
    data class ToggleSeries(val card: TrendCard, val key: String) : TrendsEvent
}

/**
 * Fliken Trender, gruppen Mående (TRD-1–TRD-3, TRD-8, TRD-14, TRD-15, TRD-18, TRD-19, TRD-21): varje kort läser sin
 * egen period – **först när det fällts ut** (TRD-15) – och all matematik görs i `:core` (`TrendSeries`,
 * `TrendRange`, `PreviousPeriod`). Två kort med samma läsning delar den; ett kort som fälls ihop slutar läsa
 * men behåller sin senaste data till sammanfattningen. Läsfel visar tomt läge och försöker igen (`withFallback`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val screenings: ScreeningRepository,
    private val activities: ActivityRepository,
    private val events: EventRepository,
    private val illnesses: IllnessRepository,
    options: OptionsRepository,
    clock: Clock,
    private val zone: Provider<TimeZone>,
) : ViewModel() {
    /** Dagens datum, igen vid midnatt – då rensas läsningarna som slutade på gårdagen (ingen nyckel lever kvar per dag). */
    private val today: Flow<LocalDate> = clock.days { zone.get() }.distinctUntilChanged()
        .onEach { today -> reads.keys.removeAll { it.second.endInclusive != today } }
        .shareIn(viewModelScope, sharing, replay = 1)

    private val group = MutableStateFlow(TrendGroup.MOOD)
    private val controls = MutableStateFlow(TrendsUiState().cards.mapValues { it.value.controls })

    /** Symptomens namn ur Listor (arkiverade med, SET-11); vid läsfel bara id:n. */
    private val symptomNames: Flow<Map<String, String>> =
        options.observe(OptionKind.SYMPTOM).withFallback(emptyList()).map { all -> all.associate { it.id to it.name } }.distinctUntilChanged()

    /**
     * Delade läsningar per källa och intervall – två kort med samma period läser en gång, och ett kort som byter
     * period eller fälls ihop och ut igen prenumererar inte om en läsning som redan går. Nycklarna slutar alltid
     * på idag, så [today] rensar gårdagens vid midnatt; cachen är därmed aldrig större än antalet aktiva läsningar.
     */
    private val reads = mutableMapOf<Pair<String, ClosedRange<LocalDate>>, Flow<*>>()

    /** Antal cachade läsningar – för testet att cachen inte växer över dagbyten. */
    internal val cachedReads: Int get() = reads.size

    @Suppress("UNCHECKED_CAST")
    private fun <T> shared(source: String, range: ClosedRange<LocalDate>, fallback: T, create: () -> Flow<T>): Flow<T> =
        reads.getOrPut(source to range) { create().withFallback(fallback).shareIn(viewModelScope, sharing, replay = 1) } as Flow<T>

    private fun screenings(from: LocalDate, to: LocalDate) = shared("screenings", from..to, emptyList<Screening>()) { screenings.observeDays(from, to) }

    private fun activities(from: LocalDate, to: LocalDate) = shared("activities", from..to, emptyList<Activity>()) { activities.observeDays(from, to) }

    private fun events(from: LocalDate, to: LocalDate) = shared("events", from..to, emptyList<Event>()) { events.observeDays(from, to) }

    /**
     * HIST-9-mönstret: alla episoder (en användare har få) med sina incheckningar; incheckningarna lyssnas om bara
     * när episoderna byts. Oberoende av period, så nyckeln är hela tiden fram till idag – delad av varje
     * periodbyte och utfällning.
     */
    private fun illness(to: LocalDate): Flow<Illness> = shared("illness", TrendRange.ALL_FROM..to, Illness(emptyList(), emptyMap())) {
        illnesses.observeEpisodes().combineByKey(
            { episodes -> episodes.map { it.id } },
            { ids -> if (ids.isEmpty()) flowOf(emptyMap()) else combine(ids.map { id -> illnesses.observeCheckins(id).map { id to it } }) { it.toMap() } },
        ) { episodes, checkins -> Illness(episodes, checkins) }
    }

    private data class Illness(val episodes: List<IllnessEpisode>, val checkins: Map<String, List<Checkin>>)

    /** Perioden ett kort läser och visar: posterna från [from] till idag, x-axeln [days] och föregående periods dagar. */
    private class Period(val card: TrendCard, val controls: CardControls, val today: LocalDate) {
        val from: LocalDate = controls.range.readFrom(today, controls.comparesPrevious(card))
        fun days(earliest: LocalDate?) = controls.range.days(today, earliest)
        val previous: List<LocalDate>? = if (controls.comparesPrevious(card)) controls.range.previousDays(today) else null
    }

    /** Kortets data medan det är utfällt; ingenting läses i stängt läge (TRD-15). Det senaste står kvar efter ihopfällning. */
    private fun cardData(card: TrendCard): Flow<CardData?> =
        combine(controls.map { it.getValue(card) }.distinctUntilChanged(), today) { c, t -> Period(card, c, t) }
            .flatMapLatest { period -> if (!period.controls.expanded) flowOf(null) else load(period) }
            .scan(null as CardData?) { last, next -> next ?: last }

    private fun load(period: Period): Flow<CardData> {
        val (from, to) = period.from to period.today
        return when (period.card) {
            TrendCard.ENERGY_DAY -> screenings(from, to).map { s ->
                val days = period.days(earliestDate(s.map { it.date }))
                CardData.EnergyDay(days, dailyEnergyPoints(s, days))
            }
            TrendCard.ENERGY_OCCASION -> screenings(from, to).map { s -> lines(period, earliestDate(s.map { it.date })) { days -> energyByOccasion(s, days) } }
            TrendCard.STRESS -> combine(screenings(from, to), activities(from, to)) { s, a ->
                lines(period, earliest(s, a)) { days -> stressSeries(s, a, days) }
            }
            TrendCard.SYMPTOMS -> combine(screenings(from, to), activities(from, to), symptomNames) { s, a, names ->
                lines(period, earliest(s, a), names) { days -> symptomSeries(s, a, days) }
            }
            TrendCard.EVENTS_ILLNESS -> combine(events(from, to), illness(to)) { e, (episodes, checkins) ->
                val earliest = earliestDate(e.map { it.date } + episodes.map { it.start } + checkins.values.flatten().map { it.date })
                val days = period.days(earliest)
                CardData.EventsIllness(days, eventIllnessTrend(e, episodes, checkins, days))
            }
        }
    }

    private fun earliest(s: List<Screening>, a: List<Activity>) = earliestDate(s.map { it.date } + a.map { it.date })

    /** Ett linjediagram: serierna för perioden och – när tillvalet är på – samma serier för föregående period (TRD-18). */
    private fun lines(period: Period, earliest: LocalDate?, names: Map<String, String> = emptyMap(), series: (List<LocalDate>) -> List<TrendSerie>): CardData.Lines {
        val days = period.days(earliest)
        val all = series(days)
        val selected = period.controls.selected
        val keys = all.map { it.key }
        // Symptomen är dynamiska: periodens symptom kan väljas, namnet kommer ur Listor (saknas det sätter skärmen
        // ett allmänt namn), och ett valt symptom utan data i perioden står kvar i menyn så att det går att avmarkera.
        val symptoms = period.card == TrendCard.SYMPTOMS
        val available = (keys + if (symptoms) (selected - keys.toSet()).sorted() else emptyList())
            .map { key -> SeriesInfo(key, if (symptoms) names[key] else null) }
        val shown = all.filter { it.key in selected }
        // Föregående period visar bara serier som också visas i den nuvarande (samma x-index, samma färg).
        val current = shown.map { it.key }.toSet()
        val previous = period.previous?.let { days -> series(days).filter { it.key in current } }.orEmpty()
        return CardData.Lines(days, available, shown, previous)
    }

    private val data: Flow<Map<TrendCard, CardData?>> = combine(TrendCard.entries.map { card -> cardData(card).map { card to it } }) { it.toMap() }

    val state: StateFlow<TrendsUiState> = combine(group, controls, data) { group, controls, data ->
        TrendsUiState(group, TrendCard.entries.associateWith { TrendCardState(controls.getValue(it), data[it]) })
    }.stateIn(viewModelScope, sharing, TrendsUiState())

    fun onEvent(event: TrendsEvent) {
        when (event) {
            is TrendsEvent.ShowGroup -> group.value = event.group
            is TrendsEvent.Toggle -> update(event.card) { it.copy(expanded = !it.expanded) }
            is TrendsEvent.SetRange -> update(event.card) { it.copy(range = event.range) }
            is TrendsEvent.SetPrevious -> update(event.card) { it.copy(previous = event.enabled) }
            is TrendsEvent.ToggleSeries -> update(event.card) { it.copy(selected = if (event.key in it.selected) it.selected - event.key else it.selected + event.key) }
        }
    }

    private fun update(card: TrendCard, change: (CardControls) -> CardControls) {
        controls.value = controls.value + (card to change(controls.value.getValue(card)))
    }

    private companion object {
        /** Delade flöden följer skärmen och glömmer sitt senaste värde när de stoppats – Trender öppnas alltid stängt (TRD-14). */
        val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0)
    }
}
