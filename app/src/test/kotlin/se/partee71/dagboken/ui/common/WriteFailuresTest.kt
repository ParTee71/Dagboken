package se.partee71.dagboken.ui.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.data.common.DataError

class WriteFailuresTest {
    @Test
    fun `en lyckad skrivning visar inget fel`() = runTest(UnconfinedTestDispatcher()) {
        val failures = WriteFailures(backgroundScope)
        failures.write { Result.success(Unit) }
        assertNull(failures.failure.value)
    }

    @Test
    fun `en misslyckad skrivning visas som UiError tills den kvitterats, och samma fel två gånger är två händelser`() = runTest(UnconfinedTestDispatcher()) {
        val failures = WriteFailures(backgroundScope)
        failures.failure.test {
            assertNull(awaitItem())
            failures.write { Result.failure(DataError.Offline) }
            assertEquals(DataError.Offline, awaitItem()?.dataError)
            failures.clear()
            assertNull(awaitItem())
            failures.write { Result.failure(DataError.Offline) }
            assertEquals(DataError.Offline, awaitItem()?.dataError)
        }
    }

    @Test
    fun `report av ett resultat säger om det misslyckades, och ett lyckat rör inte ett fel som visas`() = runTest(UnconfinedTestDispatcher()) {
        val failures = WriteFailures(backgroundScope)
        assertTrue(failures.report(Result.failure<Unit>(DataError.NotFound)))
        assertEquals(DataError.NotFound, failures.failure.value?.dataError)
        assertFalse(failures.report(Result.success(Unit)))
        failures.report(null)
        assertEquals(DataError.NotFound, failures.failure.value?.dataError)
    }
}
