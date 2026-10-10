package se.partee71.dagboken.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Archivable
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.ui.components.ListArchive
import se.partee71.dagboken.ui.components.UndoRequest

/** Arkivera med svep, Ångra och "Visa arkiverade" – samma händelser i alla listor (SET-5). */
sealed interface ArchiveEvent {
    data class Archive(val id: String, val name: String) : ArchiveEvent

    data object Undo : ArchiveEvent

    data object UndoDismissed : ArchiveEvent

    data object ToggleArchived : ArchiveEvent

    data object ErrorShown : ArchiveEvent
}

/**
 * Den enda arkiveringslogiken för en listskärms ViewModel (skill shared-ui-components): vad
 * `SwipeToHide`, `UndoSnackbar` och "Visa arkiverade" i `EntityListScreen` behöver. [setArchived]
 * skriver; ett fel visas som meddelande ([failure]) i stället för att tappas. Samma mekanik för
 * att dölja en rad: [setArchived] sätter då t.ex. `hidden` och [undoFormat] är "%s dold". [errorMessage] ger en
 * egen text för ett fel som inte är ett `DataError` (t.ex. en dubblett som nekas vid Ångra). ViewModeln exponerar
 * den som `archive`, och skärmen skickar `archive.collectAsListArchive()` till listan.
 */
class ArchiveActions(
    private val scope: CoroutineScope,
    private val setArchived: suspend (id: String, archived: Boolean) -> Result<Unit>,
    @param:StringRes private val undoFormat: Int = R.string.archived_format,
    private val errorMessage: (Throwable) -> Int? = { null },
) {
    private val _showingArchived = MutableStateFlow(false)
    val showingArchived: StateFlow<Boolean> = _showingArchived.asStateFlow()

    private val _undo = MutableStateFlow<UndoRequest?>(null)
    val undo: StateFlow<UndoRequest?> = _undo.asStateFlow()

    /** Ett misslyckat arkivera/ångra med sin text, tills listan visat det. Två fel i rad är två värden. */
    private val _failure = MutableStateFlow<Failure?>(null)
    val failure: StateFlow<Failure?> = _failure.asStateFlow()

    /** Aktiva först; arkiverade sist och bara när de visas. Ordningen i övrigt behålls. */
    fun <T : Archivable> visible(source: Flow<List<T>>): Flow<List<T>> =
        combine(source, _showingArchived) { all, showArchived -> all.filter { showArchived || !it.archived }.sortedBy { it.archived } }

    /** Ett fel från en annan skrivning i samma lista (t.ex. en avprickning) visas på samma sätt. */
    fun report(error: DataError) {
        _failure.value = error.toFailure(errorMessage)
    }

    /** Visar felet i [result], om det misslyckades; `true` = misslyckat. */
    private fun failed(result: Result<Unit>): Boolean {
        _failure.value = result.failureOrNull(errorMessage) ?: return false
        return true
    }

    fun onEvent(event: ArchiveEvent) {
        when (event) {
            is ArchiveEvent.Archive -> scope.launch {
                if (!failed(setArchived(event.id, true))) _undo.value = UndoRequest(event.id, event.name, undoFormat)
            }
            ArchiveEvent.Undo -> {
                val request = _undo.value ?: return
                _undo.value = null
                scope.launch { failed(setArchived(request.id, false)) }
            }
            ArchiveEvent.UndoDismissed -> _undo.value = null
            ArchiveEvent.ToggleArchived -> _showingArchived.value = !_showingArchived.value
            ArchiveEvent.ErrorShown -> _failure.value = null
        }
    }
}

/** Tillståndet som listan visar, med händelserna tillbaka hit (SET-5); [showToggle] = "Visa arkiverade" i menyn. */
@Composable
fun ArchiveActions.collectAsListArchive(showToggle: Boolean = true): ListArchive {
    val showing by showingArchived.collectAsStateWithLifecycle()
    val request by undo.collectAsStateWithLifecycle()
    val shown by failure.collectAsStateWithLifecycle()
    return ListArchive(showing, request, shown, showToggle, ::onEvent)
}
