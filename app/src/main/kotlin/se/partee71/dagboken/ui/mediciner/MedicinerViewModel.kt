package se.partee71.dagboken.ui.mediciner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.domain.model.medicinHistoryType
import se.partee71.dagboken.domain.usecase.LogVidBehovDosUseCase
import se.partee71.dagboken.domain.usecase.VidBehovLogResult
import javax.inject.Inject

data class CooldownWarning(val favorit: Favorit, val remainingHours: Double)

@HiltViewModel
class MedicinerViewModel @Inject constructor(
    private val repo: MedicinerRepository,
    private val noteRepo: NoteRepository,
    private val logVidBehovDos: LogVidBehovDosUseCase,
) : ViewModel() {

    init {
        viewModelScope.launch { repo.ensureTodayEntries() }
    }

    val todayMediciner: StateFlow<List<Medicin>> = repo.todayFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecept: StateFlow<List<Recept>> = repo.allRecept
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val receptNotes: StateFlow<Map<String, String>> = noteRepo.observeMap(NoteTarget.RECEPT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val medicationNotes: StateFlow<Map<String, String>> = noteRepo.observeMap(NoteTarget.MEDICATION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val favoritNotes: StateFlow<Map<String, String>> = noteRepo.observeMap(NoteTarget.FAVORIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allFavoriter: StateFlow<List<Favorit>> = repo.allFavoriter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteFavoriter: StateFlow<List<Favorit>> = repo.allFavoriter
        .map { list -> list.filter { it.isFavorite } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val otherFavoriter: StateFlow<List<Favorit>> = repo.allFavoriter
        .map { list -> list.filterNot { it.isFavorite } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMediciner: StateFlow<List<Medicin>> = repo.allMediciner
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyFilter = MutableStateFlow(setOf("recept", "vid_behov"))

    val filteredHistory: StateFlow<List<Medicin>> = combine(allMediciner, historyFilter) { list, filter ->
        list.filter { medicinHistoryType(it) in filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleHistoryFilter(type: String) {
        val current = historyFilter.value
        historyFilter.value = if (type in current) {
            if (current.size > 1) current - type else current
        } else {
            current + type
        }
    }

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private val _cooldownWarning = MutableStateFlow<CooldownWarning?>(null)
    val cooldownWarning: StateFlow<CooldownWarning?> = _cooldownWarning.asStateFlow()

    private val _noteDialogMedicin = MutableStateFlow<Medicin?>(null)
    val noteDialogMedicin: StateFlow<Medicin?> = _noteDialogMedicin.asStateFlow()

    private val _noteDialogText = MutableStateFlow("")
    val noteDialogText: StateFlow<String> = _noteDialogText.asStateFlow()

    private var noteLoadJob: Job? = null

    fun clearSnackbar() { _snackbar.value = null }

    fun openNoteDialog(medicin: Medicin) {
        noteLoadJob?.cancel()
        _noteDialogMedicin.value = medicin
        _noteDialogText.value = ""
        noteLoadJob = viewModelScope.launch {
            _noteDialogText.value = noteRepo.observe(NoteTarget.MEDICATION, medicin.id).first()
        }
    }

    fun updateNoteDialogText(text: String) { _noteDialogText.value = text }

    fun saveAndCloseNoteDialog() {
        val medicin = _noteDialogMedicin.value ?: return
        val text = _noteDialogText.value
        _noteDialogMedicin.value = null
        _noteDialogText.value = ""
        viewModelScope.launch {
            noteRepo.save(NoteTarget.MEDICATION, medicin.id, text)
        }
    }

    fun dismissNoteDialog() {
        noteLoadJob?.cancel()
        _noteDialogMedicin.value = null
        _noteDialogText.value = ""
    }

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
                noteRepo.delete(NoteTarget.MEDICATION, medicin.id)
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
            noteRepo.delete(NoteTarget.RECEPT, recept.id)
            _snackbar.value = "${recept.namn} borttagen"
        }
    }

    fun deleteFavorit(favorit: Favorit) {
        viewModelScope.launch {
            repo.deleteFavorit(favorit)
            noteRepo.delete(NoteTarget.FAVORIT, favorit.id)
            _snackbar.value = "${favorit.namn} borttagen"
        }
    }

    fun toggleFavoritFavorite(favorit: Favorit) {
        viewModelScope.launch { repo.setFavoritFavorite(favorit.id, !favorit.isFavorite) }
    }

    fun quickDos(favorit: Favorit) {
        viewModelScope.launch {
            when (val result = logVidBehovDos.logDose(favorit)) {
                is VidBehovLogResult.CooldownWarning ->
                    _cooldownWarning.value = CooldownWarning(favorit, result.remainingHours)
                VidBehovLogResult.DailyLimitReached ->
                    _snackbar.value = "Max ${favorit.maxDoserPerDag} doser/dag nådda för ${favorit.namn}"
                VidBehovLogResult.Logged ->
                    _snackbar.value = "${favorit.namn} ${favorit.dos} ${favorit.enhet} loggad"
            }
        }
    }

    fun forceDos(favorit: Favorit) {
        _cooldownWarning.value = null
        viewModelScope.launch {
            when (val result = logVidBehovDos.logDose(favorit, force = true)) {
                VidBehovLogResult.DailyLimitReached ->
                    _snackbar.value = "Max ${favorit.maxDoserPerDag} doser/dag nådda för ${favorit.namn}"
                VidBehovLogResult.Logged ->
                    _snackbar.value = "${favorit.namn} ${favorit.dos} ${favorit.enhet} loggad"
                is VidBehovLogResult.CooldownWarning -> Unit // force=true kringgår cooldown, når aldrig hit
            }
        }
    }

    fun dismissCooldownWarning() {
        _cooldownWarning.value = null
    }
}
