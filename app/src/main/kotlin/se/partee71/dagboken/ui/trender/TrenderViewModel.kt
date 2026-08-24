package se.partee71.dagboken.ui.trender

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.DailyHealth
import se.partee71.dagboken.domain.model.HealthHistory
import se.partee71.dagboken.domain.model.NightlySleepQuality
import se.partee71.dagboken.domain.model.SleepQualityKind
import se.partee71.dagboken.domain.model.ageFromBirthYear
import se.partee71.dagboken.domain.model.scoreNightlySleep
import se.partee71.dagboken.domain.usecase.DailyEnergyStats
import se.partee71.dagboken.domain.usecase.SymptomUtils
import se.partee71.dagboken.domain.usecase.computeDailyEnergyStats
import se.partee71.dagboken.ui.diagram.ChartSeries
import java.time.LocalDate
import javax.inject.Inject

/**
 * Trenders periodval (#144) — [days] `null` betyder "Allt" (ingen nedre datumgräns).
 */
enum class TrenderRange(@StringRes val labelRes: Int, val days: Int?) {
    SEVEN_DAYS(R.string.trender_range_7_days, 7),
    FOURTEEN_DAYS(R.string.trender_range_14_days, 14),
    MONTH(R.string.trender_range_month, 30),
    THREE_MONTHS(R.string.trender_range_3_months, 90),
    ALL(R.string.trender_range_all, null),
}

/**
 * Trenders sex diagram (#149) — vart och ett med en egen [TrenderRange], i stället för en
 * gemensam period som styr alla samtidigt. Tre av dem ([ENERGI_TILLFALLE]/[STRESS_BELASTNING]/
 * [SYMPTOM]) motsvarar en [TrenderCategory] (serieval); de övriga tre har inget serieval.
 */
enum class TrenderSection {
    ENERGI_DAG,
    ENERGI_TILLFALLE,
    STRESS_BELASTNING,
    SYMPTOM,
    STEG,
    VILOPULS,
    SOMN,
    SOMNKVALITET,
    TRANING,
    KALORIER,
    STRACKA,
    SYREMATTNAD,
    BLODTRYCK,
}

/**
 * Diagrammen som läser klockdata (HLS-12). Var och en har ett eget diagram med en egen
 * **enhet** — serier med olika enheter delar aldrig y-skala, för då blir den mindre serien
 * en platt linje längs botten (TRD-15).
 */
internal val HEALTH_SECTIONS: Set<TrenderSection> = setOf(
    TrenderSection.STEG,
    TrenderSection.VILOPULS,
    TrenderSection.SOMN,
    TrenderSection.SOMNKVALITET,
    TrenderSection.TRANING,
    TrenderSection.KALORIER,
    TrenderSection.STRACKA,
    TrenderSection.SYREMATTNAD,
    TrenderSection.BLODTRYCK,
)

private val DEFAULT_RANGES: Map<TrenderSection, TrenderRange> =
    TrenderSection.entries.associateWith { TrenderRange.MONTH }

/**
 * Samtliga diagramkort är stängda när Trender öppnas (TRD-14) — ytan rymmer ett tiotal
 * diagram och ett utfällt kort kostar en full diagramkomposition.
 */
/**
 * Health Connect-läsningarna kapas vid ett år bakåt även för perioden "Allt" (TRD-3) —
 * de egna loggade serierna har ingen nedre gräns, men ett obegränsat hälsofönster är varken
 * meningsfullt eller billigt.
 */
private const val HEALTH_HISTORY_MAX_DAYS = 365

private val DEFAULT_EXPANDED: Map<TrenderSection, Boolean> =
    TrenderSection.entries.associateWith { false }

/** De fyra sektioner vars data kommer från loggade aktiviteter/screeningar (inte Health Connect). */
private val CATEGORY_SECTIONS = setOf(
    TrenderSection.ENERGI_DAG, TrenderSection.ENERGI_TILLFALLE,
    TrenderSection.STRESS_BELASTNING, TrenderSection.SYMPTOM,
)

