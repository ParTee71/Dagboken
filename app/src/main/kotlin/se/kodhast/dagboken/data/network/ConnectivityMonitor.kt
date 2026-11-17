package se.kodhast.dagboken.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import se.kodhast.dagboken.di.ApplicationScope

/** Om appen har fungerande internet just nu – pluppen på avataren (NAV-12) och när profilfotot kontrolleras (AUTH-3). */
interface NetworkStatus {
    /** Sant när enhetens nätverk når internet (Android har kontrollerat det); falskt utan nät och bakom en inloggningsportal. */
    val online: StateFlow<Boolean>
}

/**
 * [NetworkStatus] ur systemets standardnätverk: `registerDefaultNetworkCallback`, ansluten = nätverket har
 * `NET_CAPABILITY_VALIDATED` (internet, inte bara wifi). Lyssnar bara medan någon samlar (och fem sekunder efter, så att
 * en skärmrotation inte registrerar om). Kräver `ACCESS_NETWORK_STATE`.
 */
@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope
) : NetworkStatus {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override val online: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.isOnline())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(current())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), current())

    private fun current(): Boolean = manager.activeNetwork?.let(manager::getNetworkCapabilities)?.isOnline() == true

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun NetworkCapabilities.isOnline(): Boolean = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
