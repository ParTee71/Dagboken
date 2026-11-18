package se.kodhast.dagboken.ui.sync

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import se.kodhast.dagboken.R
import se.kodhast.dagboken.data.common.DataError
import se.kodhast.dagboken.data.common.SyncStatus
import se.kodhast.dagboken.testing.FakeNetworkStatus
import se.kodhast.dagboken.testing.MainDispatcherRule
import se.kodhast.ui.common.SyncUiState

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private class FakeSyncStatus : SyncStatus {
        override val syncing = MutableStateFlow(false)
        override val lastWriteError = MutableStateFlow<DataError?>(null)
        override fun clearWriteError() {
            lastWriteError.value = null
        }

        override suspend fun trackWork(work: suspend () -> Result<Unit>) {
            work()
        }
    }

    private val status = FakeSyncStatus()
    private val network = FakeNetworkStatus()
    private val viewModel = SyncViewModel(status, network)

    @Test
    fun `väntande ändringar visas först efter två sekunder (NFR-22)`() = runTest(main.dispatcher) {
        viewModel.state.test {
            assertEquals(SyncUiState(), awaitItem())
            status.syncing.value = true
            testScheduler.advanceTimeBy(SyncViewModel.PENDING_DELAY.inWholeMilliseconds - 1)
            expectNoEvents()
            testScheduler.advanceTimeBy(2)
            assertEquals(SyncUiState(pending = true), awaitItem())
            status.syncing.value = false
            assertEquals(SyncUiState(), awaitItem())
        }
    }

    @Test
    fun `en nekad skrivning visas tills den släppts`() = runTest(main.dispatcher) {
        viewModel.state.test {
            assertEquals(SyncUiState(), awaitItem())
            status.lastWriteError.value = DataError.PermissionDenied
            val shown = checkNotNull(awaitItem().writeError)
            assertEquals(DataError.PermissionDenied, shown.cause)
            assertEquals(R.string.error_permission_denied, shown.message)
            viewModel.onEvent(SyncEvent.WriteErrorShown)
            assertEquals(SyncUiState(), awaitItem())
        }
        assertNull(status.lastWriteError.value)
    }

    @Test
    fun `samma nekade skrivning är samma fel medan synkläget ändras, så att meddelandet inte visas igen`() = runTest(main.dispatcher) {
        viewModel.state.test {
            awaitItem()
            status.lastWriteError.value = DataError.PermissionDenied
            val shown = checkNotNull(awaitItem().writeError)
            network.online.value = true
            val after = awaitItem()
            assertTrue(after.online)
            assertSame(shown, after.writeError)
        }
    }

    @Test
    fun `anslutningen följer nätet – pluppen på avataren (NAV-12)`() = runTest(main.dispatcher) {
        viewModel.state.test {
            assertEquals(SyncUiState(), awaitItem())
            network.online.value = true
            assertEquals(SyncUiState(online = true), awaitItem())
            network.online.value = false
            assertEquals(SyncUiState(), awaitItem())
        }
    }

    @Test
    fun `startläget har anslutningen redan, så att pluppen inte blinkar`() = runTest(main.dispatcher) {
        val online = SyncViewModel(status, FakeNetworkStatus(online = true))
        assertEquals(SyncUiState(online = true), online.state.value)
    }
}
