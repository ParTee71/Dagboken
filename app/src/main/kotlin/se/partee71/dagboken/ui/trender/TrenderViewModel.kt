package se.partee71.dagboken.ui.trender

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.domain.model.DailyRestingHeartRate
import se.partee71.dagboken.domain.model.DailySteps
import se.partee71.dagboken.domain.usecase.DailyEnergyStats
import se.partee71.dagboken.domain.usecase.SymptomUtils
import se.partee71.dagboken.domain.usecase.computeDailyEnergyStats
import se.partee71.dagboken.ui.diagram.ChartSeries
import java.time.LocalDate
import javax.inject.Inject

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
internal enum class TrenderCategory { ENERGI_TILLFALLE, STRESS_BELASTNING, SYMPTOM }

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

/** Färger för Health Connect-diagrammen (TRD-10) — egna, utanför [SERIES_PALETTE]. */
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

data class TrenderUiState(
    val rangeDays: Int = 30,
    val allSeriesLabels: List<String> = ALL_SERIES,
    val symptomLabels: List<String> = emptyList(),
    val selectedSeries: Set<String> = setOf("Energi Frukost"),
    val series: List<ChartSeries> = emptyList(),
    val dates: List<String> = emptyList(),
    /** Energi (dag), TRD-8 — alltid beräknad, oavsett [selectedSeries]. Delad uträkning med Idag (HEM-7). */
    val dailyEnergy: List<DailyEnergyStats> = emptyList(),
    /** Steg per dag (TRD-10, Health Connect) för [rangeDays] — tom om ej kopplat/behörighet saknas. */
    val dailySteps: List<DailySteps> = emptyList(),
    /** Vilopuls per dag (TRD-10, Health Connect) för [rangeDays] — tom om ej kopplat/behörighet saknas. */
    val dailyRestingHeartRate: List<DailyRestingHeartRate> = emptyList(),
)

/** Färg för valfri serie, oavsett om det är en fast aktivitetsserie eller en dynamisk symptomserie. */
fun trenderSeriesColor(name: String, symptomLabels: List<String>) =
    if (name in ALL_SERIES) seriesColor(name) else symptomColor(name, symptomLabels)

/** De valda och renderade [ChartSeries] som hör till [category] (#141). */
internal fun TrenderUiState.seriesFor(category: TrenderCategory): List<ChartSeries> =
    series.filter { s -> categoryOf(s.label) == category }

@HiltViewModel
class TrenderViewModel @Inject constructor(
    private val repo: AktiviteterRepository,
    private val healthRepo: HealthConnectRepository,
) : ViewModel() {

    private val _rangeDays = MutableStateFlow(30)
    private val _selectedSeries = MutableStateFlow(setOf("Energi Frukost"))

    private val _state = MutableStateFlow(TrenderUiState())
    val state: StateFlow<TrenderUiState> = _state.asStateFlow()

    init {
        // Steg/vilopuls (TRD-10) läses fristående från Health Connect, precis som Idag-kortet
        // (HomeViewModel.refreshHealthCard) — ett separat flöde så en misslyckad/ej kopplad
        // hälsokälla inte blockerar de egna loggade aktivitets-/screeningdiagrammen nedan.
        viewModelScope.launch {
            _rangeDays.collectLatest { days ->
                val weekly = runCatching {
                    if (healthRepo.availability() != HealthAvailability.AVAILABLE) return@runCatching null
                    if (!healthRepo.hasAllPermissions()) return@runCatching null
                    healthRepo.readHealthRange(days)
                }.getOrNull()
                _state.update {
                    it.copy(
                        dailySteps = weekly?.dailySteps.orEmpty(),
                        dailyRestingHeartRate = weekly?.dailyRestingHeartRate.orEmpty(),
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(repo.all, _rangeDays, _selectedSeries) { entries, range, selected ->
                Triple(entries, range, selected)
            }.collectLatest { (entries, range, selected) ->
                val cutoff = LocalDate.now().minusDays(range.toLong()).toString()
                val inRange = entries.filter { it.datum >= cutoff }

                val byDay = inRange
                    .groupBy { it.datum }
                    .entries
                    .sortedBy { it.key }
                val dates = byDay.map { it.key }

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

                val symptomScoresByDay = byDay.map { (_, group) ->
                    val accumulated = mutableMapOf<String, MutableList<Int>>()
                    group.forEach { entry ->
                        SymptomUtils.decode(entry.symptom).forEach { (name, score) ->
                            accumulated.getOrPut(name) { mutableListOf() }.add(score)
                        }
                    }
                    accumulated.mapValues { (_, scores) -> scores.average().toFloat() }
                }
                val allSymptoms = symptomScoresByDay
                    .flatMap { it.keys }
                    .distinct()
                    .sorted()

                val allLabels = ALL_SERIES + allSymptoms
                val effectiveSelected = selected.intersect(allLabels.toSet())
                if (effectiveSelected != selected) _selectedSeries.value = effectiveSelected

                val series = effectiveSelected.mapNotNull { name ->
                    when {
                        name in ALL_SERIES -> ChartSeries(
                            label  = name,
                            color  = seriesColor(name),
                            points = dailyStats.map { it.valueFor(name) },
                        )
                        name in allSymptoms -> ChartSeries(
                            label  = name,
                            color  = symptomColor(name, allSymptoms),
                            points = symptomScoresByDay.map { it[name] },
                        )
                        else -> null
                    }
                }

                // .update { it.copy(...) } i stället för .value = — bevarar dailySteps/
                // dailyRestingHeartRate som skrivs av det fristående Health Connect-flödet ovan.
                _state.update {
                    it.copy(
                        rangeDays       = range,
                        allSeriesLabels = allLabels,
                        symptomLabels   = allSymptoms,
                        selectedSeries  = effectiveSelected,
                        series          = series,
                        dates           = dates,
                        dailyEnergy     = computeDailyEnergyStats(inRange),
                    )
                }
            }
        }
    }

    fun setRange(days: Int) { _rangeDays.value = days }

    fun toggleSeries(name: String) {
        val current = _selectedSeries.value
        _selectedSeries.value = if (name in current) current - name else current + name
    }
}
