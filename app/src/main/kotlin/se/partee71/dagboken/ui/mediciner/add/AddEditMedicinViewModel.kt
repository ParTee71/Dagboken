package se.partee71.dagboken.ui.mediciner.add

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
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.Timestamps
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.ui.formatTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class MedicinForm(
    val namn: String = "",
    val dos: String = "",
    val enhet: String = "mg",
    val tidpunkt: String = "Morgon",
    val anteckning: String = "",
)

@HiltViewModel
class AddEditMedicinViewModel @Inject constructor(
    private val repo: MedicinerRepository,
    private val noteRepo: NoteRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(MedicinForm())
    val form: StateFlow<MedicinForm> = _form.asStateFlow()
    private var editingMedicin: Medicin? = null

    private var originalForm = _form.value
    val isDirty: StateFlow<Boolean> = form
        .map { it != originalForm }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun markClean() { originalForm = _form.value }

    fun updateForm(update: MedicinForm.() -> MedicinForm) { _form.value = _form.value.update() }

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val m = repo.getMedicinById(id) ?: return@launch
            editingMedicin = m
            val note = noteRepo.observe(NoteTarget.MEDICATION, id).first()
            val loaded = MedicinForm(
                namn       = m.namn,
                dos        = m.dos,
                enhet      = m.enhet,
                tidpunkt   = m.tidpunkt,
                anteckning = note,
            )
            originalForm = loaded
            _form.value = loaded
        }
    }

    fun save() {
        viewModelScope.launch {
            val f = _form.value
            val original = editingMedicin
            val medicin = if (original != null) {
                original.copy(
                    namn       = f.namn.trim(),
                    dos        = f.dos.trim(),
                    enhet      = f.enhet,
                    tidpunkt   = f.tidpunkt,
                )
            } else {
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val tid   = formatTime(LocalTime.now())
                Medicin(
                    id         = UUID.randomUUID().toString(),
                    timestamp  = Timestamps.of(today, tid),
                    datum      = today,
                    tid        = tid,
                    namn       = f.namn.trim(),
                    dos        = f.dos.trim(),
                    enhet      = f.enhet,
                    tidpunkt   = f.tidpunkt,
                    tagen      = false,
                )
            }
            repo.saveMedicin(medicin)
            noteRepo.save(NoteTarget.MEDICATION, medicin.id, f.anteckning.trim())
            markClean()
        }
    }
}
