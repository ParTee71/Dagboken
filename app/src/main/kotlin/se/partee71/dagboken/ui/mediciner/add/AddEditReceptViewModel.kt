package se.partee71.dagboken.ui.mediciner.add

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.MedicinerRepository
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
) : ViewModel() {

    val form = mutableStateOf(ReceptForm())
    private var editingId: String? = null

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val r = repo.getReceptById(id) ?: return@launch
            editingId = id
            form.value = ReceptForm(
                namn          = r.namn,
                dos           = r.dos,
                enhet         = r.enhet,
                tidpunkter    = r.tidpunkter,
                upprepning    = r.upprepning,
                dagar         = r.dagar,
                intervalDagar = r.intervalDagar,
                anteckning    = r.anteckning,
                aktiv         = r.aktiv,
                skapad        = r.skapad,
            )
        }
    }

    fun toggleTidpunkt(t: String) {
        val cur = form.value.tidpunkter.toMutableList()
        if (cur.contains(t)) { if (cur.size > 1) cur.remove(t) } else cur.add(t)
        form.value = form.value.copy(tidpunkter = cur)
    }

    fun toggleDag(dag: Int) {
        val cur = form.value.dagar.toMutableList()
        if (cur.contains(dag)) cur.remove(dag) else cur.add(dag)
        form.value = form.value.copy(dagar = cur.sorted())
    }

    fun save() {
        viewModelScope.launch {
            val f = form.value
            val recept = Recept(
                id            = editingId ?: UUID.randomUUID().toString(),
                namn          = f.namn.trim(),
                dos           = f.dos.trim(),
                enhet         = f.enhet,
                tidpunkter    = f.tidpunkter,
                upprepning    = f.upprepning,
                dagar         = f.dagar,
                intervalDagar = f.intervalDagar,
                anteckning    = f.anteckning.trim(),
                aktiv         = f.aktiv,
                skapad        = f.skapad,
            )
            repo.saveRecept(recept)
        }
    }
}
