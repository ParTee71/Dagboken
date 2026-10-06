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
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.PrescriptionCodec
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.TestUserScope

/** `nextSortOrder`/`upsertPlaced`: något nytt hamnar sist, och sparandet väntar aldrig på nätet. */
class EntityCollectionTest {

    @Test
    fun `något nytt hamnar efter det sista i listan`() = runTest {
        val items = FakeCollectionFactory().options()
        items.upsert(Option("a", OptionKind.ACTIVITY, "Promenad", sortOrder = 4)).getOrThrow()
        items.upsertPlaced(Option("b", OptionKind.ACTIVITY, "Yoga"), isNew = true) { copy(sortOrder = it) }.getOrThrow()
        assertEquals(5, items.get("b").getOrThrow()?.sortOrder)
        items.upsertPlaced(Option("b", OptionKind.ACTIVITY, "Yoga", sortOrder = 1), isNew = false) { copy(sortOrder = it) }.getOrThrow()
        assertEquals(1, items.get("b").getOrThrow()?.sortOrder, "något som redan finns behåller sin plats")
    }

    @Test
    fun `utan lista att läsa blir platsen 0 i stället för att vänta`() = runTest {
        val items = FakeCollectionFactory(scope = TestUserScope(uid = null)).options()
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
        val fake = FakeCollectionFactory().options()
        fake.upsert(Option("a", OptionKind.ACTIVITY, "Promenad", sortOrder = 4)).getOrThrow()
        val slow = object : EntityCollection<Option> by fake {
            override suspend fun cached(): Result<List<Option>> = awaitCancellation()
        }

        assertEquals(0, slow.nextSortOrder())
        assertEquals(2_000L, currentTime)
        assertEquals(5, fake.nextSortOrder())
    }

    @Test
    fun `updateChanged skriver bara ändrade toppfält, en ändrad map hel, och inget när inget ändrats`() = runTest {
        val factory = FakeCollectionFactory()
        val calls = mutableListOf<Pair<Set<String>, Set<String>>>()
        val recipes = object : EntityCollection<Prescription> by factory.prescriptions() {
            override suspend fun update(item: Prescription, fields: Set<String>, remove: Set<String>): Result<Unit> {
                calls += fields to remove
                return factory.prescriptions().update(item, fields, remove)
            }
        }
        val newer = mapOf("regel" to "fullmåne", "fas" to 3L)
        val before = Prescription("a", "Levaxin", schedule = Schedule.Unknown(newer))
        val path = Paths.prescriptions(factory.scope.uid.value!!)
        factory.store.set(path, "a", PrescriptionCodec.encode(before) + ("framtidaFält" to "kvar"), merge = false)

        recipes.updateChanged(PrescriptionCodec, before, before.copy()).getOrThrow()
        assertEquals(emptyList(), calls, "inget ändrat – ingen skrivning")

        // En nyckel som tagits bort ur en map räknas som en ändring, och kartan skrivs hel.
        val after = before.copy(note = "Till kvällen", schedule = Schedule.Unknown(mapOf("regel" to "fullmåne")))
        recipes.updateChanged(PrescriptionCodec, before, after).getOrThrow()
        assertEquals(listOf(setOf("note", "schedule") to emptySet<String>()), calls)
        val stored = factory.store.read(path, "a")!!
        assertEquals(mapOf("regel" to "fullmåne"), stored["schedule"], "ingen kvarbliven nyckel")
        assertEquals("kvar", stored["framtidaFält"], "okänt fält står kvar")

        recipes.updateChanged(PrescriptionCodec, after, after.copy(createdAt = null)).getOrThrow()
        assertEquals(1, calls.size, "createdAt utan värde skrivs aldrig – ingen ändring")

        factory.store.delete(path, "a")
        recipes.updateChanged(PrescriptionCodec, after, after.copy(name = "Annat")).getOrThrow()
        assertNull(factory.store.read(path, "a"), "ett raderat dokument återuppstår inte")
    }

    @Test
    fun `updateChanged tar bort ett toppfält som codecen inte längre skriver`() = runTest {
        val factory = FakeCollectionFactory()
        val tags = factory.collection(OptionalTagCodec, "options") { Paths.options(it!!) }
        val path = Paths.options(factory.scope.uid.value!!)
        val before = Tagged("a", "Promenad", tag = "ute")
        factory.store.set(path, "a", OptionalTagCodec.encode(before) + ("framtidaFält" to "kvar"), merge = false)

        tags.updateChanged(OptionalTagCodec, before, before.copy(tag = null)).getOrThrow()

        assertEquals(mapOf("name" to "Promenad", "framtidaFält" to "kvar"), factory.store.read(path, "a"))
    }

    /** En modell vars codec utelämnar ett fält utan värde – det som `updateChanged` ska ta bort. */
    private data class Tagged(override val id: String, val name: String, val tag: String?) : Identified

    private object OptionalTagCodec : DocCodec<Tagged> {
        override fun encode(value: Tagged): Doc = buildMap {
            put("name", value.name)
            value.tag?.let { put("tag", it) }
        }

        override fun decode(id: String, map: Doc) = Tagged(id, map["name"] as? String ?: "", map["tag"] as? String)
    }
}
