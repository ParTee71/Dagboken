package se.partee71.dagboken.data.room.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import se.partee71.dagboken.data.room.entities.SjukdomsIncheckningEntity

@Dao
interface SjukdomsIncheckningDao {

    @Query("SELECT * FROM sjukdoms_incheckningar WHERE episod_id = :episodId ORDER BY datum DESC, tid DESC")
    fun allForEpisodFlow(episodId: String): Flow<List<SjukdomsIncheckningEntity>>

    @Query("SELECT * FROM sjukdoms_incheckningar WHERE id = :id")
    suspend fun getById(id: String): SjukdomsIncheckningEntity?

    @Upsert
    suspend fun save(incheckning: SjukdomsIncheckningEntity)

    @Delete
    suspend fun delete(incheckning: SjukdomsIncheckningEntity)

    @Query("SELECT COUNT(*) FROM sjukdoms_incheckningar")
    suspend fun count(): Int
}
