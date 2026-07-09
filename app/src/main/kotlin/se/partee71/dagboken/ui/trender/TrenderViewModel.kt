package se.partee71.dagboken.ui.trender

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.domain.usecase.SymptomUtils
import se.partee71.dagboken.ui.diagram.ALL_SERIES
import se.partee71.dagboken.ui.diagram.ChartSeries
import se.partee71.dagboken.ui.diagram.DailyStats
import se.partee71.dagboken.ui.diagram.seriesColor
import se.partee71.dagboken.ui.diagram.symptomColor
import se.partee71.dagboken.ui.diagram.valueFor
import java.time.LocalDate
import javax.inject.Inject

data class TrenderUiState(
    val rangeDays: Int = 30,
    val allSeriesLabels: List<String> = ALL_SERIES,
    val symptomLabels: List<String> = emptyList(),
    val selectedSeries: Set<String> = setOf("Energi Frukost"),
    val series: List<ChartSeries> = emptyList(),
    val dates: List<String> = emptyList(),
)

/** Färg för valfri serie, oavsett om det är en fast aktivitetsserie eller en dynamisk symptomserie. */
fun trenderSeriesColor(name: String, symptomLabels: List<String>) =
    if (name in ALL_SERIES) seriesColor(name) else symptomColor(name, symptomLabels)

@HiltViewModel
class TrenderViewModel @Inject constructor(
    private val repo: AktiviteterRepository,
) : ViewModel() {

    private val _rangeDays = MutableStateFlow(30)
    private val _selectedSeries = MutableStateFlow(setOf("Energi Frukost"))

    private val _state = MutableStateFlow(TrenderUiState())
    val state: StateFlow<TrenderUiState> = _state.asStateFlow()

    init {
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

                _state.value = TrenderUiState(
                    rangeDays       = range,
                    allSeriesLabels = allLabels,
                    symptomLabels   = allSymptoms,
                    selectedSeries  = effectiveSelected,
                    series          = series,
                    dates           = dates,
                )
            }
        }
    }

    fun setRange(days: Int) { _rangeDays.value = days }

    fun toggleSeries(name: String) {
        val current = _selectedSeries.value
        _selectedSeries.value = if (name in current) current - name else current + name
    }
}
