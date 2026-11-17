package se.kodhast.dagboken.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/** NAV-12: ansluten = standardnätverket når internet (validerat); systemets besked följs medan någon lyssnar. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConnectivityMonitorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val network = manager.activeNetwork!!

    private fun capabilities(vararg capability: Int): NetworkCapabilities = ShadowNetworkCapabilities.newInstance().also { caps ->
        capability.forEach { shadowOf(caps).addCapability(it) }
    }

    private val validated get() = capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    /** Wifi utan internet, eller en inloggningsportal: nätverk finns, men det är inte validerat. */
    private val unvalidated get() = capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    @Test
    fun `startläget, validerat nät, förlorat nät och nät utan internet`() = runTest {
        shadowOf(manager).setNetworkCapabilities(network, validated)
        val monitor = ConnectivityMonitor(context, backgroundScope)

        monitor.online.test {
            assertTrue(awaitItem(), "startar ansluten")
            runCurrent()
            val callbacks = shadowOf(manager).networkCallbacks
            assertEquals(1, callbacks.size, "en lyssnare medan någon samlar")

            callbacks.forEach { it.onLost(network) }
            assertFalse(awaitItem())

            callbacks.forEach { it.onCapabilitiesChanged(network, unvalidated) }
            expectNoEvents()

            callbacks.forEach { it.onCapabilitiesChanged(network, validated) }
            assertTrue(awaitItem())
        }
    }

    @Test
    fun `ett ovaliderat nät är inte anslutet, och lyssnaren tas bort när ingen samlar`() = runTest {
        shadowOf(manager).setNetworkCapabilities(network, unvalidated)
        val monitor = ConnectivityMonitor(context, backgroundScope)

        monitor.online.test {
            assertFalse(awaitItem())
            runCurrent()
            assertEquals(1, shadowOf(manager).networkCallbacks.size)
        }
        advanceTimeBy(6_000)
        runCurrent()
        assertTrue(shadowOf(manager).networkCallbacks.isEmpty(), "inga lyssnare kvar")
    }
}