internal val ENERGY_SLOT_SERIES = listOf(
    "Energi Frukost", "Energi Lunch", "Energi Kvällsmat", "Energi Läggdags",
)
internal val STRESS_SERIES = listOf("Stress", "Somatiska", "Återhämtande", "Energitjuv")

internal val ALL_SERIES = ENERGY_SLOT_SERIES + STRESS_SERIES

/**
 * Trenders diagram delas upp per kategori (#141) — ett gemensamt diagram för alla
 * serier ger en gemensam y-skala som gör enskilda serier oläsliga. "Energi (dag)"
 * (TRD-8) hör inte hemma här — den är inte en väljbar [ChartSeries] utan ett eget
 * intervalldiagram, se [TrenderUiState.dailyEnergy].
 */
enum class TrenderCategory { ENERGI_TILLFALLE, STRESS_BELASTNING, SYMPTOM }

internal fun categoryOf(seriesName: String): TrenderCategory = when {
    seriesName in ENERGY_SLOT_SERIES -> TrenderCategory.ENERGI_TILLFALLE
    seriesName in STRESS_SERIES      -> TrenderCategory.STRESS_BELASTNING
    else                             -> TrenderCategory.SYMPTOM
}

private val SERIES_PALETTE = listOf(
    Color(0xFF60a5fa),  // blue-400      (Energi Frukost)
    Color(0xFF34d399),  // emerald-400   (Energi Lunch)
    Color(0xFFfbbf24),  // amber-400     (Energi Kvällsmat)
    Color(0xFFa78bfa),  // violet-400    (Energi Läggdags)
    Color(0xFFfb923c),  // orange-400    (Stress)
    Color(0xFF4ade80),  // green-400     (Somatiska)
    Color(0xFFe879f9),  // fuchsia-400   (Återhämtande)
    Color(0xFFf472b6),  // pink-400      (Energitjuv)
)

internal fun seriesColor(name: String): Color =
    SERIES_PALETTE.getOrElse(ALL_SERIES.indexOf(name)) { SERIES_PALETTE.last() }

/** Färger för Health Connect-diagrammen (TRD-11/TRD-15) — egna, utanför [SERIES_PALETTE]. */
internal val HEALTH_STEPS_COLOR = Color(0xFF38bdf8)       // sky-400
internal val HEALTH_RESTING_HR_COLOR = Color(0xFFf87171)  // red-400
internal val HEALTH_HEART_RATE_COLOR = Color(0xFFfb7185)  // rose-400
internal val HEALTH_EXERCISE_COLOR = Color(0xFFf97316)    // orange-500
internal val HEALTH_KCAL_COLOR = Color(0xFFf59e0b)        // amber-500
internal val HEALTH_DISTANCE_COLOR = Color(0xFF22d3ee)    // cyan-400
internal val HEALTH_SPO2_COLOR = Color(0xFF60a5fa)        // blue-400

/**
 * Sömnstadiernas färger. Definierade på ett ställe så samma stadium ser likadant ut i
 * Sömn-diagrammets linjeserier och i det staplade stadiediagrammet (TRD-16).
 */
internal val SLEEP_TOTAL_COLOR = Color(0xFF818cf8)  // indigo-400
internal val SLEEP_STAGE_COLORS: Map<String, Color> = mapOf(
    "Djup" to Color(0xFF4f46e5),   // indigo-600
    "REM" to Color(0xFFa78bfa),    // violet-400
    "Lätt" to Color(0xFF93c5fd),   // blue-300
    "Vaken" to Color(0xFFfbbf24),  // amber-400
)

/** Sömnkvalitetens poäng och delkomponenter — alla på 0–100, så de delar y-skala. */
internal val SLEEP_QUALITY_COLOR = Color(0xFF34d399)  // emerald-400

