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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.core.engine.CompareKey
import se.partee71.dagboken.core.engine.ComparedSerie
import se.partee71.dagboken.core.engine.EventIllnessTrend
import se.partee71.dagboken.core.engine.IntervalPoint
import se.partee71.dagboken.core.engine.SLEEP_METRICS
import se.partee71.dagboken.core.engine.SLEEP_SCORE_KEY
import se.partee71.dagboken.core.engine.StackedPoint
import se.partee71.dagboken.core.engine.StressSeries
import se.partee71.dagboken.core.engine.TrendRange
import se.partee71.dagboken.core.engine.TrendSerie
import se.partee71.dagboken.core.engine.WATCH_COMPARE_KEYS
import se.partee71.dagboken.core.engine.WatchMetric
import se.partee71.dagboken.core.engine.ageFromBirthYear
import se.partee71.dagboken.core.engine.cappedDays
import se.partee71.dagboken.core.engine.cappedReadFrom
import se.partee71.dagboken.core.engine.compareSeries
import se.partee71.dagboken.core.engine.dailyEnergyPoints
import se.partee71.dagboken.core.engine.earliestDate
import se.partee71.dagboken.core.engine.energyByOccasion
import se.partee71.dagboken.core.engine.eventIllnessTrend
import se.partee71.dagboken.core.engine.hasPreviousPeriod
import se.partee71.dagboken.core.engine.moodCompareSeries
import se.partee71.dagboken.core.engine.previousDays
import se.partee71.dagboken.core.engine.readFrom
import se.partee71.dagboken.core.engine.sleepQualitySeries
import se.partee71.dagboken.core.engine.sleepStagePoints
import se.partee71.dagboken.core.engine.stressSeries
import se.partee71.dagboken.core.engine.symptomSeries
import se.partee71.dagboken.core.engine.watchCompareSeries
import se.partee71.dagboken.core.engine.watchSeries
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Profile
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.data.common.combineByKey
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.health.HealthRepository
import se.partee71.dagboken.data.health.HealthStatus
import se.partee71.dagboken.data.repository.ActivityRepository
import se.partee71.dagboken.data.repository.EventRepository
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.dagboken.ui.common.days

/** Diagramgrupperna i Trender (TRD-19): Mående, Klocka och Jämför. */
enum class TrendGroup { MOOD, WATCH, COMPARE }

/**
 * Korten i Trender (TRD-1, TRD-11, TRD-15–TRD-17, TRD-21), i visningsordning inom sin [group]. Alla kort går på
 * samma maskineri – utfällning, period, serieval, föregående period – och skiljer sig bara i vad de läser och räknar.
 */
enum class TrendCard(val group: TrendGroup, val hasSeriesPicker: Boolean, val hasPreviousPeriod: Boolean) {
    ENERGY_DAY(TrendGroup.MOOD, hasSeriesPicker = false, hasPreviousPeriod = false),
    ENERGY_OCCASION(TrendGroup.MOOD, hasSeriesPicker = true, hasPreviousPeriod = true),
    STRESS(TrendGroup.MOOD, hasSeriesPicker = true, hasPreviousPeriod = true),
    SYMPTOMS(TrendGroup.MOOD, hasSeriesPicker = true, hasPreviousPeriod = true),
    EVENTS_ILLNESS(TrendGroup.MOOD, hasSeriesPicker = false, hasPreviousPeriod = false),
    STEPS(TrendGroup.WATCH, hasSeriesPicker = false, hasPreviousPeriod = true),
    HEART_RATE(TrendGroup.WATCH, hasSeriesPicker = true, hasPreviousPeriod = true),
    SLEEP(TrendGroup.WATCH, hasSeriesPicker = true, hasPreviousPeriod = true),
    SLEEP_STAGES(TrendGroup.WATCH, hasSeriesPicker = false, hasPreviousPeriod = false),
    SLEEP_QUALITY(TrendGroup.WATCH, hasSeriesPicker = true, hasPreviousPeriod = true),
    EXERCISE(TrendGroup.WATCH, hasSeriesPicker = false, hasPreviousPeriod = true),
    CALORIES(TrendGroup.WATCH, hasSeriesPicker = false, hasPreviousPeriod = true),
    DISTANCE(TrendGroup.WATCH, hasSeriesPicker = false, hasPreviousPeriod = true),
    OXYGEN(TrendGroup.WATCH, hasSeriesPicker = false, hasPreviousPeriod = true),
    BLOOD_PRESSURE(TrendGroup.WATCH, hasSeriesPicker = true, hasPreviousPeriod = true),
    COMPARE(TrendGroup.COMPARE, hasSeriesPicker = true, hasPreviousPeriod = false),
    ;

