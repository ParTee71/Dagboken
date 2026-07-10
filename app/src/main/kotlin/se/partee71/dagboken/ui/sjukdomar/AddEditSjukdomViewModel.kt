package se.partee71.dagboken.ui.sjukdomar

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

data class SjukdomForm(
    val typ: String = "",
    val startDatum: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val anteckning: String = "",
    val svarighetsgrad: Int = 5,
    val symptomScores: Map<String, Int> = emptyMap(),
)

@HiltViewModel
class AddEditSjukdomViewModel @Inject constructor(
    private val repo: SjukdomarRepository,
    private val noteRepo: NoteRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(SjukdomForm())
    val form: StateFlow<SjukdomForm> = _form.asStateFlow()

    private var originalForm = _form.value
    val isDirty: StateFlow<Boolean> = form
        .map { it != originalForm }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val symptomOptions: StateFlow<List<SymptomOption>> = prefs.symptomOptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private var editId: String? = null

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val episod = repo.all.first().firstOrNull { it.id == id } ?: return@launch
            editId = id
            val note = noteRepo.observe(NoteTarget.SJUKDOM_EPISOD, id).first()
            val loaded = SjukdomForm(
                typ        = episod.typ,
                startDatum = episod.startDatum,
                anteckning = note,
            )
            originalForm = loaded
            _form.value = loaded
        }
    }

    fun updateForm(update: SjukdomForm.() -> SjukdomForm) {
        _form.value = _form.value.update()
    }

    fun toggleSymptomFavorite(name: String) {
        viewModelScope.launch {
            val updated = symptomOptions.value.map {
                if (it.name == name) it.copy(isFavorite = !it.isFavorite) else it
            }
            prefs.setSymptomOptions(updated)
        }
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.typ.isBlank()) return
        viewModelScope.launch {
            val episodId = editId ?: UUID.randomUUID().toString()
            val episod = SjukdomsEpisod(
                id         = episodId,
                typ        = f.typ,
                startDatum = f.startDatum,
                slutDatum  = "",
            )
            repo.saveEpisod(episod)
            noteRepo.save(NoteTarget.SJUKDOM_EPISOD, episodId, f.anteckning.trim())
            if (editId == null) {
                val symptomStr = SymptomUtils.encode(f.symptomScores)
                val now = formatTime(LocalTime.now())
                repo.saveIncheckning(
                    SjukdomsIncheckning(
                        id             = UUID.randomUUID().toString(),
                        episodId       = episodId,
                        datum          = f.startDatum,
                        tid            = now,
                        svarighetsgrad = f.svarighetsgrad,
                        symptom        = symptomStr,
                        somatiska      = SymptomUtils.sum(symptomStr),
                    )
                )
            }
            _snackbar.value = "Sjukdom sparad ✓"
            onDone()
        }
    }

    fun resetForm() {
        editId = null
        val blank = SjukdomForm()
        originalForm = blank
        _form.value = blank
    }

    fun clearSnackbar() { _snackbar.value = null }
}
