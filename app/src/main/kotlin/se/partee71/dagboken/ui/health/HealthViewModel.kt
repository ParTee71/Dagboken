package se.partee71.dagboken.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.domain.model.HealthData
import se.partee71.dagboken.domain.model.SleepQuality
import se.partee71.dagboken.domain.model.ageFromBirthYear
import se.partee71.dagboken.domain.model.scoreSleepQuality
import javax.inject.Inject

/** Tillstånd för Hälsa-skärmen (§19 HLS). */
sealed interface HealthUiState {
    data object Loading : HealthUiState

    /** Health Connect saknas ([updateRequired]=false) eller behöver uppdateras (=true). HLS-4. */
    data class Unavailable(val updateRequired: Boolean) : HealthUiState

    /** Läsbehörigheter ej beviljade — visa begär-behörighet-flöde. HLS-3. */
    data object PermissionsRequired : HealthUiState

    /**
     * Hälsodata inläst (fält kan vara null om källa saknas).
     *
     * [sleepQuality] är null när natten saknar sömndata. [birthYearMissing] är true när
     * användaren inte angett något födelseår — då går sömnkvaliteten inte att
     * åldersjustera (HLS-10/HLS-11) och skärmen uppmanar till att fylla i det i stället
     * för att visa en poäng mot fel norm.
     */
    data class Data(
        val health: HealthData,
        val sleepQuality: SleepQuality? = null,
        val birthYearMissing: Boolean = false,
    ) : HealthUiState

    /** I/O- eller behörighetsfel vid läsning. */
    data object Error : HealthUiState
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repo: HealthConnectRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    /** Behörigheter som skärmens behörighetslauncher ska begära. */
    val permissions: Set<String> get() = repo.permissions

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = HealthUiState.Loading
            when (repo.availability()) {
                HealthAvailability.NOT_INSTALLED -> {
                    _state.value = HealthUiState.Unavailable(updateRequired = false)
                    return@launch
                }
                HealthAvailability.UPDATE_REQUIRED -> {
                    _state.value = HealthUiState.Unavailable(updateRequired = true)
                    return@launch
                }
                HealthAvailability.AVAILABLE -> Unit
            }

            val granted = runCatching { repo.hasRequiredPermissions() }.getOrDefault(false)
            if (!granted) {
                _state.value = HealthUiState.PermissionsRequired
                return@launch
            }

            _state.value = runCatching {
                val health = repo.readToday()
                val age = ageFromBirthYear(prefs.birthYear.first())
                // Sömnkvaliteten läses separat och får inte fälla resten av skärmen:
                // den behöver flera nätter bakåt och är den tyngsta läsningen här.
                val quality = if (age == null) {
                    null
                } else {
                    runCatching {
                        repo.readSleepMeasurements()?.let { scoreSleepQuality(it, age, prefs.sex.first()) }
                    }.getOrNull()
                }
                HealthUiState.Data(
                    health = health,
                    sleepQuality = quality,
                    birthYearMissing = age == null,
                )
            }.getOrElse { HealthUiState.Error }
        }
    }
}
