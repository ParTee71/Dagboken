package se.kodhast.dagboken.data.auth

import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.kodhast.dagboken.data.common.DataError
import se.kodhast.dagboken.testing.FakeAuthRepository
import se.kodhast.dagboken.testing.UserFixture

/** AUTH-3: profilen (fotot) kontrolleras mot Google när appen är ansluten, högst en gång per dag och konto. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileCheckUseCaseTest {

    private fun TestScope.started(user: AuthUser? = AuthUser("uid-anna")): UserFixture {
        val fixture = UserFixture(backgroundScope, FakeAuthRepository(user))
        backgroundScope.launch { fixture.profileCheck() }
        runCurrent()
        return fixture
    }

    private fun TestScope.reconnect(fixture: UserFixture) {
        fixture.network.online.value = false
        runCurrent()
        fixture.network.online.value = true
        runCurrent()
    }

    @Test
    fun `utan nät görs ingenting, ansluten kontrolleras profilen en gång samma dag`() = runTest {
        val fixture = started()
        assertEquals(0, fixture.auth.reloadCalls, "utan nät")

        fixture.network.online.value = true
        runCurrent()
        assertEquals(1, fixture.auth.reloadCalls, "när appen blir ansluten")

        reconnect(fixture)
        assertEquals(1, fixture.auth.reloadCalls, "samma dag")

        fixture.clock.instant += 1.days
        reconnect(fixture)
        assertEquals(2, fixture.auth.reloadCalls, "nästa dag")
    }

    @Test
    fun `startar appen ansluten kontrolleras profilen direkt`() = runTest {
        val fixture = UserFixture(backgroundScope, FakeAuthRepository(AuthUser("uid-anna")))
        fixture.network.online.value = true
        backgroundScope.launch { fixture.profileCheck() }
        runCurrent()
        assertEquals(1, fixture.auth.reloadCalls)
    }

    @Test
    fun `ett misslyckat försök görs om nästa gång appen blir ansluten`() = runTest {
        val fixture = started()
        fixture.auth.nextReload = Result.failure(DataError.Offline)
        fixture.network.online.value = true
        runCurrent()
        assertEquals(1, fixture.auth.reloadCalls)

        fixture.auth.nextReload = Result.success(Unit)
        reconnect(fixture)
        reconnect(fixture)
        assertEquals(2, fixture.auth.reloadCalls, "ett nytt försök, sedan inget mer samma dag")
    }

    @Test
    fun `utloggad kontrolleras ingenting – efter inloggningen görs det`() = runTest {
        val fixture = started(user = null)
        fixture.network.online.value = true
        runCurrent()
        assertEquals(0, fixture.auth.reloadCalls)

        fixture.auth.authState.value = AuthUser("uid-anna")
        runCurrent()
        assertEquals(1, fixture.auth.reloadCalls)
    }
}
