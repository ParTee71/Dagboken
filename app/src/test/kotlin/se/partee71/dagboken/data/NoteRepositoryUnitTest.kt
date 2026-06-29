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
        private val store = mutableMapOf<Pair<String, String>, NoteEntity>()
        private val _flow = MutableStateFlow<Map<Pair<String, String>, NoteEntity>>(emptyMap())

        override fun observe(type: String, id: String): Flow<NoteEntity?> =
            _flow.map { it[type to id] }

        override suspend fun getByKey(type: String, id: String): NoteEntity? =
            store[type to id]

        override suspend fun upsert(note: NoteEntity) {
            val key = note.targetType to note.targetId
            store[key] = note
            _flow.value = store.toMap()
        }

        override suspend fun delete(type: String, id: String) {
            store.remove(type to id)
            _flow.value = store.toMap()
        }

        override fun observeAll(type: String): Flow<List<NoteEntity>> =
            _flow.map { it.values.filter { e -> e.targetType == type } }

        override fun observeAll(): Flow<List<NoteEntity>> =
            _flow.map { it.values.toList() }

        override suspend fun count(): Int = store.size

        override suspend fun deleteAll() {
            store.clear()
            _flow.value = emptyMap()
        }
    }

    private fun repo() = NoteRepository(FakeNoteDao())

    @Test fun `observe maps null entity to empty string`() = runTest {
        repo().observe(NoteTarget.DAY, "2026-06-24").test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `save blank text calls delete`() = runTest {
        val r = repo()
        r.save(NoteTarget.EVENT, "e1", "hello")
        r.save(NoteTarget.EVENT, "e1", "  ")
        r.observe(NoteTarget.EVENT, "e1").test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `save trims whitespace`() = runTest {
        val r = repo()
        r.save(NoteTarget.ACTIVITY, "a1", "  hello world  ")
        r.observe(NoteTarget.ACTIVITY, "a1").test {
            assertEquals("hello world", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