    /**
     * Serierna som är valda när kortet öppnas första gången (som 3.x): frukost (TRD-1), stress, vilopuls, sömnens
     * total, sömnkvalitetens poäng och blodtryckets båda; symptomen och Jämför väljs (TRD-17).
     */
    val defaultSelection: Set<String>
        get() = when (this) {
            ENERGY_OCCASION -> setOf(Occasion.BREAKFAST.wire)
            STRESS -> setOf(StressSeries.STRESS.name)
            HEART_RATE -> setOf(WatchMetric.RESTING_HEART_RATE.name)
            SLEEP -> setOf(WatchMetric.SLEEP_TOTAL.name)
            SLEEP_QUALITY -> setOf(SLEEP_SCORE_KEY)
            BLOOD_PRESSURE -> setOf(WatchMetric.SYSTOLIC.name, WatchMetric.DIASTOLIC.name)
            else -> emptySet()
        }

    /** Klockan och Jämför läser aldrig längre bakåt än ett år – "Allt" är de senaste 365 dagarna (TRD-15, TRD-17). */
    val cappedHistory: Boolean get() = group != TrendGroup.MOOD

    /** Klockmåtten ett linjekort i Klocka visar (TRD-11, TRD-15); tomt för övriga kort. */
    val metrics: List<WatchMetric>
        get() = when (this) {
            STEPS -> listOf(WatchMetric.STEPS)
            HEART_RATE -> listOf(WatchMetric.RESTING_HEART_RATE, WatchMetric.HEART_RATE_AVG)
            SLEEP -> SLEEP_METRICS
            EXERCISE -> listOf(WatchMetric.EXERCISE)
            CALORIES -> listOf(WatchMetric.ACTIVE_CALORIES)
            DISTANCE -> listOf(WatchMetric.DISTANCE)
            OXYGEN -> listOf(WatchMetric.OXYGEN_SATURATION)
            BLOOD_PRESSURE -> listOf(WatchMetric.SYSTOLIC, WatchMetric.DIASTOLIC)
            else -> emptyList()
        }

