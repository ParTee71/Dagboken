package se.partee71.dagboken.ui.auth

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.testing.UserFixture

/**
 * Robolectric bara för att kunna skicka en `Context` i händelsen; allt annat är fakes. Köad
 * dispatcher, så att ordningsproblem inte döljs (som i UserSessionTest).
 */
@RunWith(RobolectricTestRunner::class)
class AuthViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun TestScope.viewModel(fixture: UserFixture = UserFixture(backgroundScope)) =
        fixture to AuthViewModel(fixture.auth, fixture.session, fixture.ensureUser, fixture.signOut)

    private suspend fun AuthViewModel.awaitGate(gate: AuthGate) = state.first { it.gate == gate && !it.busy }

    @Test
    fun `utloggad visar inloggningen utan fel`() = runTest(main.dispatcher) {
        val (_, vm) = viewModel()
        assertEquals(AuthUiState(), vm.state.value)
        assertEquals(AuthUiState(AuthGate.SignedOut), vm.awaitGate(AuthGate.SignedOut))
    }

    @Test
    fun `kontot med namn, e-post och foto följer inloggningen och släpps vid utloggning (AUTH-3)`() = runTest(main.dispatcher) {
        val fixture = UserFixture(backgroundScope)
        fixture.storeUser("uid-anna")
        val anna = AuthUser("uid-anna", "Anna Berg", "anna.berg@exempel.se", "https://exempel.se/anna.jpg")
        fixture.auth.authState.value = anna
        val (_, vm) = viewModel(fixture)

        assertEquals(anna, vm.awaitGate(AuthGate.Ready).account)

        vm.onEvent(AuthEvent.SignOut)
        assertEquals(null, vm.awaitGate(AuthGate.SignedOut).account)
    }

    @Test
    fun `första inloggningen skapar användardokumentet och släpper in användaren`() = runTest(main.dispatcher) {
        val (fixture, vm) = viewModel()
        vm.awaitGate(AuthGate.SignedOut)

        vm.onEvent(AuthEvent.SignIn(context))

        assertEquals(AuthUiState(AuthGate.Ready, account = AuthUser("uid-anna")), vm.awaitGate(AuthGate.Ready))
        assertEquals(Schema.CURRENT_VERSION.toLong(), fixture.user("uid-anna")?.get("schemaVersion"))
        assertEquals(1, fixture.auth.signInCalls)
    }

    @Test
    fun `avbruten inloggning är inget fel`() = runTest(main.dispatcher) {
        val (fixture, vm) = viewModel()
        fixture.auth.nextSignIn = Result.failure(DataError.Cancelled)

        vm.onEvent(AuthEvent.SignIn(context))

        assertEquals(AuthUiState(AuthGate.SignedOut), vm.awaitGate(AuthGate.SignedOut))
    }

    @Test
    fun `misslyckad inloggning visar felet tills det visats`() = runTest(main.dispatcher) {
        val (fixture, vm) = viewModel()
        fixture.auth.nextSignIn = Result.failure(DataError.Offline)

        vm.onEvent(AuthEvent.SignIn(context))

        assertEquals(DataError.Offline, vm.state.first { it.error != null }.error)
        vm.onEvent(AuthEvent.ErrorShown)
        assertEquals(AuthUiState(AuthGate.SignedOut), vm.state.first { it.error == null })
    }

    @Test
    fun `inloggad användare med befintligt dokument går direkt in utan att fråga servern`() = runTest(main.dispatcher) {
        val fixture = UserFixture(backgroundScope)
        fixture.storeUser("uid-anna")
        fixture.auth.authState.value = AuthUser("uid-anna")
        val (_, vm) = viewModel(fixture)

        vm.awaitGate(AuthGate.Ready)
        assertEquals(0, fixture.directory.calls)
    }

    @Test
    fun `dokumentet kunde inte skapas - felet visas och knappen försöker igen utan ny inloggning`() = runTest(main.dispatcher) {
        val fixture = UserFixture(backgroundScope)
        fixture.directory.failure = DataError.Offline
        fixture.auth.authState.value = AuthUser("uid-anna")
        val (_, vm) = viewModel(fixture)

        val failed = vm.state.first { it.error != null }
        assertEquals(AuthGate.NeedsUser, failed.gate)
        assertEquals(DataError.Offline, failed.error)

        fixture.directory.failure = null
        vm.onEvent(AuthEvent.SignIn(context))

        vm.awaitGate(AuthGate.Ready)
        assertEquals(0, fixture.auth.signInCalls)
    }

    @Test
    fun `utloggning och inloggning med ett annat konto ger det kontots dokument`() = runTest(main.dispatcher) {
        val fixture = UserFixture(backgroundScope)
        fixture.storeUser("uid-anna")
        val (_, vm) = viewModel(fixture)
        vm.onEvent(AuthEvent.SignIn(context))
        vm.awaitGate(AuthGate.Ready)
        assertEquals("uid-anna", fixture.session.uid.value)

        vm.onEvent(AuthEvent.SignOut)
        vm.awaitGate(AuthGate.SignedOut)
        assertEquals(1, fixture.auth.signOutCalls)
        // AUTH-6: cachen töms efter utloggningen, så att nästa konto börjar tomt.
        assertEquals(listOf("await inloggad", "clear utloggad"), fixture.cache.calls)
        fixture.auth.nextSignIn = Result.success(AuthUser("uid-erik"))
        vm.onEvent(AuthEvent.SignIn(context))

        vm.awaitGate(AuthGate.Ready)
        assertEquals("uid-erik", fixture.session.uid.value)
        assertEquals(Schema.CURRENT_VERSION.toLong(), fixture.user("uid-erik")?.get("schemaVersion"))
    }

    @Test
    fun `misslyckad utloggning visar felet och användaren är kvar`() = runTest(main.dispatcher) {
        val fixture = UserFixture(backgroundScope)
        fixture.storeUser("uid-anna")
        fixture.auth.authState.value = AuthUser("uid-anna")
        fixture.auth.nextSignOut = Result.failure(DataError.Unknown)
        val (_, vm) = viewModel(fixture)
        vm.awaitGate(AuthGate.Ready)

        vm.onEvent(AuthEvent.SignOut)

        assertEquals(AuthUiState(AuthGate.Ready, error = DataError.Unknown, account = AuthUser("uid-anna")), vm.state.first { it.error != null })
        assertEquals(0, fixture.cache.clearCalls)
    }

    @Test
    fun `ett användardokument som inte går att läsa visar inloggningen med felet`() = runTest(main.dispatcher) {
        val fixture = UserFixture(backgroundScope)
        fixture.storeUser("uid-anna")
        fixture.denied.value = setOf("uid-anna")
        fixture.directory.failure = DataError.PermissionDenied
        fixture.auth.authState.value = AuthUser("uid-anna")
        val (_, vm) = viewModel(fixture)

        assertEquals(AuthGate.NeedsUser, vm.awaitGate(AuthGate.NeedsUser).gate)
        vm.onEvent(AuthEvent.SignIn(context))
        assertEquals(DataError.PermissionDenied, vm.state.first { it.error != null }.error)
    }

    @Test
    fun `byte av konto medan dokumentet skapas - det nya kontot får sitt eget och inget fel`() = runTest(main.dispatcher) {
        val fixture = UserFixture(backgroundScope)
        val hold = CompletableDeferred<Unit>()
        fixture.directory.hold = hold
        val (_, vm) = viewModel(fixture)
        fixture.auth.authState.value = AuthUser("uid-anna")
        vm.state.first { it.busy }

        fixture.auth.signOut()
        fixture.auth.authState.value = AuthUser("uid-erik")
        vm.state.first { it.gate == AuthGate.NeedsUser }
        hold.complete(Unit)

        assertEquals(AuthUiState(AuthGate.Ready, account = AuthUser("uid-erik")), vm.awaitGate(AuthGate.Ready))
        assertEquals("uid-erik", fixture.session.uid.value)
    }

    @Test
    fun `appen visas inte förrän användarens version är känd`() = runTest(main.dispatcher) {
        val fixture = UserFixture(backgroundScope)
        fixture.storeUser("uid-anna", mapOf("schemaVersion" to (Schema.CURRENT_VERSION + 1).toLong()))
        fixture.versionsKnown.value = false
        fixture.auth.authState.value = AuthUser("uid-anna")
        val (_, vm) = viewModel(fixture)

        assertEquals(AuthGate.Loading, vm.awaitGate(AuthGate.Loading).gate)
        fixture.versionsKnown.value = true
        vm.awaitGate(AuthGate.UpdateRequired)
    }

    @Test
    fun `användardata med nyare schemaversion visar Uppdatera appen`() = runTest(main.dispatcher) {
        val fixture = UserFixture(backgroundScope)
        fixture.storeUser("uid-anna", mapOf("schemaVersion" to (Schema.CURRENT_VERSION + 1).toLong()))
        val (_, vm) = viewModel(fixture)

        vm.onEvent(AuthEvent.SignIn(context))

        vm.awaitGate(AuthGate.UpdateRequired)
        assertEquals(0, fixture.directory.calls, "ett befintligt dokument behöver inte skapas")
    }
}
