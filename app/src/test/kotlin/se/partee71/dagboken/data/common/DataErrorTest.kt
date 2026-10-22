package se.partee71.dagboken.data.common

import java.io.IOException
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Felmappningen som datalagret delar ([asDataError], [suspendRunCatching]). */
class DataErrorTest {

    @Test
    fun `ett DataError behåller sin typ, annat blir Unknown`() {
        assertEquals(DataError.Offline, DataError.Offline.asDataError())
        assertEquals(DataError.Unknown, IOException().asDataError())
        assertEquals(DataError.PermissionDenied, Result.failure<Unit>(DataError.PermissionDenied).dataError())
    }

    @Test
    fun `suspendRunCatching mappar som asDataError utan egen mappning`() = runTest {
        assertEquals(DataError.Offline, suspendRunCatching<Unit> { throw DataError.Offline }.dataError())
        assertEquals(DataError.Unknown, suspendRunCatching<Unit> { throw IOException() }.dataError())
        assertEquals(42, suspendRunCatching { 42 }.getOrThrow())
    }

    @Test
    fun `egen mappning gäller andra fel, ett DataError släpps igenom`() = runTest {
        assertEquals(DataError.NotFound, suspendRunCatching<Unit>({ DataError.NotFound }) { throw IOException() }.dataError())
        assertEquals(DataError.Offline, suspendRunCatching<Unit>({ DataError.NotFound }) { throw DataError.Offline }.dataError())
    }
}
