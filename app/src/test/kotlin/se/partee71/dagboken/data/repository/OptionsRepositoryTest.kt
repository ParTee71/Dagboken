package se.partee71.dagboken.data.repository

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionIds
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.firestore.CollectionTable
import se.partee71.dagboken.data.firestore.Paths

/** Alternativlistorna (DAT-9, DAT-13, SET-5, SET-6, SET-9, SET-11) mot `FakeCollection`. */
class OptionsRepositoryTest {

    private val factory = FakeCollectionFactory()
    private val options = factory.options()
    private val repository = DefaultOptionsRepository(factory)

    private suspend fun all() = options.getAll().getOrThrow().associateBy { it.id }

    @Test
    fun `ett nytt alternativ får id ur lista och namn och hamnar sist (DAT-13)`() = runTest {
        options.upsert(Option("activity-yoga", OptionKind.ACTIVITY, "Yoga", sortOrder = 3)).getOrThrow()

        repository.add(OptionKind.ACTIVITY, " Promenad ").getOrThrow()

        val added = all().getValue(OptionIds.of(OptionKind.ACTIVITY, "Promenad"))
        assertEquals(Option(OptionIds.of(OptionKind.ACTIVITY, "Promenad"), OptionKind.ACTIVITY, "Promenad", sortOrder = 4), added)
    }

    @Test
    fun `samma namn i en annan lista är ett eget alternativ`() = runTest {
        repository.add(OptionKind.ACTIVITY, "Promenad").getOrThrow()
        repository.add(OptionKind.EVENT, "Promenad").getOrThrow()

        assertEquals(setOf(OptionIds.of(OptionKind.ACTIVITY, "Promenad"), OptionIds.of(OptionKind.EVENT, "Promenad")), all().keys)
    }

    @Test
    fun `ett arkiverat alternativ med samma namn återställs i stället för att dubbleras`() = runTest {
        val id = OptionIds.of(OptionKind.SYMPTOM, "Huvudvärk")
        options.upsert(Option(id, OptionKind.SYMPTOM, "Huvudvärk", favorite = true, sortOrder = 2, archived = true)).getOrThrow()

        repository.add(OptionKind.SYMPTOM, "Huvudvärk").getOrThrow()

        assertEquals(listOf(Option(id, OptionKind.SYMPTOM, "Huvudvärk", favorite = true, sortOrder = 2)), all().values.toList())
    }

    @Test
    fun `är id upptaget av ett alternativ som bytt namn får det nya ett eget id`() = runTest {
        val taken = OptionIds.of(OptionKind.ACTIVITY, "Promenad")
        options.upsert(Option(taken, OptionKind.ACTIVITY, "Gå ut")).getOrThrow()

        repository.add(OptionKind.ACTIVITY, "Promenad").getOrThrow()

        val stored = all()
        assertEquals("Gå ut", stored.getValue(taken).name, "det omdöpta alternativet skrivs inte över")
        val added = stored.values.single { it.name == "Promenad" }
        assertNotEquals(taken, added.id)
    }

    @Test
    fun `går listan inte att läsa ur cachen skrivs ingenting – inte heller över ett befintligt id`() = runTest {
        val renamed = Option(OptionIds.of(OptionKind.ACTIVITY, "Promenad"), OptionKind.ACTIVITY, "Gå ut", favorite = true, sortOrder = 3)
        options.upsert(renamed).getOrThrow()
        val before = factory.store.documents.value

        val offline = DefaultOptionsRepository(CachedOverride(factory, Result.failure(DataError.Offline)))
        assertEquals(DataError.Offline, offline.add(OptionKind.ACTIVITY, "Promenad").exceptionOrNull())
        assertEquals(DataError.Offline, offline.setArchived(renamed.id, archived = false).exceptionOrNull())
        assertEquals(DataError.Offline, offline.rename(renamed, "Annat").exceptionOrNull())
        val broken = DefaultOptionsRepository(CachedOverride(factory, Result.failure(DataError.Unknown)))
        assertIs<DataError>(broken.add(OptionKind.ACTIVITY, "Promenad").exceptionOrNull())

        assertEquals(before, factory.store.documents.value)
    }

    @Test
    fun `med ofullständig cache blir ett befintligt arkiverat dokument aktivt och behåller stjärnan`() = runTest {
        val id = OptionIds.of(OptionKind.ACTIVITY, "Promenad")
        options.upsert(Option(id, OptionKind.ACTIVITY, "Gå ut", favorite = true, sortOrder = 3, archived = true)).getOrThrow()
        factory.store.set(Paths.options("uid-test"), id, mapOf("futureField" to "kvar"), merge = true)
        val incomplete = DefaultOptionsRepository(CachedOverride(factory, Result.success(emptyList())))

        incomplete.add(OptionKind.ACTIVITY, "Promenad").getOrThrow()

        assertEquals(Option(id, OptionKind.ACTIVITY, "Promenad", favorite = true, sortOrder = 0), all().getValue(id))
        assertEquals("kvar", factory.store.read(Paths.options("uid-test"), id)?.get("futureField"))
    }

    @Test
    fun `ett namn som redan står bland de aktiva läggs inte till igen, oavsett skiftläge (SET-5)`() = runTest {
        options.upsert(Option("a", OptionKind.SYMPTOM, "Huvudvärk")).getOrThrow()
        val before = factory.store.documents.value

        val result = repository.add(OptionKind.SYMPTOM, " huvudVÄRK ")

        assertEquals("huvudVÄRK", assertIs<DuplicateOptionName>(result.exceptionOrNull()).name)
        assertEquals(before, factory.store.documents.value)
        repository.add(OptionKind.ACTIVITY, "Huvudvärk").getOrThrow() // en annan lista
    }

