package se.partee71.dagboken.ui.sjukdomar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class SjukdomForm(
    val typ: String = "",
    val startDatum: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val anteckning: String = "",
)

@HiltViewModel
class AddEditSjukdomViewModel @Inject constructor(
    private val repo: SjukdomarRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(SjukdomForm())
    val form: StateFlow<SjukdomForm> = _form.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private var editId: String? = null

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val e = repo.all.collect { episoder ->
                val episod = episoder.firstOrNull { it.id == id } ?: return@collect
                editId = id
                _form.value = SjukdomForm(
                    typ        = episod.typ,
                    startDatum = episod.startDatum,
                    anteckning = episod.anteckning,
                )
                return@collect
            }
        }
    }

    fun updateForm(update: SjukdomForm.() -> SjukdomForm) {
        _form.value = _form.value.update()
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.typ.isBlank()) return
        viewModelScope.launch {
            val episod = SjukdomsEpisod(
                id         = editId ?: UUID.randomUUID().toString(),
                typ        = f.typ,
                startDatum = f.startDatum,
                slutDatum  = "",
                anteckning = f.anteckning,
            )
            repo.saveEpisod(episod)
            _snackbar.value = "Sjukdom sparad ✓"
            onDone()
        }
    }

    fun resetForm() {
        editId = null
        _form.value = SjukdomForm()
    }

    fun clearSnackbar() { _snackbar.value = null }
}
