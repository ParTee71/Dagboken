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

    fun observe(target: NoteTarget, id: String): Flow<String> =
        dao.observe(target.name, id).map { it?.text ?: "" }

    suspend fun save(target: NoteTarget, id: String, text: String) {
        if (text.isBlank()) dao.delete(target.name, id)
        else dao.upsert(NoteEntity(targetType = target.name, targetId = id, text = text.trim()))
    }

    fun observeAll(target: NoteTarget): Flow<List<NoteEntity>> =
        dao.observeAll(target.name)

    fun observeAll(): Flow<List<NoteEntity>> =
        dao.observeAll()

    suspend fun delete(target: NoteTarget, id: String) {
        dao.delete(target.name, id)
    }

    suspend fun importAll(notes: List<NoteEntity>) {
        notes.forEach { dao.upsert(it) }
    }
}
