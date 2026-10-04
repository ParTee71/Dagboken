package se.partee71.dagboken.data.auth

import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.testing.UserFixture

/** AUTH-6: utloggningen tömmer cachen – men aldrig osynkade skrivningar (regel 1). */
class SignOutUseCaseTest {

    private fun signedIn(fixture: UserFixture) = fixture.apply { auth.authState.value = AuthUser("uid-anna") }

    @Test
    fun `utloggning väntar på synken, loggar ut och tömmer sedan cachen`() = runTest {
        val fixture = signedIn(UserFixture(backgroundScope))

        val result = fixture.signOut()

        assertEquals(Result.success(Unit), result)
        assertEquals(1, fixture.auth.signOutCalls)
        assertEquals(listOf("await inloggad", "clear utloggad"), fixture.cache.calls)
    }

    @Test
    fun `osynkade skrivningar - utloggad men cachen behålls`() = runTest {
        val fixture = signedIn(UserFixture(backgroundScope))
        fixture.cache.synced = false

        val result = fixture.signOut()

        assertEquals(Result.success(Unit), result)
        assertEquals(null, fixture.auth.authState.value)
        assertEquals(listOf("await inloggad"), fixture.cache.calls)
    }

    @Test
    fun `misslyckad utloggning rör inte cachen`() = runTest {
        val fixture = signedIn(UserFixture(backgroundScope))
        fixture.auth.nextSignOut = Result.failure(DataError.Unknown)

        val result = fixture.signOut()

        assertEquals(DataError.Unknown, result.dataError())
        assertEquals(0, fixture.cache.clearCalls)
    }

    @Test
    fun `cachen går inte att tömma - felet kommer fram, utloggad ändå`() = runTest {
        val fixture = signedIn(UserFixture(backgroundScope))
        fixture.cache.nextClear = Result.failure(DataError.Unknown)

        val result = fixture.signOut()

        assertEquals(DataError.Unknown, result.dataError())
        assertEquals(null, fixture.auth.authState.value)
    }
}
