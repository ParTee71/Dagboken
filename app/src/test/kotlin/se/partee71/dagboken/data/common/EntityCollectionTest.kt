package se.partee71.dagboken.data.common

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.data.ContractItem
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.TestUserScope

/** `nextSortOrder`/`upsertPlaced`: något nytt hamnar sist, och sparandet väntar aldrig på nätet. */
class EntityCollectionTest {

    @Test
    fun `något nytt hamnar efter det sista i listan`() = runTest {
        val items = FakeCollectionFactory().contractItems()
        items.upsert(ContractItem("a", "Promenad", sortOrder = 4)).getOrThrow()
        items.upsertPlaced(ContractItem("b", "Yoga"), isNew = true) { copy(sortOrder = it) }.getOrThrow()
        assertEquals(5, items.get("b").getOrThrow()?.sortOrder)
        items.upsertPlaced(ContractItem("b", "Yoga", sortOrder = 1), isNew = false) { copy(sortOrder = it) }.getOrThrow()
        assertEquals(1, items.get("b").getOrThrow()?.sortOrder, "något som redan finns behåller sin plats")
    }

    @Test
    fun `utan lista att läsa blir platsen 0 i stället för att vänta`() = runTest {
        val items = FakeCollectionFactory(scope = TestUserScope(uid = null)).contractItems()
        assertEquals(0, items.nextSortOrder())
    }

    @Test
    fun `fältvägar är segment – en punkt i en nyckel är ingen väg`() {
        val before = mapOf("theme" to mapOf("mode" to "auto", "a.b" to 1), "name" to "x", "list" to listOf(1, 2))
        val after = mapOf("theme" to mapOf("mode" to "auto", "a.b" to 2), "name" to "x", "list" to listOf(1, 3), "updatedAt" to "nu")

        val changed = changedFields(before, after)

        assertEquals(setOf(listOf("theme", "a.b"), listOf("list"), listOf("updatedAt")), changed)
        assertEquals(mapOf("updatedAt" to "nu", "theme" to mapOf("a.b" to 2), "list" to listOf(1, 3)), fieldsForMerge(after, changed))
        assertFailsWith<IllegalArgumentException> { fieldsForMerge(after, setOf(listOf("theme.mode"))) }
        assertFailsWith<IllegalArgumentException> { fieldsForMerge(after, setOf(emptyList())) }
    }

    private fun <R> snapshots(vararg values: Snapshot<R>): () -> Flow<Snapshot<R>> = { flowOf(*values) }

    @Test
    fun `ett dokument ur cachen, eller bekräftat saknas, är ett svar direkt`() = runTest {
        assertEquals("a", firstFromCache({ DataError.Unknown }, { it.isDocumentAnswer() }, snapshots(Snapshot<String?>("a", fromCache = true))).getOrThrow())
        assertNull(firstFromCache({ DataError.Unknown }, { it.isDocumentAnswer() }, snapshots(Snapshot<String?>(null, fromCache = false))).getOrThrow())
        assertEquals(0L, currentTime)
    }

    @Test
    fun `saknas dokumentet i cachen och servern nås inte blir det Offline direkt`() = runTest {
        val result = firstFromCache({ DataError.Unknown }, { it.isDocumentAnswer() }, snapshots(Snapshot<String?>(null, fromCache = true)))
        assertEquals(DataError.Offline, result.exceptionOrNull())
        assertEquals(0L, currentTime, "ingen väntan")
    }

    @Test
    fun `kommer ingen ögonblicksbild alls blir det Offline efter skyddsnätet`() = runTest {
        val result = firstFromCache<String>({ DataError.Unknown }) { flow { awaitCancellation() } }
        assertEquals(DataError.Offline, result.exceptionOrNull())
        assertEquals(15_000L, currentTime)
    }

    @Test
    fun `utloggad ger NotSignedIn direkt och ett fel i lyssnaren mappas`() = runTest {
        assertEquals(DataError.NotSignedIn, firstFromCache<String>({ DataError.Unknown }) { throw DataError.NotSignedIn }.exceptionOrNull())
        assertEquals(DataError.Unknown, firstFromCache<String>({ DataError.Unknown }) { flow { throw IllegalStateException() } }.exceptionOrNull())
    }

    @Test
    fun `ett fel i lyssnaren, även inlindat i ett avbrott, blir ett misslyckande`() = runTest {
        val wrapped = CancellationException("lyssnaren avslutad").apply { initCause(IllegalStateException("nekad")) }
        val result = firstFromCache<String>({ DataError.PermissionDenied }) { flow { throw wrapped } }
        assertEquals(DataError.PermissionDenied, result.exceptionOrNull())
    }

    @Test
    fun `sortOrder väntar högst 2 s på listan och blir då 0`() = runTest {
        val fake = FakeCollectionFactory().contractItems()
        fake.upsert(ContractItem("a", "Promenad", sortOrder = 4)).getOrThrow()
        val slow = object : EntityCollection<ContractItem> by fake {
            override suspend fun cached(): Result<List<ContractItem>> = awaitCancellation()
        }

        assertEquals(0, slow.nextSortOrder())
        assertEquals(2_000L, currentTime)
        assertEquals(5, fake.nextSortOrder())
    }
}
