package se.partee71.dagboken.data.room.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import se.partee71.dagboken.data.room.entities.NoteEntity

@Dao
interface NoteDao {
    @Query("SELECT text FROM notes WHERE target = :target AND entityId = :entityId")
    fun observe(target: String, entityId: String): Flow<String?>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE target = :target AND entityId = :entityId")
    suspend fun delete(target: String, entityId: String)

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int
}