/** Blodtryckets två serier, båda i mmHg. */
internal val BLOOD_PRESSURE_COLORS: Map<String, Color> = mapOf(
    "Systoliskt" to Color(0xFFef4444),   // red-500
    "Diastoliskt" to Color(0xFFfb923c),  // orange-400
)

/**
 * En valbar serie i ett hälsodiagram (TRD-15). [value] plockar måttet ur dygnet i
 * diagrammets egen enhet — timmar, minuter, kilometer, kcal, procent, mmHg eller bpm.
 * Ett dygn som saknar måttet ger null, vilket blir en lucka i linjen (HLS-12).
 */
internal data class HealthSeriesSpec(
    val label: String,
    val color: Color,
    val value: (DailyHealth) -> Float?,
)

private fun hours(duration: java.time.Duration?): Float? =
    duration?.let { it.toMinutes() / 60f }

/** Sömnens serier i timmar (TRD-15). */
internal val SLEEP_SERIES: List<HealthSeriesSpec> = listOf(
    HealthSeriesSpec("Total", SLEEP_TOTAL_COLOR) { hours(it.sleepDuration) },
    HealthSeriesSpec("Djup", SLEEP_STAGE_COLORS.getValue("Djup")) { hours(it.sleepStages.deep) },
    HealthSeriesSpec("REM", SLEEP_STAGE_COLORS.getValue("REM")) { hours(it.sleepStages.rem) },
    HealthSeriesSpec("Lätt", SLEEP_STAGE_COLORS.getValue("Lätt")) { hours(it.sleepStages.light) },
    HealthSeriesSpec("Vaken", SLEEP_STAGE_COLORS.getValue("Vaken")) { hours(it.sleepStages.awake) },
)

/** Pulsdiagrammets serier i bpm (TRD-11, utökat med dygnssnittet). */
internal val HEART_RATE_SERIES: List<HealthSeriesSpec> = listOf(
    HealthSeriesSpec("Vilopuls", HEALTH_RESTING_HR_COLOR) { it.restingHeartRate?.toFloat() },
    HealthSeriesSpec("Dygnssnitt", HEALTH_HEART_RATE_COLOR) { it.heartRateAvg?.toFloat() },
)

/** Blodtryckets serier i mmHg (TRD-15). */
internal val BLOOD_PRESSURE_SERIES: List<HealthSeriesSpec> = listOf(
    HealthSeriesSpec("Systoliskt", BLOOD_PRESSURE_COLORS.getValue("Systoliskt")) {
        it.bloodPressure?.systolic?.toFloat()
    },
    HealthSeriesSpec("Diastoliskt", BLOOD_PRESSURE_COLORS.getValue("Diastoliskt")) {
        it.bloodPressure?.diastolic?.toFloat()
    },
)

/** Enkelseriediagrammen — ingen serieväljare, ett mått per diagram. */
internal val STEPS_SERIES = listOf(
    HealthSeriesSpec("Steg", HEALTH_STEPS_COLOR) { it.steps?.toFloat() },
)
internal val EXERCISE_SERIES = listOf(
    HealthSeriesSpec("Träning", HEALTH_EXERCISE_COLOR) { it.exerciseDuration?.toMinutes()?.toFloat() },
)
internal val KCAL_SERIES = listOf(
    HealthSeriesSpec("Aktiva kalorier", HEALTH_KCAL_COLOR) { it.activeEnergyKcal?.toFloat() },
)
internal val DISTANCE_SERIES = listOf(
    HealthSeriesSpec("Sträcka", HEALTH_DISTANCE_COLOR) { it.distanceMeters?.let { m -> (m / 1000.0).toFloat() } },
)
internal val SPO2_SERIES = listOf(
    HealthSeriesSpec("Syremättnad", HEALTH_SPO2_COLOR) { it.oxygenSaturationAvg?.toFloat() },
)

/** Sömnkvalitetens serier — poängen plus HLS-10:s sex delkomponenter, alla 0–100. */
internal val SLEEP_QUALITY_SERIES: List<String> = listOf("Poäng") +
    SleepQualityKind.entries.map { it.seriesLabel }

