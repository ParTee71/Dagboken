package se.partee71.dagboken.ui.mediciner.add

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.model.Medicin
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
) : ViewModel() {

    val form = mutableStateOf(MedicinForm())
    private var editingId: String? = null

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val m = repo.getMedicinById(id) ?: return@launch
            editingId = id
            form.value = MedicinForm(
                namn       = m.namn,
                dos        = m.dos,
                enhet      = m.enhet,
                tidpunkt   = m.tidpunkt,
                anteckning = m.anteckning,
            )
        }
    }

    fun save() {
        viewModelScope.launch {
            val f = form.value
            val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            val today = LocalDate.now().toString()
            val medicin = Medicin(
                id         = editingId ?: UUID.randomUUID().toString(),
                timestamp  = System.currentTimeMillis().toString(),
                datum      = today,
                tid        = now,
                namn       = f.namn.trim(),
                dos        = f.dos.trim(),
                enhet      = f.enhet,
                tidpunkt   = f.tidpunkt,
                tagen      = false,
                anteckning = f.anteckning.trim(),
            )
            repo.saveMedicin(medicin)
        }
    }
}