    companion object {
        fun inGroup(group: TrendGroup): List<TrendCard> = entries.filter { it.group == group }
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
     * [needsBirthYear]: sömnkvaliteten kan inte räknas förrän födelseåret finns i Profil (HLS-11).
     */
    data class Lines(
        override val days: List<LocalDate>,
        val available: List<SeriesInfo>,
        val shown: List<TrendSerie>,
        val previous: List<TrendSerie>,
        val needsBirthYear: Boolean = false,
    ) : CardData

    /** Händelser och sjukdom (TRD-21). */
    data class EventsIllness(override val days: List<LocalDate>, val trend: EventIllnessTrend) : CardData

    /** Sömnstadierna per natt (TRD-16): en stapel per dag i `SLEEP_STAGE_METRICS` ordning. */
    data class Stacked(override val days: List<LocalDate>, val points: List<StackedPoint>) : CardData

    /**
     * Jämför (TRD-17): alla valbara serier ([available], nycklar ur `CompareKey.wire`, också valda utan data så att de
     * går att avmarkera) och de valda med data, indexerade 0–100 med sitt verkliga min/max ([shown]). [selectedCount]
     * skiljer "välj minst två" från "för lite data"; [needsBirthYear]: sömnkvaliteten valdes men saknar födelseår (HLS-11).
     */
    data class Compare(
        override val days: List<LocalDate>,
        val available: List<SeriesInfo>,
        val shown: List<ComparedSerie>,
        val selectedCount: Int,
        val needsBirthYear: Boolean = false,
    ) : CardData
}

/** Ett korts läge: det användaren styr och – när kortet lästs – datan, som står kvar när det fälls ihop. */
data class TrendCardState(val controls: CardControls, val data: CardData? = null)

/**
 * [healthStatus] är klockans läge (HLS-4, TRD-20): `null` tills det lästs; "saknas" eller "uppdatera" ger bannern
 * överst i Klocka – statusraden med "Ge åtkomst" kommer i #241. Korten visas oavsett, stängda.
 */
data class TrendsUiState(
    val group: TrendGroup = TrendGroup.MOOD,
    val cards: Map<TrendCard, TrendCardState> = TrendCard.entries.associateWith { TrendCardState(CardControls(selected = it.defaultSelection)) },
    val healthStatus: HealthStatus? = null,
) {
    /** Health Connect saknas eller behöver uppdateras (HLS-4) – Klocka visar bannern. */
    val healthMissing: Boolean get() = healthStatus == HealthStatus.UNAVAILABLE || healthStatus == HealthStatus.UPDATE_REQUIRED
}

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
 * Fliken Trender (TRD-1–TRD-3, TRD-8, TRD-11, TRD-14–TRD-19, TRD-21): varje kort läser sin egen period – **först
 * när det fällts ut** (TRD-15) – och all matematik görs i `:core` (`TrendSeries`, `WatchSeries`, `CompareIndex`,
 * `TrendRange`, `PreviousPeriod`). Två kort med samma läsning delar den – också klockans hälsoläsning, som
 * aldrig persisteras (HLS-5) – och ett kort som fälls ihop slutar läsa men behåller sin senaste data till
 * sammanfattningen. Läsfel visar tomt läge och försöker igen (`withFallback`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val screenings: ScreeningRepository,
    private val activities: ActivityRepository,
    private val events: EventRepository,
    private val illnesses: IllnessRepository,
    options: OptionsRepository,
    private val health: HealthRepository,
    settings: SettingsRepository,
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

    /** Profilen för sömnkvalitetens åldersnormer (HLS-11); utan födelseår blir varje natt en lucka. */
    private val profile: Flow<Profile> = settings.settings.withFallback(Settings()).map { it.profile }.distinctUntilChanged()

    /** Klockans läge (HLS-4) – bannern i Klocka; ett fel i statusflödet lämnar läget okänt. */
    private val healthStatus: Flow<HealthStatus?> = health.status.map<HealthStatus, HealthStatus?> { it }.withFallback(null).distinctUntilChanged()

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
     * Klockans dagshistorik för perioden (HLS-12), **en** läsning som delas av alla klockkort och Jämför med samma
     * period (TRD-15); utan Health Connect bara luckor. Läsningen är nycklad på klockans läge: blir Health Connect
     * tillgängligt (eller behörigheten given) läses perioden om och öppna kort fylls på – också efter ett fel som
     * annars vore bestående. Inget sparas (HLS-5).
     */
    private fun health(from: LocalDate, to: LocalDate): Flow<HealthHistory> = healthStatus.flatMapLatest { status ->
        shared("health:$status", from..to, HealthHistory.empty(from, to)) { flow { emit(health.history(from, to).getOrThrow()) } }
    }

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

    /**
     * Perioden ett kort läser och visar: posterna från [from] till idag, x-axeln [days] och föregående periods dagar.
     * Klockan och Jämför kapar "Allt" vid ett år ([TrendCard.cappedHistory]); dagbokens kort går till första loggen.
     */
    private class Period(val card: TrendCard, val controls: CardControls, val today: LocalDate) {
        private val withPrevious = controls.comparesPrevious(card)
        val from: LocalDate = if (card.cappedHistory) controls.range.cappedReadFrom(today, withPrevious) else controls.range.readFrom(today, withPrevious)
        fun days(earliest: LocalDate? = null) = if (card.cappedHistory) controls.range.cappedDays(today) else controls.range.days(today, earliest)
        val previous: List<LocalDate>? = if (withPrevious) controls.range.previousDays(today) else null
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
            TrendCard.SLEEP_STAGES -> health(from, to).map { h -> period.days().let { days -> CardData.Stacked(days, sleepStagePoints(h, days)) } }
            TrendCard.SLEEP_QUALITY -> combine(health(from, to), profile) { h, p ->
                val age = ageFromBirthYear(p.birthYear, period.today)
                lines(period, null) { days -> sleepQualitySeries(h, age, p.sex, days) }.copy(needsBirthYear = age == null)
            }
            TrendCard.STEPS, TrendCard.HEART_RATE, TrendCard.SLEEP, TrendCard.EXERCISE, TrendCard.CALORIES, TrendCard.DISTANCE, TrendCard.OXYGEN, TrendCard.BLOOD_PRESSURE ->
                health(from, to).map { h -> lines(period, null) { days -> watchSeries(h, period.card.metrics, days) } }
            TrendCard.COMPARE -> compare(period)
        }
    }

