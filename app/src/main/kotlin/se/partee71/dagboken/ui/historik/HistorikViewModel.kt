package se.partee71.dagboken.ui.historik

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import java.time.LocalDate
import javax.inject.Inject

/** Standardfönster för historiken — cirka ett år bakåt. */
private const val DEFAULT_RANGE_DAYS = 365

@HiltViewModel
class HistorikViewModel @Inject constructor(
    private val aktiviteterRepo: AktiviteterRepository,
    private val medicinerRepo: MedicinerRepository,
    private val handelserRepo: HandelserRepository,
    private val sjukdomarRepo: SjukdomarRepository,
) : ViewModel() {

    private val _typeFilter = MutableStateFlow(HistorikType.entries.toSet())
    val typeFilter: StateFlow<Set<HistorikType>> = _typeFilter.asStateFlow()

    private val _viewMode = MutableStateFlow(HistorikViewMode.LISTA)
    val viewMode: StateFlow<HistorikViewMode> = _viewMode.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    /**
     * Hur långt bakåt historiken läses. Utan gräns lästes hela databasen in i minnet och
     * byggdes om vid varje emission — det växer obegränsat i en dagbok som fylls på varje
     * dag. [loadMore] utökar fönstret när användaren vill längre bak (HIST-9).
     */
    private val _rangeDays = MutableStateFlow(DEFAULT_RANGE_DAYS)
    val rangeDays: StateFlow<Int> = _rangeDays.asStateFlow()

    fun loadMore() { _rangeDays.value += DEFAULT_RANGE_DAYS }

    private val incheckningEntries = combine(
        sjukdomarRepo.allIncheckningar,
        sjukdomarRepo.all,
    ) { incheckningar, episoder ->
        val typByEpisod = episoder.associate { it.id to it.typ }
        incheckningar.map { HistorikEntry.IncheckningEntry(it, typByEpisod[it.episodId].orEmpty()) }
    }

    private val allEntries = combine(
        aktiviteterRepo.all,
        medicinerRepo.takenMediciner,
        handelserRepo.all,
        incheckningEntries,
    ) { aktiviteter, mediciner, handelser, incheckningar ->
        aktiviteter.map { HistorikEntry.AktivitetEntry(it) } +
            mediciner.map { HistorikEntry.MedicinEntry(it) } +
            handelser.map { HistorikEntry.HandelseEntry(it) } +
            incheckningar
    }

    val filteredEntries: StateFlow<List<HistorikEntry>> = combine(
        allEntries,
        _typeFilter,
        _rangeDays,
    ) { entries, filter, days ->
        val from = LocalDate.now().minusDays(days.toLong()).toString()
        entries.filter { it.entryType in filter && it.datum >= from }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setViewMode(mode: HistorikViewMode) {
        _viewMode.value = mode
        if (mode == HistorikViewMode.LISTA) _selectedDate.value = null
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.update { if (it == date) null else date }
    }

    fun toggleFilter(type: HistorikType) {
        _typeFilter.update { current ->
            when {
                type !in current      -> current + type
                current.size > 1      -> current - type
                else                  -> current
            }
        }
    }

    fun delete(entry: HistorikEntry) {
        viewModelScope.launch {
            when (entry) {
                is HistorikEntry.AktivitetEntry -> aktiviteterRepo.delete(entry.aktivitet)
                is HistorikEntry.MedicinEntry -> medicinerRepo.deleteMedicin(entry.medicin)
                is HistorikEntry.HandelseEntry -> handelserRepo.delete(entry.handelse)
                is HistorikEntry.IncheckningEntry -> sjukdomarRepo.deleteIncheckning(entry.incheckning)
            }
        }
    }
}
