package se.partee71.dagboken.data.auth

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
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
    fun `historiklägets täckning glöms för alla konton innan cachen töms – annat i anteckningarna står kvar (NFR-24)`() = runTest {
        val fixture = signedIn(UserFixture(backgroundScope))
        val day = LocalDate(2026, 10, 1)
        fixture.marks.marks += mapOf("history.users/uid-anna/doses.date.from" to day, "history.users/uid-bo/events.date.on" to day, "tidyUp.uid-anna" to day)

        fixture.signOut()

        assertEquals(setOf("tidyUp.uid-anna"), fixture.marks.marks.keys, "dagsgrinden rörs inte")
        assertEquals(1, fixture.cache.clearCalls)
    }

    @Test
    fun `går täckningen inte att glömma töms cachen ändå och utloggningen lyckas – integriteten går före (NFR-24)`() = runTest {
        val fixture = signedIn(UserFixture(backgroundScope))
        fixture.marks.fails = true

        val result = fixture.signOut()

        assertEquals(Result.success(Unit), result, "användaren är utloggad och cachen tömd")
        assertEquals(1, fixture.cache.clearCalls, "ingen hälsodata blir kvar i cachen")
        assertEquals(null, fixture.auth.authState.value, "utloggad ändå")
    }

    @Test
    fun `osynkade skrivningar - utloggad men cachen behålls, och med den täckningen`() = runTest {
        val fixture = signedIn(UserFixture(backgroundScope))
        fixture.cache.synced = false
        fixture.marks.marks["history.users/uid-anna/doses.date.from"] = LocalDate(2026, 10, 1)

        val result = fixture.signOut()

        assertEquals(Result.success(Unit), result)
        assertEquals(null, fixture.auth.authState.value)
        assertEquals(listOf("await inloggad"), fixture.cache.calls)
        assertTrue(fixture.marks.marks.isNotEmpty(), "täckningen beskriver cachen som står kvar")
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
