package se.partee71.dagboken.ui.diagram

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
import java.time.LocalDate
import javax.inject.Inject

data class DailyStats(
    val datum: String,
    val avgEnergy: Float?,
    val avgStress: Float?,
)

data class DiagramUiState(
    val stats: List<DailyStats> = emptyList(),
    val rangeDays: Int = 30,
    val visibleSeries: Set<String> = setOf("Energi"),
)

@HiltViewModel
class DiagramViewModel @Inject constructor(
    private val repo: AktiviteterRepository,
) : ViewModel() {

    private val _rangeDays      = MutableStateFlow(30)
    private val _visibleSeries  = MutableStateFlow(setOf("Energi"))

    private val _state = MutableStateFlow(DiagramUiState())
    val state: StateFlow<DiagramUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repo.all, _rangeDays) { entries, range -> entries to range }
                .collectLatest { (entries, range) ->
                    val cutoff = LocalDate.now().minusDays(range.toLong()).toString()
                    val stats = entries
                        .filter { it.datum >= cutoff }
                        .groupBy { it.datum }
                        .entries
                        .sortedBy { it.key }
                        .map { (datum, group) ->
                            DailyStats(
                                datum     = datum,
                                avgEnergy = group.map { it.energy.toFloat() }.average().toFloat(),
                                avgStress = group.map { it.stress.toFloat() }.average().toFloat(),
                            )
                        }
                    _state.value = _state.value.copy(stats = stats, rangeDays = range)
                }
        }
        viewModelScope.launch {
            _visibleSeries.collectLatest { visible ->
                _state.value = _state.value.copy(visibleSeries = visible)
            }
        }
    }

    fun setRange(days: Int) { _rangeDays.value = days }

    fun toggleSeries(series: String) {
        val current = _visibleSeries.value
        _visibleSeries.value = if (series in current) current - series else current + series
    }
}
