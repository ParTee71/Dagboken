package se.partee71.dagboken.ui.handelser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.domain.model.Handelse
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class HandelseForm(
    val typ: String = "",
    val datum: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val tid: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    val svarighetsgrad: Int = 5,
    val varaktighetTimmar: Int = 0,
    val varaktighetMinuter: Int = 0,
    val triggers: String = "",
    val atgarder: String = "",
    val anteckning: String = "",
)

data class HandelserUiState(
    val filteredHandelser: List<Handelse> = emptyList(),
    val dagFilter: Int? = null,
    val typFilter: String? = null,
    val allTyper: List<String> = emptyList(),
)

@HiltViewModel
class HandelserViewModel @Inject constructor(
    private val repo: HandelserRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    val handelseTypOptions: StateFlow<List<SymptomOption>> = prefs.handelseTypOptions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _dagFilter  = MutableStateFlow<Int?>(null)
    private val _typFilter  = MutableStateFlow<String?>(null)

    private val _all = repo.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val state: StateFlow<HandelserUiState> = combine(_all, _dagFilter, _typFilter) { all, dag, typ ->
        val cutoff = dag?.let {
            LocalDate.now().minusDays(it.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
        val filtered = all
            .let { list -> if (cutoff != null) list.filter { it.datum >= cutoff } else list }
            .let { list -> if (typ != null) list.filter { it.typ == typ } else list }
        HandelserUiState(
            filteredHandelser = filtered,
            dagFilter         = dag,
            typFilter         = typ,
            allTyper          = all.map { it.typ }.distinct().sorted(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HandelserUiState())

    private val _form    = MutableStateFlow(HandelseForm())
    val form: StateFlow<HandelseForm> = _form.asStateFlow()

    private val _editId  = MutableStateFlow<String?>(null)
    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    fun setDagFilter(days: Int?) { _dagFilter.value = days }
    fun setTypFilter(typ: String?) { _typFilter.value = typ }

    fun updateForm(update: HandelseForm.() -> HandelseForm) {
        _form.value = _form.value.update()
    }

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val h = repo.getById(id) ?: return@launch
            _editId.value = id
            _form.value = HandelseForm(
                typ                = h.typ,
                datum              = h.datum,
                tid                = h.tid,
                svarighetsgrad     = h.svarighetsgrad,
                varaktighetTimmar  = h.varaktighetMinuter / 60,
                varaktighetMinuter = h.varaktighetMinuter % 60,
                triggers           = h.triggers,
                atgarder           = h.atgarder,
                anteckning         = h.anteckning,
            )
        }
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.typ.isBlank()) return
        viewModelScope.launch {
            val entry = Handelse(
                id                 = _editId.value ?: UUID.randomUUID().toString(),
                timestamp          = "${f.datum}T${f.tid}:00.000Z",
                datum              = f.datum,
                tid                = f.tid,
                typ                = f.typ,
                svarighetsgrad     = f.svarighetsgrad,
                varaktighetMinuter = f.varaktighetTimmar * 60 + f.varaktighetMinuter,
                triggers           = f.triggers,
                atgarder           = f.atgarder,
                anteckning         = f.anteckning,
            )
            repo.save(entry)
            resetForm()
            onDone()
        }
    }

    fun delete(handelse: Handelse) {
        viewModelScope.launch {
            repo.delete(handelse)
            _snackbar.value = "${handelse.typ} borttagen"
        }
    }

    fun clearSnackbar() { _snackbar.value = null }

    fun resetForm() {
        _editId.value = null
        _form.value   = HandelseForm()
    }

    fun toggleHandelseTypFavorite(name: String) {
        viewModelScope.launch {
            prefs.setHandelseTypOptions(handelseTypOptions.value.map {
                if (it.name == name) it.copy(isFavorite = !it.isFavorite) else it
            })
        }
    }
}
