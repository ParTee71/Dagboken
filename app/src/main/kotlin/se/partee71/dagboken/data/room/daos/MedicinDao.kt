package se.partee71.dagboken.data.room.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import se.partee71.dagboken.data.room.entities.FavoritEntity
import se.partee71.dagboken.data.room.entities.MedicinEntity
import se.partee71.dagboken.data.room.entities.ReceptEntity

// ─── Medicin ─────────────────────────────────────────────────────────────────
@Dao
interface MedicinDao {

    @Query("SELECT * FROM mediciner WHERE datum = :datum AND skipped = 0 ORDER BY tidpunkt ASC")
    fun getTodayFlow(datum: String): Flow<List<MedicinEntity>>

    @Query("SELECT * FROM mediciner ORDER BY datum DESC, tid DESC")
    fun getAllFlow(): Flow<List<MedicinEntity>>

    @Query("SELECT * FROM mediciner WHERE datum = :datum ORDER BY tid ASC")
    suspend fun getByDate(datum: String): List<MedicinEntity>

    @Query("""
        SELECT * FROM mediciner
        WHERE LOWER(namn) = LOWER(:namn) AND tagen = 1 AND skipped = 0
        ORDER BY timestamp DESC LIMIT 1
    """)
    suspend fun getLastTaken(namn: String): MedicinEntity?

    @Query("""
        SELECT COUNT(*) FROM mediciner
        WHERE datum = :datum AND LOWER(namn) = LOWER(:namn) AND tagen = 1 AND skipped = 0
    """)
    suspend fun countDailyDoses(datum: String, namn: String): Int

    @Query("SELECT * FROM mediciner WHERE id = :id")
    suspend fun getById(id: String): MedicinEntity?

    @Query("SELECT id FROM mediciner WHERE datum = :datum AND receptId IS NOT NULL")
    suspend fun getReceptEntryIdsForDate(datum: String): List<String>

    @Upsert
    suspend fun upsert(entity: MedicinEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MedicinEntity>)

    @Delete
    suspend fun delete(entity: MedicinEntity)

    @Query("UPDATE mediciner SET tagen = :tagen WHERE id = :id")
    suspend fun updateTagen(id: String, tagen: Boolean)

    @Query("UPDATE mediciner SET skipped = 1 WHERE id = :id")
    suspend fun markSkipped(id: String)

    @Query("DELETE FROM mediciner")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM mediciner")
    suspend fun count(): Int
}

// ─── Recept ───────────────────────────────────────────────────────────────────
@Dao
interface ReceptDao {

    @Query("SELECT * FROM recept ORDER BY skapad DESC")
    fun getAllFlow(): Flow<List<ReceptEntity>>

    @Query("SELECT * FROM recept WHERE aktiv = 1")
    suspend fun getActive(): List<ReceptEntity>

    @Query("SELECT * FROM recept WHERE id = :id")
    suspend fun getById(id: String): ReceptEntity?

    @Upsert
    suspend fun upsert(entity: ReceptEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ReceptEntity>)

    @Delete
    suspend fun delete(entity: ReceptEntity)

    @Query("UPDATE recept SET aktiv = :aktiv WHERE id = :id")
    suspend fun updateAktiv(id: String, aktiv: Boolean)

    @Query("DELETE FROM recept")
    suspend fun deleteAll()
}

// ─── Favorit ──────────────────────────────────────────────────────────────────
@Dao
interface FavoritDao {

    @Query("SELECT * FROM favoriter ORDER BY namn ASC")
    fun getAllFlow(): Flow<List<FavoritEntity>>

    @Query("SELECT * FROM favoriter")
    suspend fun getAll(): List<FavoritEntity>

    @Query("SELECT * FROM favoriter WHERE id = :id")
    suspend fun getById(id: String): FavoritEntity?

    @Upsert
    suspend fun upsert(entity: FavoritEntity)

    @Upsert
    suspend fun upsertAll(entities: List<FavoritEntity>)

    @Delete
    suspend fun delete(entity: FavoritEntity)

    @Query("DELETE FROM favoriter")
    suspend fun deleteAll()
}
