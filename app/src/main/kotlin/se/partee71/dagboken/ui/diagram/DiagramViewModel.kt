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
    val avgEnergyFrukost: Float?,
    val avgEnergyLunch: Float?,
    val avgEnergyKvallsmat: Float?,
    val avgEnergyLaggdags: Float?,
    val avgStress: Float?,
    val avgSomatiska: Float?,
    val avgAterhamtande: Float?,
    val avgEnergitjuv: Float?,
)

data class DiagramUiState(
    val stats: List<DailyStats> = emptyList(),
    val rangeDays: Int = 30,
    val visibleSeries: Set<String> = setOf("Energi Frukost"),
)

/** Reused by [DiagramScreen] and Trender-ytan (`ui/trender`) to read a named series off a day's stats. */
internal fun DailyStats.valueFor(seriesName: String): Float? = when (seriesName) {
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

@HiltViewModel
class DiagramViewModel @Inject constructor(
    private val repo: AktiviteterRepository,
) : ViewModel() {

    private val _rangeDays     = MutableStateFlow(30)
    private val _visibleSeries = MutableStateFlow(setOf("Energi Frukost"))

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