internal val SleepQualityKind.seriesLabel: String
    get() = when (this) {
        SleepQualityKind.DURATION -> "Längd"
        SleepQualityKind.EFFICIENCY -> "Effektivitet"
        SleepQualityKind.REGULARITY -> "Regelbundenhet"
        SleepQualityKind.DEEP -> "Djupsömn"
        SleepQualityKind.REM -> "REM-andel"
        SleepQualityKind.WASO -> "Vaken tid"
    }

private val SLEEP_QUALITY_COMPONENT_COLORS = listOf(
    Color(0xFF60a5fa), Color(0xFFfb923c), Color(0xFFa78bfa),
    Color(0xFF4ade80), Color(0xFFf472b6), Color(0xFFfbbf24),
)

internal fun sleepQualitySeriesColor(label: String): Color =
    if (label == "Poäng") {
        SLEEP_QUALITY_COLOR
    } else {
        SLEEP_QUALITY_COMPONENT_COLORS[
            SLEEP_QUALITY_SERIES.indexOf(label).coerceAtLeast(1).minus(1) % SLEEP_QUALITY_COMPONENT_COLORS.size,
        ]
    }

/** Vilka serier ett hälsodiagram erbjuder — tom lista för diagram utan serieval. */
internal fun healthSeriesFor(section: TrenderSection): List<HealthSeriesSpec> = when (section) {
    TrenderSection.STEG -> STEPS_SERIES
    TrenderSection.VILOPULS -> HEART_RATE_SERIES
    TrenderSection.SOMN -> SLEEP_SERIES
    TrenderSection.TRANING -> EXERCISE_SERIES
    TrenderSection.KALORIER -> KCAL_SERIES
    TrenderSection.STRACKA -> DISTANCE_SERIES
    TrenderSection.SYREMATTNAD -> SPO2_SERIES
    TrenderSection.BLODTRYCK -> BLOOD_PRESSURE_SERIES
    else -> emptyList()
}

/** Serier som är valda från början i varje hälsodiagram med serieväljare. */
private val DEFAULT_HEALTH_SERIES: Map<TrenderSection, Set<String>> = mapOf(
    TrenderSection.SOMN to setOf("Total"),
    TrenderSection.SOMNKVALITET to setOf("Poäng"),
    TrenderSection.VILOPULS to setOf("Vilopuls"),
    TrenderSection.BLODTRYCK to setOf("Systoliskt", "Diastoliskt"),
)

private val SYMPTOM_PALETTE = listOf(
    Color(0xFF60a5fa),  // blue
    Color(0xFFfb923c),  // orange
    Color(0xFF4ade80),  // green
    Color(0xFFa78bfa),  // violet
    Color(0xFFf472b6),  // pink
    Color(0xFFfbbf24),  // amber
    Color(0xFF34d399),  // teal
)

private fun symptomColor(name: String, allSymptoms: List<String>): Color =
    SYMPTOM_PALETTE[allSymptoms.indexOf(name).coerceAtLeast(0) % SYMPTOM_PALETTE.size]

internal data class DailyStats(
    val datum: String,
    val avgEnergyFrukost: Float?,
    val avgEnergyLunch: Float?,
    val avgEnergyKvallsmat: Float?,
    val avgEnergyLaggdags: Float?,
    val avgStress: Float?,
    val avgSomatiska: Float?,
    val avgAterhamtande: Float?,
    val avgEnergitjuv: Float?,
)

private fun DailyStats.valueFor(seriesName: String): Float? = when (seriesName) {
    "Energi Frukost"   -> avgEnergyFrukost
    "Energi Lunch"     -> avgEnergyLunch
    "Energi Kvällsmat" -> avgEnergyKvallsmat
    "Energi Läggdags"  -> avgEnergyLaggdags
    "Stress"           -> avgStress
    "Somatiska"        -> avgSomatiska
    "Återhämtande"     -> avgAterhamtande
    "Energitjuv"       -> avgEnergitjuv
    else               -> null
}

