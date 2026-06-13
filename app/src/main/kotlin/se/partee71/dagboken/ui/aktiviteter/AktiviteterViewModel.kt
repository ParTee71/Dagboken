package se.partee71.dagboken.ui.aktiviteter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.usecase.SymptomUtils
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class AktivitetForm(
    val aktivitet: String = "",
    val aktivitetAnnat: String = "",
    val aterhamtande: Boolean = false,
    val energitjuv: Boolean = false,
    val datum: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val tid: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    val spentTimeHours: Int = 0,
    val spentTimeMinutes: Int = 0,
    val energy: Int = 0,
    val stress: Int = 0,
    val symptomScores: Map<String, Int> = emptyMap(),
    val metricsExpanded: Boolean = false,
    val symptomsExpanded: Boolean = false,
    val type: String = "aktivitet",
)

@HiltViewModel
class AktiviteterViewModel @Inject constructor(
    private val repo: AktiviteterRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    val all: StateFlow<List<Aktivitet>> = repo.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aktivitetOptions: StateFlow<List<String>> = prefs.aktivitetOptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val symptomOptions: StateFlow<List<String>> = prefs.symptomOptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _form = MutableStateFlow(AktivitetForm())
    val form: StateFlow<AktivitetForm> = _form.asStateFlow()

    private val _editId = MutableStateFlow<String?>(null)

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    fun updateForm(update: AktivitetForm.() -> AktivitetForm) {
        _form.value = _form.value.update()
    }

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val a = repo.getById(id) ?: return@launch
            _editId.value = id
            _form.value = AktivitetForm(
                aktivitet      = a.aktivitet,
                aterhamtande   = a.aterhamtande,
                energitjuv     = a.energitjuv,
                datum          = a.datum,
                tid            = a.tid,
                spentTimeHours = (a.spentTime ?: 0) / 60,
                spentTimeMinutes = (a.spentTime ?: 0) % 60,
                energy         = a.energy,
                stress         = a.stress,
                symptomScores  = SymptomUtils.decode(a.symptom),
                type           = a.type,
            )
        }
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        val aktivitetName = if (f.aktivitet == "Övrigt") f.aktivitetAnnat else f.aktivitet
        if (aktivitetName.isBlank()) return

        viewModelScope.launch {
            val symptomStr = SymptomUtils.encode(f.symptomScores)
            val entry = Aktivitet(
                id           = _editId.value ?: UUID.randomUUID().toString(),
                timestamp    = "${f.datum}T${f.tid}:00.000Z",
                datum        = f.datum,
                tid          = f.tid,
                aktivitet    = aktivitetName,
                energy       = f.energy,
                stress       = f.stress,
                somatiska    = SymptomUtils.sum(symptomStr),
                symptom      = symptomStr,
                aterhamtande = f.aterhamtande,
                energitjuv   = f.energitjuv,
                type         = f.type,
                spentTime    = f.spentTimeHours * 60 + f.spentTimeMinutes,
            )
            repo.save(entry)
            resetForm()
            onDone()
        }
    }

    fun delete(aktivitet: Aktivitet) {
        viewModelScope.launch {
            repo.delete(aktivitet)
            _snackbar.value = "${aktivitet.aktivitet} borttagen"
        }
    }

    fun clearSnackbar() { _snackbar.value = null }

    fun resetForm() {
        _editId.value = null
        _form.value = AktivitetForm()
    }
}