    /**
     * Jämför (TRD-17): dagbokens serier och klockans i samma kort. Klockan läses bara när någon klockserie är vald
     * – annars räcker dagbokens läsningar, och Health Connect behöver inte svara för att diagrammet ska ritas.
     */
    private fun compare(period: Period): Flow<CardData> {
        val (from, to) = period.from to period.today
        val selected = period.controls.selected
        val watchKeys = selected.mapNotNull { CompareKey.parse(it) }.filter { it.fromWatch }
        val watch = if (watchKeys.isNotEmpty()) health(from, to) else flowOf(HealthHistory())
        return combine(screenings(from, to), activities(from, to), symptomNames, watch, profile) { s, a, names, h, p ->
            val days = period.days()
            val age = ageFromBirthYear(p.birthYear, period.today)
            // Bara de valda klockserierna räknas (sömnkvaliteten poängsätts bara när den är vald); dagbokens serier
            // behövs i sin helhet för menyn (periodens symptom).
            val mood = moodCompareSeries(s, a, days)
            val keys = mood.map { it.key } + WATCH_COMPARE_KEYS.map { it.wire }
            // Ett valt symptom utan data i perioden står kvar i menyn så att det går att avmarkera (som Mående → Symptom).
            val available = (keys + (selected - keys.toSet()).sorted())
                .map { key -> SeriesInfo(key, (CompareKey.parse(key) as? CompareKey.Symptom)?.let { names[it.optionId] }) }
            val chosen = mood.filter { it.key in selected } + watchCompareSeries(h, age, p.sex, days, watchKeys)
            CardData.Compare(days, available, compareSeries(chosen), selected.size, needsBirthYear = age == null && CompareKey.SleepQuality in watchKeys)
        }
    }

    private fun earliest(s: List<Screening>, a: List<Activity>) = earliestDate(s.map { it.date } + a.map { it.date })

    /** Ett linjediagram: serierna för perioden och – när tillvalet är på – samma serier för föregående period (TRD-18). */
    private fun lines(period: Period, earliest: LocalDate?, names: Map<String, String> = emptyMap(), series: (List<LocalDate>) -> List<TrendSerie>): CardData.Lines {
        val days = period.days(earliest)
        val all = series(days)
        // Ett kort utan serieval visar alla sina serier (Steg, Träning … har en; Sömnstadier är ett eget kort).
        val selected = if (period.card.hasSeriesPicker) period.controls.selected else all.map { it.key }.toSet()
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

    val state: StateFlow<TrendsUiState> = combine(group, controls, data, healthStatus) { group, controls, data, status ->
        TrendsUiState(group, TrendCard.entries.associateWith { TrendCardState(controls.getValue(it), data[it]) }, status)
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