    @Test
    fun `återställning nekas när ett aktivt alternativ redan har namnet (SET-5, SET-6, SET-9)`() = runTest {
        val archived = Option(OptionIds.of(OptionKind.EVENT, "Yrsel"), OptionKind.EVENT, "Yrsel", sortOrder = 1, archived = true)
        options.batch(listOf(archived, Option("ny", OptionKind.EVENT, "yrsel", sortOrder = 2))).getOrThrow()
        val before = factory.store.documents.value

        assertIs<DuplicateOptionName>(repository.setArchived(archived.id, archived = false).exceptionOrNull())
        assertIs<DuplicateOptionName>(repository.add(OptionKind.EVENT, "Yrsel").exceptionOrNull(), "inte heller via lägg till")
        assertEquals(before, factory.store.documents.value)

        repository.setArchived("ny", archived = true).getOrThrow()
        repository.setArchived(archived.id, archived = false).getOrThrow()
        assertEquals(false, all().getValue(archived.id).archived, "när dubbletten är arkiverad går det")
    }

    @Test
    fun `återställning skriver bara archived – namn, favorit och plats från en annan enhet står kvar`() = runTest {
        val id = OptionIds.of(OptionKind.SYMPTOM, "Huvudvärk")
        options.upsert(Option(id, OptionKind.SYMPTOM, "Huvudvärk", sortOrder = 2, archived = true)).getOrThrow()
        factory.store.set(Paths.options("uid-test"), id, mapOf("favorite" to true, "sortOrder" to 7L, "futureField" to "kvar"), merge = true)

        repository.setArchived(id, archived = false).getOrThrow()

        assertEquals(Option(id, OptionKind.SYMPTOM, "Huvudvärk", favorite = true, sortOrder = 7), all().getValue(id))
        assertEquals("kvar", factory.store.read(Paths.options("uid-test"), id)?.get("futureField"))
    }

    @Test
    fun `namnbyte skriver bara namnet – en gammal kopia återställer inte favorit, arkiv eller plats`() = runTest {
        val snapshot = Option("o1", OptionKind.ACTIVITY, "Promenad", sortOrder = 1)
        options.upsert(snapshot).getOrThrow()
        repository.setFavorite(snapshot, favorite = true).getOrThrow()
        factory.store.set(Paths.options("uid-test"), "o1", mapOf("sortOrder" to 9L, "futureField" to "kvar"), merge = true)

        repository.rename(snapshot, "Kvällspromenad").getOrThrow()

        assertEquals(Option("o1", OptionKind.ACTIVITY, "Kvällspromenad", favorite = true, sortOrder = 9), all().getValue("o1"))
        assertEquals("kvar", factory.store.read(Paths.options("uid-test"), "o1")?.get("futureField"))
    }

    @Test
    fun `namnbyte till ett namn som ett annat aktivt alternativ har nekas, till sitt eget skiftläge går det`() = runTest {
        val option = Option("o1", OptionKind.ACTIVITY, "Promenad")
        options.batch(listOf(option, Option("o2", OptionKind.ACTIVITY, "Yoga", sortOrder = 1))).getOrThrow()

        assertIs<DuplicateOptionName>(repository.rename(option, "YOGA").exceptionOrNull())
        repository.rename(option, "PROMENAD").getOrThrow()

        assertEquals("PROMENAD", all().getValue("o1").name)
    }

    @Test
    fun `namnbyte behåller id, plats och favorit (SET-11)`() = runTest {
        val option = Option("activity-promenad-c78928", OptionKind.ACTIVITY, "Promenad", favorite = true, sortOrder = 5)
        options.upsert(option).getOrThrow()

        repository.rename(option, " Långpromenad ").getOrThrow()

        assertEquals(option.copy(name = "Långpromenad"), all().getValue(option.id))
    }

    @Test
    fun `stjärna och arkivering skriver bara sitt fält`() = runTest {
        val option = Option("o1", OptionKind.EVENT, "Yrsel", sortOrder = 1)
        options.upsert(option).getOrThrow()
        factory.store.set(Paths.options("uid-test"), "o1", mapOf("futureField" to "kvar"), merge = true)

        repository.setFavorite(option.copy(name = "Annat namn"), favorite = true).getOrThrow()
        repository.setArchived("o1", true).getOrThrow()

        assertEquals(option.copy(favorite = true, archived = true), all().getValue("o1"))
        assertEquals("kvar", factory.store.read(Paths.options("uid-test"), "o1")?.get("futureField"))
    }

    @Test
    fun `listan visar bara sin sort, arkiverade med, i ordning`() = runTest {
        options.batch(
            listOf(
                Option("b", OptionKind.SYMPTOM, "Trötthet", sortOrder = 2, archived = true),
                Option("a", OptionKind.SYMPTOM, "Huvudvärk", sortOrder = 1),
                Option("c", OptionKind.ACTIVITY, "Yoga"),
            ),
        ).getOrThrow()

        repository.observe(OptionKind.SYMPTOM).test {
            assertEquals(listOf("a", "b"), awaitItem().map { it.id })
        }
        assertTrue(repository.get("c").getOrThrow()?.kind == OptionKind.ACTIVITY)
    }
}

/** Som [FakeCollectionFactory], men listan ur cachen är [list] – ett fel, eller en ofullständig cache. */
private class CachedOverride(private val fake: FakeCollectionFactory, private val list: Result<List<Nothing>>) : CollectionTable() {
    override fun <T : Identified> create(codec: DocCodec<T>, name: String, path: (uid: String?) -> String): EntityCollection<T> =
        object : EntityCollection<T> by fake.collection(codec, name, path) {
            override suspend fun cached(): Result<List<T>> = list
        }
}
