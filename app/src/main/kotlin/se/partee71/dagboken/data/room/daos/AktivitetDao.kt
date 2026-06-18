package se.partee71.dagboken.data.room.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import se.partee71.dagboken.data.room.entities.AktivitetEntity

@Dao
interface AktivitetDao {

    @Query("SELECT * FROM aktiviteter ORDER BY datum DESC, tid DESC, id DESC")
    fun getAllFlow(): Flow<List<AktivitetEntity>>

    @Query("SELECT * FROM aktiviteter WHERE datum >= :fromDatum ORDER BY datum ASC, tid ASC")
    fun getFromDateFlow(fromDatum: String): Flow<List<AktivitetEntity>>

    @Query("SELECT * FROM aktiviteter WHERE datum = :datum ORDER BY tid ASC")
    suspend fun getByDate(datum: String): List<AktivitetEntity>

    @Query("SELECT * FROM aktiviteter WHERE type = 'screening' AND datum = :datum ORDER BY tid ASC")
    suspend fun getScreeningToday(datum: String): List<AktivitetEntity>

    @Query("SELECT * FROM aktiviteter WHERE type = :type ORDER BY datum DESC, tid DESC LIMIT :limit")
    suspend fun getRecent(type: String, limit: Int = 3): List<AktivitetEntity>

    @Query("SELECT * FROM aktiviteter WHERE type = 'screening' AND datum >= :fromDatum ORDER BY datum ASC, tid ASC")
    fun getScreeningFromDate(fromDatum: String): Flow<List<AktivitetEntity>>

    @Query("SELECT * FROM aktiviteter WHERE id = :id")
    suspend fun getById(id: String): AktivitetEntity?

    @Upsert
    suspend fun upsert(entity: AktivitetEntity)

    @Upsert
    suspend fun upsertAll(entities: List<AktivitetEntity>)

    @Delete
    suspend fun delete(entity: AktivitetEntity)

    @Query("DELETE FROM aktiviteter")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM aktiviteter")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM aktiviteter WHERE type = 'screening' AND datum = :datum")
    suspend fun countScreeningToday(datum: String): Int
}
