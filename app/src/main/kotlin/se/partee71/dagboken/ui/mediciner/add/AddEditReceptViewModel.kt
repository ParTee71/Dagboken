package se.partee71.dagboken.ui.mediciner.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.Recept
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class ReceptForm(
    val namn: String = "",
    val dos: String = "",
    val enhet: String = "mg",
    val tidpunkter: List<String> = listOf("Morgon"),
    val upprepning: String = "dagligen",
    val dagar: List<Int> = emptyList(),
    val intervalDagar: Int = 1,
    val anteckning: String = "",
    val aktiv: Boolean = true,
    val skapad: String = LocalDate.now().toString(),
)

@HiltViewModel
class AddEditReceptViewModel @Inject constructor(
    private val repo: MedicinerRepository,
    private val noteRepo: NoteRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(ReceptForm())
    val form: StateFlow<ReceptForm> = _form.asStateFlow()
    private var editingId: String? = null

    private var originalForm = _form.value
    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private fun setCleanForm(form: ReceptForm) {
        originalForm = form
        _form.value = form
        _isDirty.value = false
    }

    private fun publish(form: ReceptForm) {
        _form.value = form
        _isDirty.value = form != originalForm
    }

    fun updateForm(update: ReceptForm.() -> ReceptForm) { publish(_form.value.update()) }

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val r = repo.getReceptById(id) ?: return@launch
            editingId = id
            val note = noteRepo.observe(NoteTarget.RECEPT, id).first()
            setCleanForm(
                ReceptForm(
                    namn          = r.namn,
                    dos           = r.dos,
                    enhet         = r.enhet,
                    tidpunkter    = r.tidpunkter,
                    upprepning    = r.upprepning,
                    dagar         = r.dagar,
                    intervalDagar = r.intervalDagar,
                    anteckning    = note,
                    aktiv         = r.aktiv,
                    skapad        = r.skapad,
                ),
            )
        }
    }

    fun toggleTidpunkt(t: String) {
        val cur = _form.value.tidpunkter.toMutableList()
        if (cur.contains(t)) { if (cur.size > 1) cur.remove(t) } else cur.add(t)
        publish(_form.value.copy(tidpunkter = cur))
    }

    fun toggleDag(dag: Int) {
        val cur = _form.value.dagar.toMutableList()
        if (cur.contains(dag)) cur.remove(dag) else cur.add(dag)
        publish(_form.value.copy(dagar = cur.sorted()))
    }

    fun save() {
        viewModelScope.launch {
            val f = _form.value
            val recept = Recept(
                id            = editingId ?: UUID.randomUUID().toString(),
                namn          = f.namn.trim(),
                dos           = f.dos.trim(),
                enhet         = f.enhet,
                tidpunkter    = f.tidpunkter,
                upprepning    = f.upprepning,
                dagar         = f.dagar,
                intervalDagar = f.intervalDagar,
                aktiv         = f.aktiv,
                skapad        = f.skapad,
            )
            repo.saveRecept(recept)
            noteRepo.save(NoteTarget.RECEPT, recept.id, f.anteckning.trim())
            originalForm = f
            _isDirty.value = false
        }
    }
}