/** Ett kategoridiagrams renderade data (#149) — [dates] är kategorins egna, beroende av dess [TrenderRange]. */
data class CategoryTrend(
    val labels: List<String> = emptyList(),
    val series: List<ChartSeries> = emptyList(),
    val dates: List<String> = emptyList(),
)

/** Kategorins rådata innan serieval tillämpas — separat steg så [TrenderViewModel] kan
 * räkna fram den korrigerade [TrenderUiState.selectedSeries] innan [ChartSeries] byggs. */
private data class CategoryData(
    val labels: List<String>,
    val dates: List<String>,
    val pointsByLabel: Map<String, List<Float?>>,
    val colorByLabel: Map<String, Color>,
)

private fun filterByRange(entries: List<Aktivitet>, range: TrenderRange): List<Aktivitet> =
    range.days?.let { days ->
        val cutoff = LocalDate.now().minusDays(days.toLong()).toString()
        entries.filter { it.datum >= cutoff }
    } ?: entries

private fun computeCategoryData(entries: List<Aktivitet>, range: TrenderRange, category: TrenderCategory): CategoryData {
    val inRange = filterByRange(entries, range)
    val byDay = inRange.groupBy { it.datum }.entries.sortedBy { it.key }
    val dates = byDay.map { it.key }

    if (category == TrenderCategory.SYMPTOM) {
        val symptomScoresByDay = byDay.map { (_, group) ->
            val accumulated = mutableMapOf<String, MutableList<Int>>()
            group.forEach { entry ->
                SymptomUtils.decode(entry.symptom).forEach { (name, score) ->
                    accumulated.getOrPut(name) { mutableListOf() }.add(score)
                }
            }
            accumulated.mapValues { (_, scores) -> scores.average().toFloat() }
        }
        val allSymptoms = symptomScoresByDay.flatMap { it.keys }.distinct().sorted()
        return CategoryData(
            labels = allSymptoms,
            dates = dates,
            pointsByLabel = allSymptoms.associateWith { name -> symptomScoresByDay.map { it[name] } },
            colorByLabel = allSymptoms.associateWith { name -> symptomColor(name, allSymptoms) },
        )
    }

    val fixedLabels = if (category == TrenderCategory.ENERGI_TILLFALLE) ENERGY_SLOT_SERIES else STRESS_SERIES
    val dailyStats = byDay.map { (datum, group) ->
        val n          = group.size.toFloat()
        val screenings = group.filter { it.type == "screening" }
        fun slotEnergy(slot: String): Float? =
            screenings.filter { it.aktivitet == slot }
                .map { it.energy.toFloat() }
                .average().toFloat()
                .takeIf { it.isFinite() }
        DailyStats(
            datum              = datum,
            avgEnergyFrukost   = slotEnergy("Efter frukost"),
            avgEnergyLunch     = slotEnergy("Lunch"),
            avgEnergyKvallsmat = slotEnergy("Kvällsmat"),
            avgEnergyLaggdags  = slotEnergy("Läggdags"),
            avgStress       = group.map { it.stress.toFloat() }.average().toFloat().takeIf { it.isFinite() },
            avgSomatiska    = group.map { it.somatiska.toFloat() }.average().toFloat().takeIf { it.isFinite() },
            avgAterhamtande = group.count { it.aterhamtande } / n * 10f,
            avgEnergitjuv   = group.count { it.energitjuv } / n * 10f,
        )
    }
    return CategoryData(
        labels = fixedLabels,
        dates = dates,
        pointsByLabel = fixedLabels.associateWith { name -> dailyStats.map { it.valueFor(name) } },
        colorByLabel = fixedLabels.associateWith { name -> seriesColor(name) },
    )
}

