package se.partee71.dagboken.data.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import se.partee71.dagboken.data.room.entities.NoteEntity

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE target_type = :type AND target_id = :id LIMIT 1")
    fun observe(type: String, id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE target_type = :type AND target_id = :id LIMIT 1")
    suspend fun getByKey(type: String, id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE target_type = :type AND target_id = :id")
    suspend fun delete(type: String, id: String)

    @Query("SELECT * FROM notes WHERE target_type = :type")
    fun observeAll(type: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
