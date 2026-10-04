package se.partee71.dagboken.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.SyncStatus
import se.partee71.dagboken.ui.common.shownAfter

/** [pending]: ändringar har väntat minst [SyncViewModel.PENDING_DELAY] på servern; [writeError]: en nekad skrivning att visa. */
data class SyncUiState(val pending: Boolean = false, val writeError: DataError? = null)

sealed interface SyncEvent {
    /** Felet har visats och kan släppas. */
    data object WriteErrorShown : SyncEvent
}

/** Appens synkläge för UI:t (NFR-1): indikatorn i toppraden och meddelandet om en nekad skrivning. */
@HiltViewModel
class SyncViewModel @Inject constructor(private val status: SyncStatus) : ViewModel() {

    val state: StateFlow<SyncUiState> = combine(status.syncing.shownAfter(PENDING_DELAY), status.lastWriteError, ::SyncUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncUiState())

    fun onEvent(event: SyncEvent) {
        when (event) {
            SyncEvent.WriteErrorShown -> status.clearWriteError()
        }
    }

    companion object {
        /** Så länge en skrivning får vänta innan indikatorn visas – online syns den aldrig. */
        val PENDING_DELAY = 2.seconds
    }
}
