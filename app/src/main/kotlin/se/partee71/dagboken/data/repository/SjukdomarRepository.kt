package se.partee71.dagboken.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.partee71.dagboken.data.room.daos.SjukdomsEpisodDao
import se.partee71.dagboken.data.room.daos.SjukdomsIncheckningDao
import se.partee71.dagboken.data.room.entities.SjukdomsEpisodEntity
import se.partee71.dagboken.data.room.entities.SjukdomsIncheckningEntity
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import se.partee71.dagboken.domain.usecase.SymptomUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SjukdomarRepository @Inject constructor(
    private val episodDao: SjukdomsEpisodDao,
    private val incheckningDao: SjukdomsIncheckningDao,
    private val noteRepo: NoteRepository,
) {
    val all: Flow<List<SjukdomsEpisod>> = episodDao.allFlow().map { list ->
        list.map { it.toDomain() }
    }

    val pagaende: Flow<SjukdomsEpisod?> = episodDao.pagaendeFlow().map { it?.toDomain() }

    // Room flyttar själv suspend-DAO-anrop från huvudtråden, så repositoryt lindar dem
    // inte i withContext (samma som övriga repositories i appen).
    suspend fun getEpisodWithIncheckningar(id: String): SjukdomsEpisod? =
        episodDao.getById(id)?.toDomain()

    suspend fun saveEpisod(episod: SjukdomsEpisod) = episodDao.save(episod.toEntity())

    /**
     * Raderar episoden, dess incheckningar (FK ON DELETE CASCADE) och alla tillhörande
     * anteckningar. Incheckningarnas anteckningar måste läsas ut före raderingen —
     * efteråt finns inga rader kvar att härleda id:n från (DAT-4).
     */
    suspend fun deleteEpisod(episod: SjukdomsEpisod) {
        val incheckningIds = incheckningDao.idsForEpisod(episod.id)
        episodDao.delete(episod.toEntity())
        incheckningIds.forEach { noteRepo.delete(NoteTarget.SJUKDOM_INCHECKNING, it) }
        noteRepo.delete(NoteTarget.SJUKDOM_EPISOD, episod.id)
    }

    fun incheckningarForEpisod(episodId: String): Flow<List<SjukdomsIncheckning>> =
        incheckningDao.allForEpisodFlow(episodId).map { list -> list.map { it.toDomain() } }

    val allIncheckningar: Flow<List<SjukdomsIncheckning>> =
        incheckningDao.allFlow().map { list -> list.map { it.toDomain() } }

    suspend fun saveIncheckning(incheckning: SjukdomsIncheckning) =
        incheckningDao.save(incheckning.toEntity())

    suspend fun deleteIncheckning(incheckning: SjukdomsIncheckning) {
        incheckningDao.delete(incheckning.toEntity())
        noteRepo.delete(NoteTarget.SJUKDOM_INCHECKNING, incheckning.id)
    }

    /** Byter namn på ett symptom i incheckningarnas kodade symptomsträng (HAN-9). */
    suspend fun renameSymptom(old: String, new: String) {
        val updated = incheckningDao.withSymptomContaining(old).mapNotNull { entity ->
            val scores = SymptomUtils.decode(entity.symptom)
            if (old !in scores) return@mapNotNull null
            entity.copy(symptom = SymptomUtils.encode(scores.mapKeys { (n, _) -> if (n == old) new else n }))
        }
        if (updated.isNotEmpty()) incheckningDao.saveAll(updated)
    }

    // Batch-upsert — en sats i stället för en per rad.
    suspend fun importEpisoder(episoder: List<SjukdomsEpisod>) =
        episodDao.saveAll(episoder.map { it.toEntity() })

    suspend fun importIncheckningar(incheckningar: List<SjukdomsIncheckning>) =
        incheckningDao.saveAll(incheckningar.map { it.toEntity() })

    private fun SjukdomsEpisodEntity.toDomain() = SjukdomsEpisod(
        id         = id,
        typ        = typ,
        startDatum = startDatum,
        slutDatum  = slutDatum,
        timestamp  = timestamp,
    )

    private fun SjukdomsEpisod.toEntity() = SjukdomsEpisodEntity(
        id         = id,
        typ        = typ,
        startDatum = startDatum,
        slutDatum  = slutDatum,
        timestamp  = timestamp,
    )

    private fun SjukdomsIncheckningEntity.toDomain() = SjukdomsIncheckning(
        id             = id,
        episodId       = episodId,
        datum          = datum,
        tid            = tid,
        svarighetsgrad = svarighetsgrad,
        symptom        = symptom,
        somatiska      = somatiska,
        timestamp      = timestamp,
    )

    private fun SjukdomsIncheckning.toEntity() = SjukdomsIncheckningEntity(
        id             = id,
        episodId       = episodId,
        datum          = datum,
        tid            = tid,
        svarighetsgrad = svarighetsgrad,
        symptom        = symptom,
        somatiska      = somatiska,
        timestamp      = timestamp,
    )
}
