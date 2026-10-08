package se.partee71.dagboken.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.data.export.ExportUseCase
import se.partee71.dagboken.data.legacy.LegacyImportUseCase
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.failureOrNull
import se.partee71.dagboken.ui.migration.ImportEvent
import se.partee71.dagboken.ui.migration.ImportFlow
import se.partee71.dagboken.ui.migration.ImportStage

/**
 * Underskärmen Export och import (SET-8, NAV-9). [import] = importens läge, samma som i första starten (BCK-6, BCK-14);
 * [exporting] = exporten skrivs (BCK-13). [exported] = antal poster i den sparade filen – snackbaren "Sparade N poster";
 * [failure] = exporten gick inte (snackbar via `DataError.toMessage()`). Båda nollställs med [ExportImportEvent.MessageShown].
 */
data class ExportImportUiState(
    val import: ImportStage = ImportStage.Choose,
    val exporting: Boolean = false,
    val exported: Int? = null,
    val failure: Failure? = null,
) {
    /** Medan något skrivs eller läses går det inte att starta något annat (och raderna är inaktiva). */
    val busy: Boolean get() = exporting || import is ImportStage.Reading || import is ImportStage.Writing
}

sealed interface ExportImportEvent {
    /** "Spara som JSON": dokumentväljaren (`CreateDocument("application/json")` med [ExportImportViewModel.exportFileName]) gav platsen. */
    data class ExportChosen(val uri: Uri) : ExportImportEvent

    /** En händelse i importen ("Importera backup" öppnar dokumentväljaren och skickar [ImportEvent.FileChosen]). */
    data class Import(val event: ImportEvent) : ExportImportEvent

    /** Snackbaren har visats. */
    data object MessageShown : ExportImportEvent
}

/** Export och import i inställningsarket (BCK-13, BCK-14, BCK-6, SET-8). All logik i use casen; loggar ingenting (NFR-13). */
@HiltViewModel
class ExportImportViewModel @Inject constructor(
    private val exporter: ExportUseCase,
    importer: LegacyImportUseCase,
    private val zone: Provider<TimeZone>,
) : ViewModel() {
    private val _state = MutableStateFlow(ExportImportUiState())
    val state: StateFlow<ExportImportUiState> = _state.asStateFlow()

    private val import = ImportFlow(viewModelScope, importer)
    private var exportJob: Job? = null

    init {
        viewModelScope.launch { import.state.collect { stage -> _state.update { it.copy(import = stage) } } }
    }

    /** Förslaget i filväljaren, med dagens datum: `dagboken-export-<datum>.json`. */
    fun exportFileName(): String = exporter.fileName(zone.get())

    fun onEvent(event: ExportImportEvent) {
        when (event) {
            is ExportImportEvent.ExportChosen -> export(event.uri)
            is ExportImportEvent.Import -> if (!_state.value.exporting) import.onEvent(event.event)
            ExportImportEvent.MessageShown -> _state.update { it.copy(exported = null, failure = null) }
        }
    }

    private fun export(uri: Uri) {
        if (_state.value.busy || exportJob?.isActive == true) return
        exportJob = viewModelScope.launch {
            _state.update { it.copy(exporting = true, exported = null, failure = null) }
            val result = exporter.export(uri)
            _state.update { it.copy(exporting = false, exported = result.getOrNull()?.total, failure = result.failureOrNull()) }
        }
    }
}
