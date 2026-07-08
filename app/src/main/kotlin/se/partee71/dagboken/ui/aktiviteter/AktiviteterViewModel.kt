package se.partee71.dagboken.ui.aktiviteter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.Timestamps
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.usecase.SymptomUtils
import se.partee71.dagboken.ui.formatTime
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
    val tid: String = formatTime(LocalTime.now()),
    val spentTimeHours: Int = 0,
    val spentTimeMinutes: Int = 0,
    val energy: Int = 0,
    val stress: Int = 0,
    val symptomScores: Map<String, Int> = emptyMap(),
    val ovrigtNote: String = "",
    val note: String = "",
    val metricsExpanded: Boolean = false,
    val symptomsExpanded: Boolean = false,
    val type: String = "aktivitet",
)

@HiltViewModel
class AktiviteterViewModel @Inject constructor(
    private val repo: AktiviteterRepository,
    private val noteRepo: NoteRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    val all: StateFlow<List<Aktivitet>> = repo.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentEntries: StateFlow<List<Aktivitet>> = all
        .map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyFilter = MutableStateFlow(setOf("aktivitet", "screening"))

    val filteredHistory: StateFlow<List<Aktivitet>> = combine(all, historyFilter) { list, filter ->
        list.filter { it.type in filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleHistoryFilter(type: String) {
        val current = historyFilter.value
        historyFilter.value = if (type in current) {
            if (current.size > 1) current - type else current
        } else {
            current + type
        }
    }

    private val _editId = MutableStateFlow<String?>(null)

    val todaysScreenings: StateFlow<Set<String>> = combine(all, _editId) { list, editId ->
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        list.filter { it.type == "screening" && it.datum == today && it.id != editId }
            .map { it.aktivitet }
            .toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** entityId → note text, for both aktivitet and screening entries (shown as a card indicator). */
    val noteMap: StateFlow<Map<String, String>> = combine(
        noteRepo.observeMap(NoteTarget.ACTIVITY),
        noteRepo.observeMap(NoteTarget.SCREENING),
    ) { activityNotes, screeningNotes -> activityNotes + screeningNotes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val aktivitetOptions: StateFlow<List<SymptomOption>> = prefs.aktivitetOptions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val symptomOptions: StateFlow<List<SymptomOption>> = prefs.symptomOptions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _form = MutableStateFlow(AktivitetForm())
    val form: StateFlow<AktivitetForm> = _form.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    fun updateForm(update: AktivitetForm.() -> AktivitetForm) {
        _form.value = _form.value.update()
    }

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val a = repo.getById(id) ?: return@launch
            _editId.value = id
            val decoded = SymptomUtils.decode(a.symptom)
            val ovrigtKey = decoded.keys.firstOrNull { it.startsWith("Övrigt") }
            val ovrigtNote = if (ovrigtKey != null && ovrigtKey != "Övrigt") {
                ovrigtKey.removePrefix("Övrigt").trim().removeSurrounding("(", ")")
            } else ""
            val normalizedScores = if (ovrigtKey != null && ovrigtKey != "Övrigt") {
                decoded - ovrigtKey + ("Övrigt" to decoded[ovrigtKey]!!)
            } else decoded
            val noteTarget = if (a.type == "screening") NoteTarget.SCREENING else NoteTarget.ACTIVITY
            val note       = noteRepo.observe(noteTarget, id).first()
            _form.value = AktivitetForm(
                aktivitet        = a.aktivitet,
                aterhamtande     = a.aterhamtande,
                energitjuv       = a.energitjuv,
                datum            = a.datum,
                tid              = a.tid,
                spentTimeHours   = (a.spentTime ?: 0) / 60,
                spentTimeMinutes = (a.spentTime ?: 0) % 60,
                energy           = a.energy,
                stress           = a.stress,
                symptomScores    = normalizedScores,
                ovrigtNote       = ovrigtNote,
                note             = note,
                type             = a.type,
            )
        }
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        val aktivitetName = if (f.aktivitet == "Övrigt") f.aktivitetAnnat else f.aktivitet
        if (aktivitetName.isBlank()) return

        viewModelScope.launch {
            val scores = if (f.ovrigtNote.isNotBlank() && (f.symptomScores["Övrigt"] ?: 0) > 0) {
                f.symptomScores - "Övrigt" + ("Övrigt (${f.ovrigtNote})" to f.symptomScores["Övrigt"]!!)
            } else f.symptomScores
            val symptomStr = SymptomUtils.encode(scores)
            val entry = Aktivitet(
                id           = _editId.value ?: UUID.randomUUID().toString(),
                timestamp    = Timestamps.of(f.datum, f.tid),
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
            val noteTarget = if (f.type == "screening") NoteTarget.SCREENING else NoteTarget.ACTIVITY
            noteRepo.save(noteTarget, entry.id, f.note)
            _snackbar.value = if (f.type == "screening") "Screening sparad ✓" else "$aktivitetName sparad ✓"
            resetForm()
            onDone()
        }
    }

    fun toggleAktivitetFavorite(name: String) {
        viewModelScope.launch {
            prefs.setAktivitetOptions(aktivitetOptions.value.map {
                if (it.name == name) it.copy(isFavorite = !it.isFavorite) else it
            })
        }
    }

    fun toggleSymptomFavorite(name: String) {
        viewModelScope.launch {
            prefs.setSymptomOptions(symptomOptions.value.map {
                if (it.name == name) it.copy(isFavorite = !it.isFavorite) else it
            })
        }
    }

    fun delete(aktivitet: Aktivitet) {
        viewModelScope.launch {
            repo.delete(aktivitet)
            val noteTarget = if (aktivitet.type == "screening") NoteTarget.SCREENING else NoteTarget.ACTIVITY
            noteRepo.delete(noteTarget, aktivitet.id)
            _snackbar.value = "${aktivitet.aktivitet} borttagen"
        }
    }

    fun clearSnackbar() { _snackbar.value = null }

    fun resetForm() {
        _editId.value = null
        _form.value = AktivitetForm()
    }
}
