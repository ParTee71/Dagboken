package se.partee71.dagboken.data.room.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import se.partee71.dagboken.data.room.entities.HandelseEntity

@Dao
interface HandelseDao {

    @Query("SELECT * FROM health_events ORDER BY datum DESC, tid DESC, id DESC")
    fun getAllFlow(): Flow<List<HandelseEntity>>

    @Query("SELECT * FROM health_events WHERE datum >= :from ORDER BY datum DESC, tid DESC")
    fun getFromDateFlow(from: String): Flow<List<HandelseEntity>>

    @Query("SELECT * FROM health_events WHERE id = :id")
    suspend fun getById(id: String): HandelseEntity?

    @Upsert
    suspend fun upsert(entity: HandelseEntity)

    @Upsert
    suspend fun upsertAll(entities: List<HandelseEntity>)

    @Delete
    suspend fun delete(entity: HandelseEntity)

    @Query("DELETE FROM health_events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM health_events")
    suspend fun count(): Int
}
