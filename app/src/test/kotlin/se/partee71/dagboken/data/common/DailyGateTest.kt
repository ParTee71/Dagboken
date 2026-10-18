package se.partee71.dagboken.data.common

import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import se.partee71.dagboken.data.TestUserScope

/** Dagsgrinden (#304, REC-8, HEM-10): en lyckad körning per dag, enhet och konto; fel och offline försöker igen. */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyGateTest {

    private val marks = MemoryDayMarks()
    private val user = TestUserScope()
    private val gate = DailyGate(marks, user)
    private val monday = LocalDate(2026, 9, 21)
    private val tuesday = LocalDate(2026, 9, 22)
    private var runs = 0

    private suspend fun DailyGate.attempt(day: LocalDate = monday, result: Result<Unit> = Result.success(Unit)): Result<Unit> = runOncePerDay(NAME, day) {
        runs++
        result
    }

    @Test
    fun `en lyckad körning per dag – nästa dag körs igen`() = runTest {
        gate.attempt().getOrThrow()
        gate.attempt().getOrThrow()
        assertEquals(1, runs)
        gate.attempt(tuesday).getOrThrow()
        assertEquals(2, runs)
    }

    @Test
    fun `ett fel antecknas inte, når anroparen och nästa anrop kör igen`() = runTest {
        assertEquals(DataError.QuotaExceeded, gate.attempt(result = Result.failure(DataError.QuotaExceeded)).exceptionOrNull())
        gate.attempt().getOrThrow()
        gate.attempt().getOrThrow()
        assertEquals(2, runs)
    }

    @Test
    fun `glömd dag körs igen samma dag, bara för det kontot`() = runTest {
        gate.attempt().getOrThrow()
        gate.forget(NAME)
        gate.attempt().getOrThrow()
        gate.attempt().getOrThrow()
        assertEquals(2, runs)
    }

    @Test
    fun `offline räknas inte som lyckat – nästa anrop kör igen`() = runTest {
        assertEquals(DataError.Offline, gate.attempt(result = Result.failure(DataError.Offline)).exceptionOrNull())
        assertEquals(emptyMap(), marks.marks)
        gate.attempt().getOrThrow()
        assertEquals(2, runs)
    }

    @Test
    fun `ett annat konto på samma enhet kör igen, och det första är fortfarande klart`() = runTest {
        gate.attempt().getOrThrow()
        user.uid.value = "annan"
        gate.attempt().getOrThrow()
        user.uid.value = "uid-test"
        gate.attempt().getOrThrow()
        assertEquals(2, runs)
    }

    @Test
    fun `ett kontobyte under körningen antecknas inte`() = runTest {
        gate.runOncePerDay(NAME, monday) {
            runs++
            user.uid.value = "annan"
            Result.success(Unit)
        }.getOrThrow()
        user.uid.value = "uid-test"
        gate.attempt().getOrThrow()
        assertEquals(2, runs)
    }

    @Test
    fun `utan inloggning körs åtgärden utan grind och antecknas inte`() = runTest {
        user.uid.value = null
        gate.attempt().getOrThrow()
        gate.attempt().getOrThrow()
        assertEquals(2, runs)
        assertEquals(emptyMap(), marks.marks)
    }

    @Test
    fun `anteckningen överlever en ny instans med samma lagring (processdöd)`() = runTest {
        gate.attempt().getOrThrow()
        DailyGate(marks, user).attempt().getOrThrow()
        assertEquals(1, runs)
        DailyGate(MemoryDayMarks(), user).attempt().getOrThrow()
        assertEquals(2, runs, "en annan enhet (annan lagring) kör")
    }

    @Test
    fun `samtidiga anrop ger en körning, och de som väntat får dess utfall – också ett fel`() = runTest {
        val release = CompletableDeferred<Unit>()
        val first = async {
            gate.runOncePerDay(NAME, monday) {
                runs++
                release.await()
                Result.failure(DataError.QuotaExceeded)
            }
        }
        runCurrent()
        val second = async { gate.attempt() }
        val third = async { gate.attempt() }
        runCurrent()
        release.complete(Unit)
        assertEquals(List(3) { DataError.QuotaExceeded }, listOf(first, second, third).map { it.await().exceptionOrNull() })
        assertEquals(1, runs, "Idag och Mediciner samtidigt: en städning")

        gate.attempt().getOrThrow()
        assertEquals(2, runs, "ett senare anrop försöker igen")
    }

    @Test
    fun `samtidiga anrop med lyckat utfall ger en körning`() = runTest {
        val release = CompletableDeferred<Unit>()
        val first = async {
            gate.runOncePerDay(NAME, monday) {
                runs++
                release.await()
                Result.success(Unit)
            }
        }
        runCurrent()
        val second = async { gate.attempt() }
        runCurrent()
        release.complete(Unit)
        first.await().getOrThrow()
        second.await().getOrThrow()
        assertEquals(1, runs)
    }

    @Test
    fun `går lagringen inte att nå körs åtgärden hellre igen än att fälla anroparen`() = runTest {
        marks.fails = true
        gate.attempt().getOrThrow()
        gate.attempt().getOrThrow()
        assertEquals(2, runs)
    }

    private companion object {
        const val NAME = "test"
    }
}
