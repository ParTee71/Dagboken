package se.kodhast.dagboken.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.kodhast.dagboken.data.common.SyncStatus
import se.kodhast.dagboken.ui.common.AppErrors
import se.kodhast.ui.account.NetworkStatus
import se.kodhast.ui.common.SyncUiState
import se.kodhast.ui.common.shownAfter

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

    // Felet mappas en gång per nytt fel, inte i combine: samma nekade skrivning är då samma UiError medan prickarna och
    // anslutningen ändras, så att meddelandet inte visas igen (UiError jämförs på identitet).
    private val writeError = status.lastWriteError.map { it?.let(AppErrors::map) }

    // Anslutningen är känd direkt: startläget har den, så att pluppen inte blinkar till "Utan anslutning".
    val state: StateFlow<SyncUiState> = combine(status.syncing.shownAfter(PENDING_DELAY), writeError, network.online, ::SyncUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncUiState(online = network.online.value))

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
