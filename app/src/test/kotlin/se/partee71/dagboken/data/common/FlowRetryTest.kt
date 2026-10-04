package se.partee71.dagboken.data.common

import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Omförsök för flöden som appen följer hela tiden (hushållets version, inställningarna). */
class FlowRetryTest {

    @Test
    fun `pausen dubblas för varje försök upp till taket`() {
        val pauses = (0L..6L).map { backoff(2.seconds, 60.seconds, it) }
        assertEquals(listOf(2, 4, 8, 16, 32, 60, 60).map { it.seconds }, pauses)
        assertEquals(60.seconds, backoff(2.seconds, 60.seconds, Long.MAX_VALUE), "inget överslag")
    }

    @Test
    fun `godkända fel försöks igen efter pausen och ersättningsvärdet sänds först`() = runTest {
        var failures = 2
        val source = flow {
            if (failures-- > 0) throw IOException()
            emit("värde")
        }

        val values = source.retryWithBackoff(1.seconds, 30.seconds, retryOn = { it is IOException }, beforeRetry = { emit("standard") }).toList()

        assertEquals(listOf("standard", "standard", "värde"), values)
        assertEquals(3_000L, testScheduler.currentTime, "1 s + 2 s")
    }

    @Test
    fun `andra fel släpps igenom direkt`() = runTest {
        val source = flow<String> { throw DataError.PermissionDenied }
        assertFailsWith<DataError.PermissionDenied> {
            source.retryWithBackoff(1.seconds, 30.seconds, retryOn = { it is IOException }).toList()
        }
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `ett bestående fel försöks inte igen – ersättningen står kvar`() = runTest {
        var attempts = 0
        val source = flow<String> {
            attempts++
            throw DataError.PermissionDenied
        }
        assertEquals(listOf("–"), source.withFallback("–").toList())
        assertEquals(1, attempts)
    }

    @Test
    fun `pausen börjar om när något lästs igen`() = runTest {
        var attempt = 0
        val source = flow {
            attempt++
            if (attempt in setOf(2, 3)) emit("ok $attempt")
            if (attempt < 4) throw IOException("nere") else emit("klar")
        }
        val start = currentTime
        assertEquals(listOf("–", "ok 2", "–", "ok 3", "–", "klar"), source.withFallback("–").toList())
        assertEquals(3 * 2_000L, currentTime - start, "varje paus är den första, 2 s")
    }

    @Test
    fun `ett tillägg visar ersättningen vid fel och kommer tillbaka efter pausen`() = runTest {
        var failures = 1
        val source = flow {
            if (failures-- > 0) throw IOException("nere")
            emit("Anna")
        }
        assertEquals(listOf("–", "Anna"), source.withFallback("–").toList())
    }
}
