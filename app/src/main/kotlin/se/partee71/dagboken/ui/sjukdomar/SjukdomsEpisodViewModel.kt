package se.partee71.dagboken.ui.sjukdomar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import se.partee71.dagboken.domain.usecase.SymptomUtils
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class IncheckningForm(
    val datum: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val tid: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    val svarighetsgrad: Int = 5,
    val symptomScores: Map<String, Int> = emptyMap(),
    val anteckning: String = "",
)

@HiltViewModel
class SjukdomsEpisodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: SjukdomarRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val episodId: String = checkNotNull(savedStateHandle["episodId"])

    val incheckningar: StateFlow<List<SjukdomsIncheckning>> =
        repo.incheckningarForEpisod(episodId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val episod: StateFlow<SjukdomsEpisod?> = repo.all
        .map { list -> list.firstOrNull { it.id == episodId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val symptomOptions: StateFlow<List<SymptomOption>> = prefs.symptomOptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _incheckningForm = MutableStateFlow(IncheckningForm())
    val incheckningForm: StateFlow<IncheckningForm> = _incheckningForm.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    fun updateForm(update: IncheckningForm.() -> IncheckningForm) {
        _incheckningForm.value = _incheckningForm.value.update()
    }

    fun toggleSymptomFavorite(name: String) {
        viewModelScope.launch {
            val current = prefs.symptomOptions
            current.collect { options ->
                val updated = options.map { if (it.name == name) it.copy(isFavorite = !it.isFavorite) else it }
                prefs.setSymptomOptions(updated)
                return@collect
            }
        }
    }

    fun saveIncheckning() {
        val f = _incheckningForm.value
        viewModelScope.launch {
            val symptomStr = SymptomUtils.encode(f.symptomScores)
            val incheckning = SjukdomsIncheckning(
                id             = UUID.randomUUID().toString(),
                episodId       = episodId,
                datum          = f.datum,
                tid            = f.tid,
                svarighetsgrad = f.svarighetsgrad,
                symptom        = symptomStr,
                somatiska      = SymptomUtils.sum(symptomStr),
                anteckning     = f.anteckning,
            )
            repo.saveIncheckning(incheckning)
            _incheckningForm.value = IncheckningForm()
            _snackbar.value = "Incheckning sparad ✓"
        }
    }

    fun deleteIncheckning(incheckning: SjukdomsIncheckning) {
        viewModelScope.launch {
            repo.deleteIncheckning(incheckning)
            _snackbar.value = "Incheckning borttagen"
        }
    }

    fun clearSnackbar() { _snackbar.value = null }
}