/**
 * Ett hälsodiagrams renderade data (TRD-15) — samma form som [CategoryTrend], fast byggd
 * ur [HealthHistory] i stället för loggade aktiviteter. [labels] är diagrammets valbara
 * serier; diagram utan serieval har en enda serie och en tom [labels].
 */
data class HealthTrend(
    val labels: List<String> = emptyList(),
    val series: List<ChartSeries> = emptyList(),
    val dates: List<String> = emptyList(),
)

data class TrenderUiState(
    val ranges: Map<TrenderSection, TrenderRange> = DEFAULT_RANGES,
    /** Utfällt läge per diagramkort (TRD-14) — alla stängda som standard. */
    val expanded: Map<TrenderSection, Boolean> = DEFAULT_EXPANDED,
    val selectedSeries: Set<String> = setOf("Energi Frukost"),
    val categoryTrends: Map<TrenderCategory, CategoryTrend> = emptyMap(),
    /** Energi (dag), TRD-8 — alltid beräknad, oavsett [selectedSeries]. Delad uträkning med Idag (HEM-7). */
    val dailyEnergy: List<DailyEnergyStats> = emptyList(),
    /** Renderad data per hälsodiagram (TRD-15) — tom tills kortet fällts ut. */
    val healthTrends: Map<TrenderSection, HealthTrend> = emptyMap(),
    /** Valda serier per hälsodiagram med serieväljare (TRD-15). */
    val selectedHealthSeries: Map<TrenderSection, Set<String>> = DEFAULT_HEALTH_SERIES,
    /** True medan ett utfällt hälsodiagram väntar på sin läsning från Health Connect. */
    val healthLoading: Set<TrenderSection> = emptySet(),
)

/** Färg för valfri serie, oavsett om det är en fast aktivitetsserie eller en dynamisk symptomserie. */
fun trenderSeriesColor(name: String, symptomLabels: List<String>) =
    if (name in ALL_SERIES) seriesColor(name) else symptomColor(name, symptomLabels)

