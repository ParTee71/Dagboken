package se.kodhast.dagboken.ui.sync

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import se.kodhast.dagboken.data.common.DataError
import se.kodhast.dagboken.data.common.SyncStatus
import se.kodhast.dagboken.data.network.NetworkStatus
import se.kodhast.ui.common.shownAfter

/**
 * [pending]: ändringar har väntat minst [SyncViewModel.PENDING_DELAY] på servern; [writeError]: en nekad skrivning att visa;
 * [online]: appen har internet (pluppen på avataren, NAV-12).
 */
@Immutable
data class SyncUiState(val pending: Boolean = false, val writeError: DataError? = null, val online: Boolean = false)

sealed interface SyncEvent {
    /** Felet har visats och kan släppas. */
    data object WriteErrorShown : SyncEvent
}

/**
 * Appens synk- och anslutningsläge för UI:t: prickarna i sidhuvudet och meddelandet om en nekad skrivning (NFR-22) och
 * anslutningspluppen på avataren (NAV-12).
 */
@HiltViewModel
class SyncViewModel @Inject constructor(private val status: SyncStatus, network: NetworkStatus) : ViewModel() {

    val state: StateFlow<SyncUiState> = combine(status.syncing.shownAfter(PENDING_DELAY), status.lastWriteError, network.online, ::SyncUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncUiState())

    fun onEvent(event: SyncEvent) {
        when (event) {
            SyncEvent.WriteErrorShown -> status.clearWriteError()
        }
    }

    companion object {
        /** Så länge en skrivning får vänta innan prickarna hoppar – online syns det aldrig. */
        val PENDING_DELAY = 2.seconds
    }
}
