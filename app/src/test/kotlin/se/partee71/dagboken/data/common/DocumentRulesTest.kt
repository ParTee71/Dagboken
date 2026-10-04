package se.partee71.dagboken.data.common

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.ContractItem
import se.partee71.dagboken.data.ContractItemCodec
import se.partee71.dagboken.data.TestUserScope

class DocumentRulesTest {

    private val now = Instant.fromEpochSeconds(100)

    @Test
    fun `updatedAt sätts alltid och createdAt utan värde skrivs inte`() {
        val prepared = prepareForWrite(mapOf("name" to "x", "createdAt" to null, "updatedAt" to null), now)
        assertEquals(mapOf("name" to "x", "updatedAt" to now), prepared)
        assertFalse("createdAt" in prepared)
        val kept = prepareForWrite(mapOf("createdAt" to Instant.fromEpochSeconds(1)), now)
        assertEquals(Instant.fromEpochSeconds(1), kept["createdAt"])
    }

    @Test
    fun `äldre format går genom migreringen före decode, nuvarande och nyare läses som de är`() {
        val calls = mutableListOf<Int>()
        val renameNamn: Migrate = { from, collection, doc ->
            calls += from
            assertEquals("options", collection)
            doc - "namn" + ("name" to doc["namn"])
        }
        val old = mapOf("namn" to "Promenad")
        assertEquals(ContractItem("p", "Promenad"), readDocument(ContractItemCodec, "options", 0, "p", old, renameNamn))
        assertEquals(listOf(0), calls)
        val current = mapOf("name" to "Promenad")
        assertEquals(ContractItem("p", "Promenad"), readDocument(ContractItemCodec, "options", Schema.CURRENT_VERSION, "p", current, renameNamn))
        assertEquals(ContractItem("p", "Promenad"), readDocument(ContractItemCodec, "options", Schema.CURRENT_VERSION + 1, "p", current, renameNamn))
        assertEquals(ContractItem("p", "Promenad"), readDocument(ContractItemCodec, "options", null, "p", current, renameNamn))
        assertEquals(listOf(0), calls, "migreringen körs bara för äldre format")
    }

    @Test
    fun `listor sorteras på id och sedan sortOrder`() {
        val sorted = sortForList(listOf(ContractItem("c"), ContractItem("b", sortOrder = 1), ContractItem("a")))
        assertEquals(listOf("a", "c", "b"), sorted.map { it.id })
    }

    @Test
    fun `skrivskydd när användarens data är nyare än appen`() = runTest {
        assertNull(TestUserScope(version = Schema.CURRENT_VERSION).writeBlocker())
        assertEquals(DataError.UpdateRequired, TestUserScope(version = Schema.CURRENT_VERSION + 1).writeBlocker())
        assertNull(TestUserScope(uid = null, version = null).writeBlocker())
    }

    @Test
    fun `okänd version spärrar skrivning - väntar först, sedan vägras den`() = runTest {
        val scope = TestUserScope(version = null)
        var result: DataError? = DataError.Unknown
        val job = launch { result = scope.writeBlocker() }
        advanceTimeBy(1_000)
        scope.setVersion(Schema.CURRENT_VERSION)
        job.join()
        assertNull(result, "versionen kom inom väntetiden")
        assertEquals(DataError.Offline, TestUserScope(version = null).writeBlocker(), "versionen kom aldrig")
    }

    @Test
    fun `ett användardokument som inte går att läsa nekar skrivning i stället för att vänta ut den`() = runTest {
        val scope = TestUserScope(version = null)
        var result: DataError? = null
        val job = launch { result = scope.writeBlocker() }
        advanceTimeBy(1_000)
        scope.unreadable.value = "uid-test"
        job.join()
        assertEquals(DataError.PermissionDenied, result)
        assertEquals(1_000L, testScheduler.currentTime, "nekandet väntar inte ut tidsgränsen")
    }

    @Test
    fun `en version från förra användaren gäller inte den nya`() = runTest {
        val scope = TestUserScope(uid = "gammal", version = Schema.CURRENT_VERSION)
        scope.uid.value = "ny"
        assertNull(scope.currentVersion())
        assertEquals(DataError.Offline, scope.writeBlocker())
    }

    @Test
    fun `suspendRunCatching mappar fel och släpper igenom DataError`() = runTest {
        assertEquals(DataError.Offline, suspendRunCatching({ DataError.Offline }) { error("x") }.dataError())
        assertEquals(DataError.NotSignedIn, suspendRunCatching({ DataError.Unknown }) { throw DataError.NotSignedIn }.dataError())
        assertEquals(1, suspendRunCatching({ DataError.Unknown }) { 1 }.getOrThrow())
        assertNull(Result.success(1).dataError())
    }
}