@HiltViewModel
class TrenderViewModel @Inject constructor(
    private val repo: AktiviteterRepository,
    private val healthRepo: HealthConnectRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _ranges = MutableStateFlow(DEFAULT_RANGES)
    private val _expanded = MutableStateFlow(DEFAULT_EXPANDED)
    private val _selectedSeries = MutableStateFlow(setOf("Energi Frukost"))
    private val _selectedHealthSeries = MutableStateFlow(DEFAULT_HEALTH_SERIES)

    // Läst historik per period. Två diagram som visar samma period delar en läsning, och
    // ett kort som fällts ihop behåller sin data — hälsodata persisteras inte (HLS-5), men
    // att läsa om samma period medan skärmen är öppen vore rent slöseri.
    private val historyCache = mutableMapOf<TrenderRange, HealthHistory>()
    private val sleepQualityCache = mutableMapOf<TrenderRange, List<NightlySleepQuality>>()

    private val _state = MutableStateFlow(TrenderUiState())
    val state: StateFlow<TrenderUiState> = _state.asStateFlow()

    init {
        // Speglar hela periodkartan till state (#149) — billigt, oberoende av vilken
        // sektion som ändrades, så RangeSelector-knapparna alltid visar rätt val.
        viewModelScope.launch {
            _ranges.collectLatest { ranges -> _state.update { it.copy(ranges = ranges) } }
        }

        // Utfällningen (TRD-14) speglas på samma sätt som perioderna. Den styr bara
        // rendering — dataflödena nedan läser oberoende av om kortet är utfällt.
        viewModelScope.launch {
            _expanded.collectLatest { expanded -> _state.update { it.copy(expanded = expanded) } }
        }

        viewModelScope.launch {
            _selectedHealthSeries.collectLatest { selected ->
                _state.update { it.copy(selectedHealthSeries = selected) }
            }
        }

        // Energi (dag) + de tre kategoridiagrammen: var och en filtreras nu på sin
        // egen period (#149) i stället för en delad — smalnas av till just dessa fyra
        // sektioners perioder så ett Steg-/Vilopuls-periodbyte inte triggar om räkningen.
        viewModelScope.launch {
            combine(
                repo.all,
                _ranges.map { it.filterKeys { s -> s in CATEGORY_SECTIONS } }.distinctUntilChanged(),
                _selectedSeries,
            ) { entries, ranges, selected -> Triple(entries, ranges, selected) }
                .collectLatest { (entries, ranges, selected) ->
                    val energiTillfalle = computeCategoryData(entries, ranges.getValue(TrenderSection.ENERGI_TILLFALLE), TrenderCategory.ENERGI_TILLFALLE)
                    val stressBelastning = computeCategoryData(entries, ranges.getValue(TrenderSection.STRESS_BELASTNING), TrenderCategory.STRESS_BELASTNING)
                    val symptom = computeCategoryData(entries, ranges.getValue(TrenderSection.SYMPTOM), TrenderCategory.SYMPTOM)

                    val allLabels = energiTillfalle.labels + stressBelastning.labels + symptom.labels
                    val effectiveSelected = selected.intersect(allLabels.toSet())
                    if (effectiveSelected != selected) _selectedSeries.value = effectiveSelected

                    fun buildTrend(data: CategoryData) = CategoryTrend(
                        labels = data.labels,
                        dates  = data.dates,
                        series = effectiveSelected.filter { it in data.labels }.map { name ->
                            ChartSeries(label = name, color = data.colorByLabel.getValue(name), points = data.pointsByLabel.getValue(name))
                        },
                    )

                    val dailyEnergy = computeDailyEnergyStats(filterByRange(entries, ranges.getValue(TrenderSection.ENERGI_DAG)))

                    _state.update {
                        it.copy(
                            selectedSeries = effectiveSelected,
                            categoryTrends = mapOf(
                                TrenderCategory.ENERGI_TILLFALLE to buildTrend(energiTillfalle),
                                TrenderCategory.STRESS_BELASTNING to buildTrend(stressBelastning),
                                TrenderCategory.SYMPTOM to buildTrend(symptom),
                            ),
                            dailyEnergy = dailyEnergy,
                        )
                    }
                }
        }

        // Hälsodiagrammen (TRD-15) läses fristående från de loggade serierna ovan, så en
        // misslyckad eller okopplad hälsokälla aldrig blockerar dagboksdiagrammen. Läsningen
        // sker först när ett kort fällts ut (TRD-14) — med ett tiotal diagram på ytan vore
        // det slöseri att läsa allt som ingen tittar på.
        viewModelScope.launch {
            combine(_expanded, _ranges, _selectedHealthSeries) { expanded, ranges, selected ->
                Triple(expanded, ranges, selected)
            }.collectLatest { (expanded, ranges, selected) ->
                val open = HEALTH_SECTIONS.filter { expanded[it] == true }
                if (open.isEmpty()) return@collectLatest

                val pending = open.filter { !isLoaded(it, ranges.getValue(it)) }.toSet()
                if (pending.isNotEmpty()) _state.update { it.copy(healthLoading = pending) }

                open.forEach { section -> ensureLoaded(section, ranges.getValue(section)) }

                _state.update { current ->
                    current.copy(
                        healthTrends = current.healthTrends + open.associateWith {
                            buildHealthTrend(it, ranges.getValue(it), selected)
                        },
                        healthLoading = emptySet(),
                    )
                }
            }
        }
    }

    /** Perioden "Allt" (TRD-3) har ingen nedre gräns; hälsoläsningen kapas ändå vid ett år. */
    private fun TrenderRange.healthDays(): Int = days ?: HEALTH_HISTORY_MAX_DAYS

    private fun isLoaded(section: TrenderSection, range: TrenderRange): Boolean =
        if (section == TrenderSection.SOMNKVALITET) {
            sleepQualityCache.containsKey(range)
        } else {
            historyCache.containsKey(range)
        }

    private suspend fun ensureLoaded(section: TrenderSection, range: TrenderRange) {
        if (isLoaded(section, range)) return
        if (section == TrenderSection.SOMNKVALITET) {
            // Poängen är åldersjusterad (HLS-11) — utan födelseår ger scoreNightlySleep
            // luckor i stället för en poäng mot fel norm.
            val nights = readHealth { healthRepo.readSleepMeasurementsHistory(range.healthDays()) }
            if (nights != null) {
                sleepQualityCache[range] = scoreNightlySleep(
                    nights = nights,
                    age    = ageFromBirthYear(prefs.birthYear.first()),
                    sex    = prefs.sex.first(),
                )
            }
        } else {
            val history = readHealth { healthRepo.readHealthHistory(range.healthDays()) }
            // En misslyckad läsning cachas inte — då visas tomläget, och nästa periodbyte
            // eller utfällning försöker igen i stället för att fastna i tomt läge.
            if (history != null) historyCache[range] = history
        }
    }

    /** Null när Health Connect saknas, behörighet fattas eller läsningen kastar. */
    private suspend fun <T> readHealth(block: suspend () -> T): T? = runCatching {
        if (healthRepo.availability() != HealthAvailability.AVAILABLE) return@runCatching null
        if (!healthRepo.hasRequiredPermissions()) return@runCatching null
        block()
    }.getOrNull()

    private fun buildHealthTrend(
        section: TrenderSection,
        range: TrenderRange,
        selected: Map<TrenderSection, Set<String>>,
    ): HealthTrend {
        if (section == TrenderSection.SOMNKVALITET) {
            val nights = sleepQualityCache[range].orEmpty()
            val chosen = selected[section].orEmpty()
            return HealthTrend(
                labels = SLEEP_QUALITY_SERIES,
                dates  = nights.map { it.date.toString() },
                series = SLEEP_QUALITY_SERIES.filter { it in chosen }.map { label ->
                    ChartSeries(
                        label  = label,
                        color  = sleepQualitySeriesColor(label),
                        points = nights.map { sleepQualityValue(it, label) },
                    )
                },
            )
        }

        val history = historyCache[range] ?: HealthHistory()
        val specs = healthSeriesFor(section)
        // Diagram med en enda serie har ingen väljare — serien visas alltid.
        val active = if (specs.size == 1) {
            specs
        } else {
            val chosen = selected[section].orEmpty()
            specs.filter { it.label in chosen }
        }
        return HealthTrend(
            labels = if (specs.size == 1) emptyList() else specs.map { it.label },
            dates  = history.dates.map { it.toString() },
            series = active.map { spec ->
                ChartSeries(spec.label, spec.color, history.days.map { spec.value(it) })
            },
        )
    }

    private fun sleepQualityValue(night: NightlySleepQuality, label: String): Float? {
        val quality = night.quality ?: return null
        if (label == "Poäng") return quality.score.toFloat()
        return quality.components.firstOrNull { it.kind.seriesLabel == label }?.score?.toFloat()
    }

    fun setRange(section: TrenderSection, range: TrenderRange) {
        _ranges.update { it + (section to range) }
    }

    /** Fäller ut/ihop ett enskilt diagramkort (TRD-14) utan att röra de andras läge. */
    fun setExpanded(section: TrenderSection, expanded: Boolean) {
        _expanded.update { it + (section to expanded) }
    }

    /** Väljer till/från en serie i ett hälsodiagram (TRD-15), oberoende av de andra diagrammen. */
    fun toggleHealthSeries(section: TrenderSection, name: String) {
        _selectedHealthSeries.update { current ->
            val chosen = current[section].orEmpty()
            current + (section to if (name in chosen) chosen - name else chosen + name)
        }
    }

    fun toggleSeries(name: String) {
        val current = _selectedSeries.value
        _selectedSeries.value = if (name in current) current - name else current + name
    }
}
