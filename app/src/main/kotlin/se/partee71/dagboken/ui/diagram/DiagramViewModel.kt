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
import se.partee71.dagboken.domain.model.Aktivitet
import java.time.LocalDate
import javax.inject.Inject

data class DailyStats(
    val datum: String,
    val avgEnergy: Float?,
    val avgStress: Float?,
)

data class DiagramUiState(
    val stats: List<DailyStats> = emptyList(),
    val rangeDays: Int = 14,
    val selectedSeries: String = "Energi",
)

@HiltViewModel
class DiagramViewModel @Inject constructor(
    private val repo: AktiviteterRepository,
) : ViewModel() {

    private val _rangeDays = MutableStateFlow(30)
    private val _selectedSeries = MutableStateFlow("Energi")

    private val _state = MutableStateFlow(DiagramUiState(rangeDays = 30))
    val state: StateFlow<DiagramUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repo.all, _rangeDays, _selectedSeries) { entries, range, series ->
                Triple(entries, range, series)
            }.collectLatest { (entries, range, series) ->
                val cutoff = LocalDate.now().minusDays(range.toLong()).toString()
                val inRange = entries.filter { it.datum >= cutoff }
                val stats = inRange
                    .groupBy { it.datum }
                    .entries
                    .sortedBy { it.key }
                    .map { (datum, group) ->
                        DailyStats(
                            datum      = datum,
                            avgEnergy  = group.map { it.energy.toFloat() }.average().toFloat(),
                            avgStress  = group.map { it.stress.toFloat() }.average().toFloat(),
                        )
                    }
                _state.value = DiagramUiState(stats = stats, rangeDays = range, selectedSeries = series)
            }
        }
    }

    fun setRange(days: Int) { _rangeDays.value = days }

    fun setSeries(series: String) { _selectedSeries.value = series }
}
