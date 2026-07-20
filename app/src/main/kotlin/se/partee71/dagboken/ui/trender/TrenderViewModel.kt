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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.DailyRestingHeartRate
import se.partee71.dagboken.domain.model.DailySteps
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
}

private val DEFAULT_RANGES: Map<TrenderSection, TrenderRange> =
    TrenderSection.entries.associateWith { TrenderRange.MONTH }

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

/** Färger för Health Connect-diagrammen (TRD-11) — egna, utanför [SERIES_PALETTE]. */
internal val HEALTH_STEPS_COLOR = Color(0xFF38bdf8)       // sky-400
internal val HEALTH_RESTING_HR_COLOR = Color(0xFFf87171)  // red-400

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

data class TrenderUiState(
    val ranges: Map<TrenderSection, TrenderRange> = DEFAULT_RANGES,
    val selectedSeries: Set<String> = setOf("Energi Frukost"),
    val categoryTrends: Map<TrenderCategory, CategoryTrend> = emptyMap(),
    /** Energi (dag), TRD-8 — alltid beräknad, oavsett [selectedSeries]. Delad uträkning med Idag (HEM-7). */
    val dailyEnergy: List<DailyEnergyStats> = emptyList(),
    /** Steg per dag (TRD-11, Health Connect) för [TrenderSection.STEG]s egna period. */
    val dailySteps: List<DailySteps> = emptyList(),
    /** Vilopuls per dag (TRD-11, Health Connect) för [TrenderSection.VILOPULS]s egna period. */
    val dailyRestingHeartRate: List<DailyRestingHeartRate> = emptyList(),
)

/** Färg för valfri serie, oavsett om det är en fast aktivitetsserie eller en dynamisk symptomserie. */
fun trenderSeriesColor(name: String, symptomLabels: List<String>) =
    if (name in ALL_SERIES) seriesColor(name) else symptomColor(name, symptomLabels)

@HiltViewModel
class TrenderViewModel @Inject constructor(
    private val repo: AktiviteterRepository,
    private val healthRepo: HealthConnectRepository,
) : ViewModel() {

    private val _ranges = MutableStateFlow(DEFAULT_RANGES)
    private val _selectedSeries = MutableStateFlow(setOf("Energi Frukost"))

    private val _state = MutableStateFlow(TrenderUiState())
    val state: StateFlow<TrenderUiState> = _state.asStateFlow()

    init {
        // Speglar hela periodkartan till state (#149) — billigt, oberoende av vilken
        // sektion som ändrades, så RangeSelector-knapparna alltid visar rätt val.
        viewModelScope.launch {
            _ranges.collectLatest { ranges -> _state.update { it.copy(ranges = ranges) } }
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

        // Steg och vilopuls (TRD-11) läses fristående från Health Connect, var och en med
        // sin egen period (#149) — ett separat flöde per sektion så en misslyckad/ej kopplad
        // hälsokälla inte blockerar de egna loggade diagrammen ovan, och så en period ändrad
        // på det ena inte läser om det andra.
        viewModelScope.launch { collectHealthSection(TrenderSection.STEG) { steps, _ -> _state.update { it.copy(dailySteps = steps) } } }
        viewModelScope.launch { collectHealthSection(TrenderSection.VILOPULS) { _, hr -> _state.update { it.copy(dailyRestingHeartRate = hr) } } }
    }

    private suspend fun collectHealthSection(
        section: TrenderSection,
        onResult: (steps: List<DailySteps>, restingHr: List<DailyRestingHeartRate>) -> Unit,
    ) {
        _ranges.map { it.getValue(section) }.distinctUntilChanged().collectLatest { range ->
            // Health Connect-läsningen tar ett fast antal dagar — "Allt" (range.days == null,
            // TRD-3/#144) har ingen nedre datumgräns för de egna loggade serierna, men
            // Health Connect-diagrammen begränsas ändå till ett år bakåt (pragmatisk cap).
            val days = range.days ?: 365
            val weekly = runCatching {
                if (healthRepo.availability() != HealthAvailability.AVAILABLE) return@runCatching null
                if (!healthRepo.hasAllPermissions()) return@runCatching null
                healthRepo.readHealthRange(days)
            }.getOrNull()
            onResult(weekly?.dailySteps.orEmpty(), weekly?.dailyRestingHeartRate.orEmpty())
        }
    }

    fun setRange(section: TrenderSection, range: TrenderRange) {
        _ranges.update { it + (section to range) }
    }

    fun toggleSeries(name: String) {
        val current = _selectedSeries.value
        _selectedSeries.value = if (name in current) current - name else current + name
    }
}
