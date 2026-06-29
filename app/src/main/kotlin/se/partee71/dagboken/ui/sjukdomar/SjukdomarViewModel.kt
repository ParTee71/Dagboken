package se.partee71.dagboken.ui.sjukdomar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class SjukdomarViewModel @Inject constructor(
    private val repo: SjukdomarRepository,
) : ViewModel() {

    val all: StateFlow<List<SjukdomsEpisod>> = repo.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pagaende: StateFlow<SjukdomsEpisod?> = repo.pagaende
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    fun delete(episod: SjukdomsEpisod) {
        viewModelScope.launch {
            repo.deleteEpisod(episod)
            _snackbar.value = "${episod.typ} borttagen"
        }
    }

    fun markFrisk(episod: SjukdomsEpisod) {
        viewModelScope.launch {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            repo.saveEpisod(episod.copy(slutDatum = today))
            _snackbar.value = "Markerad som frisk ✓"
        }
    }

    fun clearSnackbar() { _snackbar.value = null }
}
