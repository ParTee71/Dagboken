package se.partee71.dagboken.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.partee71.dagboken.data.room.daos.NoteDao
import se.partee71.dagboken.data.room.entities.NoteEntity
import se.partee71.dagboken.domain.model.NoteTarget
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(private val dao: NoteDao) {
    fun observe(target: NoteTarget, entityId: String): Flow<String> =
        dao.observe(target.name, entityId).map { it ?: "" }

    /** Live map of entityId → note text for every note under [target]. */
    fun observeMap(target: NoteTarget): Flow<Map<String, String>> =
        dao.observeAllForTarget(target.name).map { notes -> notes.associate { it.entityId to it.text } }

    suspend fun save(target: NoteTarget, entityId: String, text: String) {
        if (text.isBlank()) {
            dao.delete(target.name, entityId)
        } else {
            dao.upsert(NoteEntity(target.name, entityId, text))
        }
    }

    suspend fun delete(target: NoteTarget, entityId: String) {
        dao.delete(target.name, entityId)
    }

    suspend fun getAll(): List<NoteEntity> = dao.getAll()

    suspend fun importAll(notes: List<NoteEntity>) = dao.upsertAll(notes)
}
