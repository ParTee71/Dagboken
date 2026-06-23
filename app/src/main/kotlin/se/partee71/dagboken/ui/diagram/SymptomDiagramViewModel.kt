package se.partee71.dagboken.ui.diagram

import androidx.compose.ui.graphics.Color
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
import java.time.LocalDate
import javax.inject.Inject

private val SYMPTOM_PALETTE = listOf(
    Color(0xFF60a5fa),  // blue
    Color(0xFFfb923c),  // orange
    Color(0xFF4ade80),  // green
    Color(0xFFa78bfa),  // violet
    Color(0xFFf472b6),  // pink
    Color(0xFFfbbf24),  // amber
    Color(0xFF34d399),  // teal
)

fun symptomColor(name: String, allSymptoms: List<String>): Color =
    SYMPTOM_PALETTE[allSymptoms.indexOf(name).coerceAtLeast(0) % SYMPTOM_PALETTE.size]

data class SymptomDiagramUiState(
    val rangeDays: Int = 30,
    val allSymptoms: List<String> = emptyList(),
    val selectedSymptoms: Set<String> = emptySet(),
    val series: List<ChartSeries> = emptyList(),
    val days: List<String> = emptyList(),
)

@HiltViewModel
class SymptomDiagramViewModel @Inject constructor(
    private val repo: AktiviteterRepository,
) : ViewModel() {

    private val _rangeDays        = MutableStateFlow(30)
    private val _selectedSymptoms = MutableStateFlow<Set<String>>(emptySet())
    private var hasAutoSelected   = false

    private val _state = MutableStateFlow(SymptomDiagramUiState())
    val state: StateFlow<SymptomDiagramUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repo.all, _rangeDays, _selectedSymptoms) { entries, range, selected ->
                Triple(entries, range, selected)
            }.collectLatest { (entries, range, selected) ->
                val cutoff  = LocalDate.now().minusDays(range.toLong()).toString()
                val inRange = entries.filter { it.datum >= cutoff && it.symptom.isNotBlank() }

                // Rank symptoms by how often they appear across all entries
                val frequency = mutableMapOf<String, Int>()
                inRange.forEach { entry ->
                    SymptomUtils.decode(entry.symptom).keys.forEach { name ->
                        frequency[name] = (frequency[name] ?: 0) + 1
                    }
                }
                val allSymptoms = frequency.entries.sortedByDescending { it.value }.map { it.key }

                // Auto-select top 2 exactly once on first load with data; afterwards honour user selection
                val effectiveSelected = if (!hasAutoSelected && allSymptoms.isNotEmpty()) {
                    hasAutoSelected = true
                    allSymptoms.take(2).toSet()
                } else {
                    selected.intersect(allSymptoms.toSet())
                }
                if (effectiveSelected != selected) _selectedSymptoms.value = effectiveSelected

                // Group by date → average score per symptom per day
                val byDay = inRange
                    .groupBy { it.datum }
                    .entries
                    .sortedBy { it.key }
                    .map { (datum, group) ->
                        val accumulated = mutableMapOf<String, MutableList<Int>>()
                        group.forEach { entry ->
                            SymptomUtils.decode(entry.symptom).forEach { (name, score) ->
                                accumulated.getOrPut(name) { mutableListOf() }.add(score)
                            }
                        }
                        datum to accumulated.mapValues { (_, scores) -> scores.average().toFloat() }
                    }

                val series = effectiveSelected.map { name ->
                    ChartSeries(
                        label  = name,
                        color  = symptomColor(name, allSymptoms),
                        points = byDay.map { (_, dayScores) -> dayScores[name] },
                    )
                }

                _state.value = SymptomDiagramUiState(
                    rangeDays        = range,
                    allSymptoms      = allSymptoms,
                    selectedSymptoms = effectiveSelected,
                    series           = series,
                    days             = byDay.map { it.first },
                )
            }
        }
    }

    fun setRange(days: Int) { _rangeDays.value = days }

    fun toggleSymptom(name: String) {
        val current = _selectedSymptoms.value
        _selectedSymptoms.value = if (name in current) current - name else current + name
    }
}
