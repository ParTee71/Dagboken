package se.partee71.dagboken.data.room.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import se.partee71.dagboken.data.room.entities.SjukdomsEpisodEntity

@Dao
interface SjukdomsEpisodDao {

    @Query("SELECT * FROM sjukdomsepisoder ORDER BY timestamp DESC")
    fun allFlow(): Flow<List<SjukdomsEpisodEntity>>

    @Query("SELECT * FROM sjukdomsepisoder WHERE id = :id")
    suspend fun getById(id: String): SjukdomsEpisodEntity?

    @Query("SELECT * FROM sjukdomsepisoder WHERE slut_datum = '' ORDER BY timestamp DESC LIMIT 1")
    fun pagaendeFlow(): Flow<SjukdomsEpisodEntity?>

    @Query("SELECT * FROM sjukdomsepisoder")
    suspend fun getAll(): List<SjukdomsEpisodEntity>

    @Upsert
    suspend fun save(episod: SjukdomsEpisodEntity)

    @Delete
    suspend fun delete(episod: SjukdomsEpisodEntity)

    @Query("SELECT COUNT(*) FROM sjukdomsepisoder")
    suspend fun count(): Int
}
