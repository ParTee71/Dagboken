package se.partee71.dagboken.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import se.partee71.dagboken.data.room.daos.SjukdomsEpisodDao
import se.partee71.dagboken.data.room.daos.SjukdomsIncheckningDao
import se.partee71.dagboken.data.room.entities.SjukdomsEpisodEntity
import se.partee71.dagboken.data.room.entities.SjukdomsIncheckningEntity
import se.partee71.dagboken.di.IoDispatcher
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SjukdomarRepository @Inject constructor(
    private val episodDao: SjukdomsEpisodDao,
    private val incheckningDao: SjukdomsIncheckningDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    val all: Flow<List<SjukdomsEpisod>> = episodDao.allFlow().map { list ->
        list.map { it.toDomain() }
    }

    val pagaende: Flow<SjukdomsEpisod?> = episodDao.pagaendeFlow().map { it?.toDomain() }

    suspend fun getEpisodWithIncheckningar(id: String): SjukdomsEpisod? = withContext(ioDispatcher) {
        val entity = episodDao.getById(id) ?: return@withContext null
        entity.toDomain()
    }

    suspend fun saveEpisod(episod: SjukdomsEpisod) = withContext(ioDispatcher) {
        episodDao.save(episod.toEntity())
    }

    suspend fun deleteEpisod(episod: SjukdomsEpisod) = withContext(ioDispatcher) {
        episodDao.delete(episod.toEntity())
    }

    fun incheckningarForEpisod(episodId: String): Flow<List<SjukdomsIncheckning>> =
        incheckningDao.allForEpisodFlow(episodId).map { list -> list.map { it.toDomain() } }

    val allIncheckningar: Flow<List<SjukdomsIncheckning>> =
        incheckningDao.allFlow().map { list -> list.map { it.toDomain() } }

    suspend fun saveIncheckning(incheckning: SjukdomsIncheckning) = withContext(ioDispatcher) {
        incheckningDao.save(incheckning.toEntity())
    }

    suspend fun deleteIncheckning(incheckning: SjukdomsIncheckning) = withContext(ioDispatcher) {
        incheckningDao.delete(incheckning.toEntity())
    }

    suspend fun importEpisoder(episoder: List<SjukdomsEpisod>) = withContext(ioDispatcher) {
        episoder.forEach { episodDao.save(it.toEntity()) }
    }

    suspend fun importIncheckningar(incheckningar: List<SjukdomsIncheckning>) = withContext(ioDispatcher) {
        incheckningar.forEach { incheckningDao.save(it.toEntity()) }
    }

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
