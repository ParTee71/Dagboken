package se.partee71.dagboken.data.repository

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.data.room.daos.FavoritDao
import se.partee71.dagboken.data.room.daos.MedicinDao
import se.partee71.dagboken.data.room.daos.ReceptDao
import se.partee71.dagboken.data.room.entities.toDomain
import se.partee71.dagboken.data.room.entities.toEntity
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicinerRepository @Inject constructor(
    private val db: AppDatabase,
    private val medicinDao: MedicinDao,
    private val receptDao: ReceptDao,
    private val favoritDao: FavoritDao,
    private val ensureTodayEntries: EnsureTodayEntriesUseCase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ─── Medicin ──────────────────────────────────────────────────────────────
    fun todayFlow(): Flow<List<Medicin>> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return medicinDao.getTodayFlow(today).map { list -> list.map { it.toDomain() } }
    }

    val allMediciner: Flow<List<Medicin>> =
        medicinDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    suspend fun getMedicinById(id: String): Medicin? = medicinDao.getById(id)?.toDomain()

    suspend fun saveMedicin(medicin: Medicin) = medicinDao.upsert(medicin.toEntity())

    suspend fun deleteMedicin(medicin: Medicin) = medicinDao.delete(medicin.toEntity())

    suspend fun toggleTagen(id: String, tagen: Boolean) = medicinDao.updateTagen(id, tagen)

    suspend fun skipMedicin(id: String) = medicinDao.markSkipped(id)

    suspend fun getLastTaken(namn: String): Medicin? = medicinDao.getLastTaken(namn)?.toDomain()

    suspend fun countDailyDoses(datum: String, namn: String): Int =
        medicinDao.countDailyDoses(datum, namn)

    // ─── Recept ───────────────────────────────────────────────────────────────
    val allRecept: Flow<List<Recept>> = receptDao.getAllFlow().map { list ->
        list.map { it.toDomain(::decodeStringList, ::decodeIntList) }
    }

    suspend fun getReceptById(id: String): Recept? =
        receptDao.getById(id)?.toDomain(::decodeStringList, ::decodeIntList)

    suspend fun saveRecept(recept: Recept) =
        receptDao.upsert(recept.toEntity(::encodeStringList, ::encodeIntList))

    suspend fun deleteRecept(recept: Recept) =
        receptDao.delete(recept.toEntity(::encodeStringList, ::encodeIntList))

    suspend fun toggleReceptAktiv(id: String, aktiv: Boolean) =
        receptDao.updateAktiv(id, aktiv)

    // ─── Favorit ──────────────────────────────────────────────────────────────
    val allFavoriter: Flow<List<Favorit>> =
        favoritDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    suspend fun getFavoritById(id: String): Favorit? = favoritDao.getById(id)?.toDomain()

    suspend fun saveFavorit(favorit: Favorit) = favoritDao.upsert(favorit.toEntity())

    suspend fun deleteFavorit(favorit: Favorit) = favoritDao.delete(favorit.toEntity())

    // ─── ensureTodayEntries ───────────────────────────────────────────────────
    /**
     * Creates synthetic Medicin entries for all active Recept that should fire today.
     * Idempotent — stable IDs prevent duplicates.
     */
    suspend fun ensureTodayEntries() {
        val today = LocalDate.now()
        val datum = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        db.withTransaction {
            val recept   = receptDao.getActive().map { it.toDomain(::decodeStringList, ::decodeIntList) }
            val existing = medicinDao.getByDate(datum).map { it.toDomain() }
            val newEntries = ensureTodayEntries.compute(recept, existing, today)
            if (newEntries.isNotEmpty()) {
                medicinDao.upsertAll(newEntries.map { it.toEntity() })
            }
        }
    }

    // ─── Import (migration) ───────────────────────────────────────────────────
    suspend fun importMediciner(entries: List<Medicin>) =
        medicinDao.upsertAll(entries.map { it.toEntity() })

    suspend fun importRecept(entries: List<Recept>) =
        receptDao.upsertAll(entries.map { it.toEntity(::encodeStringList, ::encodeIntList) })

    suspend fun importFavoriter(entries: List<Favorit>) =
        favoritDao.upsertAll(entries.map { it.toEntity() })

    suspend fun isMedicinerEmpty(): Boolean = medicinDao.count() == 0

    // ─── JSON type converters for ReceptEntity ────────────────────────────────
    private fun encodeStringList(list: List<String>): String = json.encodeToString(list)
    private fun encodeIntList(list: List<Int>): String = json.encodeToString(list)
    private fun decodeStringList(raw: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(raw) }
            .onFailure { Log.w("MedicinerRepo", "decodeStringList failed for: $raw", it) }
            .getOrDefault(emptyList())

    private fun decodeIntList(raw: String): List<Int> =
        runCatching { json.decodeFromString<List<Int>>(raw) }
            .onFailure { Log.w("MedicinerRepo", "decodeIntList failed for: $raw", it) }
            .getOrDefault(emptyList())
}
