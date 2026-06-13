package se.partee71.dagboken.ui.mediciner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.domain.model.tidpunktSortIndex
import se.partee71.dagboken.domain.usecase.CheckCooldownUseCase
import se.partee71.dagboken.domain.usecase.CheckDailyLimitUseCase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MedicinerViewModel @Inject constructor(
    private val repo: MedicinerRepository,
    private val cooldownUseCase: CheckCooldownUseCase,
    private val limitUseCase: CheckDailyLimitUseCase,
) : ViewModel() {

    init {
        viewModelScope.launch { repo.ensureTodayEntries() }
    }

    val todayMediciner: StateFlow<List<Medicin>> = repo.todayFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecept: StateFlow<List<Recept>> = repo.allRecept
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFavoriter: StateFlow<List<Favorit>> = repo.allFavoriter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    fun clearSnackbar() { _snackbar.value = null }

    fun toggleTagen(medicin: Medicin) {
        viewModelScope.launch { repo.toggleTagen(medicin.id, !medicin.tagen) }
    }

    fun deleteMedicin(medicin: Medicin) {
        viewModelScope.launch {
            if (medicin.receptId != null) {
                repo.skipMedicin(medicin.id)
                _snackbar.value = "${medicin.namn} markerad som hoppad"
            } else {
                repo.deleteMedicin(medicin)
                _snackbar.value = "${medicin.namn} borttagen"
            }
        }
    }

    fun toggleReceptAktiv(recept: Recept) {
        viewModelScope.launch { repo.toggleReceptAktiv(recept.id, !recept.aktiv) }
    }

    fun deleteRecept(recept: Recept) {
        viewModelScope.launch {
            repo.deleteRecept(recept)
            _snackbar.value = "${recept.namn} borttagen"
        }
    }

    fun deleteFavorit(favorit: Favorit) {
        viewModelScope.launch {
            repo.deleteFavorit(favorit)
            _snackbar.value = "${favorit.namn} borttagen"
        }
    }

    fun quickDos(favorit: Favorit, onBlocked: (String) -> Unit) {
        viewModelScope.launch {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

            // Check daily limit
            val takenToday = repo.countDailyDoses(today, favorit.namn)
            if (limitUseCase.limitReached(favorit.maxDoserPerDag, takenToday)) {
                onBlocked("Max ${favorit.maxDoserPerDag} doser/dag nådda för ${favorit.namn}")
                return@launch
            }

            // Check cooldown
            val lastTaken = repo.getLastTaken(favorit.namn)
            val remaining = cooldownUseCase.remainingHours(favorit.namn, favorit.minTidMellan, lastTaken)
            if (remaining != null) {
                val h = remaining.toInt()
                val m = ((remaining - h) * 60).toInt()
                onBlocked("Vänta ${h}h ${m}m innan nästa dos av ${favorit.namn}")
                return@launch
            }

            // Log the dose
            val now = java.time.LocalTime.now()
            val tid = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            repo.saveMedicin(Medicin(
                id         = UUID.randomUUID().toString(),
                timestamp  = "${today}T${tid}:00.000Z",
                datum      = today,
                tid        = tid,
                namn       = favorit.namn,
                dos        = favorit.dos,
                enhet      = favorit.enhet,
                tidpunkt   = favorit.tidpunkt,
                tagen      = true,
                anteckning = favorit.anteckning,
            ))
            _snackbar.value = "${favorit.namn} ${favorit.dos} ${favorit.enhet} loggad"
        }
    }
}
