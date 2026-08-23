package se.partee71.dagboken.ui.sjukdomar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import se.partee71.dagboken.domain.usecase.SymptomUtils
import se.partee71.dagboken.ui.formatTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class IncheckningForm(
    val datum: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val tid: String = formatTime(LocalTime.now()),
    val svarighetsgrad: Int = 5,
    val symptomScores: Map<String, Int> = emptyMap(),
    val anteckning: String = "",
)

@HiltViewModel
class SjukdomsEpisodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: SjukdomarRepository,
    private val noteRepo: NoteRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val episodId: String = checkNotNull(savedStateHandle["episodId"])

    val incheckningar: StateFlow<List<SjukdomsIncheckning>> =
        repo.incheckningarForEpisod(episodId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val episod: StateFlow<SjukdomsEpisod?> = repo.all
        .map { list -> list.firstOrNull { it.id == episodId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val episodNote: StateFlow<String> = noteRepo.observe(NoteTarget.SJUKDOM_EPISOD, episodId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val incheckningNotes: StateFlow<Map<String, String>> = noteRepo.observeMap(NoteTarget.SJUKDOM_INCHECKNING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val symptomOptions: StateFlow<List<SymptomOption>> = prefs.symptomOptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _incheckningForm = MutableStateFlow(IncheckningForm())
    val incheckningForm: StateFlow<IncheckningForm> = _incheckningForm.asStateFlow()

    private var originalIncheckningForm = _incheckningForm.value
    private val _isIncheckningFormDirty = MutableStateFlow(false)
    val isIncheckningFormDirty: StateFlow<Boolean> = _isIncheckningFormDirty.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private var editIncheckningId: String? = null

    /**
     * Laddar en befintlig incheckning för redigering (SJ-11). Formuläret sätts som
     * "rent" så att SaveButton förblir inaktiv tills något faktiskt ändras (NFR-10).
     */
    fun loadIncheckningForEdit(id: String) {
        viewModelScope.launch {
            val incheckning = repo.getIncheckning(id) ?: return@launch
            val note = noteRepo.observe(NoteTarget.SJUKDOM_INCHECKNING, id).first()
            editIncheckningId = id
            originalTimestamp = incheckning.timestamp
            val form = IncheckningForm(
                datum          = incheckning.datum,
                tid            = incheckning.tid,
                svarighetsgrad = incheckning.svarighetsgrad,
                symptomScores  = SymptomUtils.decode(incheckning.symptom),
                anteckning     = note,
            )
            originalIncheckningForm = form
            _incheckningForm.value = form
            _isIncheckningFormDirty.value = false
        }
    }

    private var originalTimestamp: Long? = null

    fun updateForm(update: IncheckningForm.() -> IncheckningForm) {
        _incheckningForm.value = _incheckningForm.value.update()
        _isIncheckningFormDirty.value = _incheckningForm.value != originalIncheckningForm
    }

    fun toggleSymptomFavorite(name: String) {
        viewModelScope.launch {
            val options = symptomOptions.value
            val updated = options.map { if (it.name == name) it.copy(isFavorite = !it.isFavorite) else it }
            prefs.setSymptomOptions(updated)
        }
    }

    fun saveIncheckning() {
        val f = _incheckningForm.value
        viewModelScope.launch {
            val symptomStr = SymptomUtils.encode(f.symptomScores)
            // Vid redigering behålls postens id och ursprungliga timestamp, så att den
            // förblir samma post i backup-kedjan (SJ-7) i stället för att bli en ny.
            val incheckning = SjukdomsIncheckning(
                id             = editIncheckningId ?: UUID.randomUUID().toString(),
                episodId       = episodId,
                datum          = f.datum,
                tid            = f.tid,
                svarighetsgrad = f.svarighetsgrad,
                symptom        = symptomStr,
                somatiska      = SymptomUtils.sum(symptomStr),
                timestamp      = originalTimestamp ?: System.currentTimeMillis(),
            )
            repo.saveIncheckning(incheckning)
            noteRepo.save(NoteTarget.SJUKDOM_INCHECKNING, incheckning.id, f.anteckning.trim())
            val blank = IncheckningForm()
            originalIncheckningForm = blank
            _incheckningForm.value = blank
            _isIncheckningFormDirty.value = false
            editIncheckningId = null
            originalTimestamp = null
            _snackbar.value = "Incheckning sparad ✓"
        }
    }

    fun deleteIncheckning(incheckning: SjukdomsIncheckning) {
        viewModelScope.launch {
            repo.deleteIncheckning(incheckning)
            _snackbar.value = "Incheckning borttagen"
        }
    }

    fun markFrisk(episod: SjukdomsEpisod) {
        viewModelScope.launch {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            repo.saveEpisod(episod.copy(slutDatum = today))
            _snackbar.value = "Markerad som frisk ✓"
        }
    }

    fun clearSnackbar() { _snackbar.value = null }
}
