package se.partee71.dagboken.ui.historik

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import javax.inject.Inject

@HiltViewModel
class HistorikViewModel @Inject constructor(
    private val aktiviteterRepo: AktiviteterRepository,
    private val medicinerRepo: MedicinerRepository,
    private val handelserRepo: HandelserRepository,
    private val sjukdomarRepo: SjukdomarRepository,
) : ViewModel() {

    val typeFilter = MutableStateFlow(HistorikType.entries.toSet())

    private val incheckningEntries = combine(
        sjukdomarRepo.allIncheckningar,
        sjukdomarRepo.all,
    ) { incheckningar, episoder ->
        val typByEpisod = episoder.associate { it.id to it.typ }
        incheckningar.map { HistorikEntry.IncheckningEntry(it, typByEpisod[it.episodId].orEmpty()) }
    }

    private val allEntries = combine(
        aktiviteterRepo.all,
        medicinerRepo.allMediciner,
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
        typeFilter,
    ) { entries, filter ->
        entries.filter { it.entryType in filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFilter(type: HistorikType) {
        val current = typeFilter.value
        typeFilter.value = if (type in current) {
            if (current.size > 1) current - type else current
        } else {
            current + type
        }
    }
}
