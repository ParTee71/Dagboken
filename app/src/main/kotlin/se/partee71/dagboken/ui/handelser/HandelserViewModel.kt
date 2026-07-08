package se.partee71.dagboken.ui.handelser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.Timestamps
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.domain.model.NoteTarget
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

data class TypPickerOptions(
    val favorites: List<String> = emptyList(),
    val nonFavorites: List<String> = emptyList(),
)

@HiltViewModel
class HandelserViewModel @Inject constructor(
    private val repo: HandelserRepository,
    private val noteRepo: NoteRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    val handelseTypOptions: StateFlow<List<SymptomOption>> = prefs.handelseTypOptions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val handelseNotes: StateFlow<Map<String, String>> = noteRepo.observeMap(NoteTarget.EVENT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _dagFilter  = MutableStateFlow<Int?>(null)
    private val _typFilter  = MutableStateFlow<String?>(null)

    private val _all = repo.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favourites-first typ picker for Add/Edit Händelse: managed options plus any custom
    // type already logged in the DB but not yet in the managed list.
    val typPickerOptions: StateFlow<TypPickerOptions> = combine(handelseTypOptions, _all) { options, all ->
        val managedNames = options.map { it.name }.toSet()
        val extraTyper   = all.map { it.typ }.distinct().filter { it !in managedNames }.sorted()
        TypPickerOptions(
            favorites    = options.filter { it.isFavorite }.map { it.name },
            nonFavorites = options.filter { !it.isFavorite }.map { it.name } + extraTyper,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TypPickerOptions())

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
            val note = noteRepo.observe(NoteTarget.EVENT, id).first()
            _form.value = HandelseForm(
                typ                = h.typ,
                datum              = h.datum,
                tid                = h.tid,
                svarighetsgrad     = h.svarighetsgrad,
                varaktighetTimmar  = h.varaktighetMinuter / 60,
                varaktighetMinuter = h.varaktighetMinuter % 60,
                triggers           = h.triggers,
                atgarder           = h.atgarder,
                anteckning         = note,
            )
        }
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.typ.isBlank()) return
        viewModelScope.launch {
            val entry = Handelse(
                id                 = _editId.value ?: UUID.randomUUID().toString(),
                timestamp          = Timestamps.of(f.datum, f.tid),
                datum              = f.datum,
                tid                = f.tid,
                typ                = f.typ,
                svarighetsgrad     = f.svarighetsgrad,
                varaktighetMinuter = f.varaktighetTimmar * 60 + f.varaktighetMinuter,
                triggers           = f.triggers,
                atgarder           = f.atgarder,
            )
            repo.save(entry)
            noteRepo.save(NoteTarget.EVENT, entry.id, f.anteckning.trim())
            resetForm()
            onDone()
        }
    }

    fun delete(handelse: Handelse) {
        viewModelScope.launch {
            repo.delete(handelse)
            noteRepo.delete(NoteTarget.EVENT, handelse.id)
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
