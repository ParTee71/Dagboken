package se.partee71.dagboken.data

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.daos.NoteDao
import se.partee71.dagboken.data.room.entities.NoteEntity
import se.partee71.dagboken.domain.model.NoteTarget

class NoteRepositoryUnitTest {

    private inner class FakeNoteDao : NoteDao {
        private val store = mutableMapOf<Pair<String, String>, String>()
        private val _flow = MutableStateFlow<Map<Pair<String, String>, String>>(emptyMap())

        override fun observe(target: String, entityId: String): Flow<String?> =
            _flow.map { it[target to entityId] }

        override fun observeAllForTarget(target: String): Flow<List<NoteEntity>> =
            _flow.map { m -> m.filterKeys { it.first == target }.map { (k, v) -> NoteEntity(k.first, k.second, v) } }

        override suspend fun getAll(): List<NoteEntity> =
            store.map { (key, text) -> NoteEntity(key.first, key.second, text) }

        override suspend fun upsert(note: NoteEntity) {
            store[note.target to note.entityId] = note.text
            _flow.value = store.toMap()
        }

        override suspend fun upsertAll(notes: List<NoteEntity>) {
            notes.forEach { store[it.target to it.entityId] = it.text }
            _flow.value = store.toMap()
        }

        override suspend fun delete(target: String, entityId: String) {
            store.remove(target to entityId)
            _flow.value = store.toMap()
        }

        override suspend fun count(): Int = store.size
    }

    private fun repo() = NoteRepository(FakeNoteDao())

    @Test fun `observe maps null to empty string`() = runTest {
        repo().observe(NoteTarget.ACTIVITY, "a1").test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `save blank text deletes the entry`() = runTest {
        val r = repo()
        r.save(NoteTarget.ACTIVITY, "a1", "hello")
        r.save(NoteTarget.ACTIVITY, "a1", "  ")
        r.observe(NoteTarget.ACTIVITY, "a1").test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `save stores text as provided`() = runTest {
        val r = repo()
        r.save(NoteTarget.ACTIVITY, "a1", "hello world")
        r.observe(NoteTarget.ACTIVITY, "a1").test {
            assertEquals("hello world", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `save then delete returns empty string`() = runTest {
        val r = repo()
        r.save(NoteTarget.MEDICATION, "m1", "anteckning")
        r.delete(NoteTarget.MEDICATION, "m1")
        r.observe(NoteTarget.MEDICATION, "m1").test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `observeMap returns entityId to text for the given target only`() = runTest {
        val r = repo()
        r.save(NoteTarget.RECEPT, "r1", "Kväll bäst")
        r.save(NoteTarget.RECEPT, "r2", "Med mat")
        r.save(NoteTarget.FAVORIT, "f1", "Ignoreras")

        r.observeMap(NoteTarget.RECEPT).test {
            val map = awaitItem()
            assertEquals(2, map.size)
            assertEquals("Kväll bäst", map["r1"])
            assertEquals("Med mat", map["r2"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `observeMap is empty when no notes exist for the target`() = runTest {
        repo().observeMap(NoteTarget.EVENT).test {
            assertEquals(emptyMap<String, String>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `importAll then getAll round-trips all notes`() = runTest {
        val r = repo()
        r.importAll(listOf(
            NoteEntity("ACTIVITY", "a1", "första"),
            NoteEntity("MEDICATION", "m1", "andra"),
        ))
        val all = r.getAll()
        assertEquals(2, all.size)
        assertEquals("första", all.find { it.entityId == "a1" }!!.text)
        assertEquals("andra", all.find { it.entityId == "m1" }!!.text)
    }
}
