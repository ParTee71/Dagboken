package se.partee71.dagboken.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.data.common.CollectionFactory

/**
 * Sjukdomsepisoderna i samlingen `illnessEpisodes` och deras incheckningar i undersamlingen `checkins`
 * (SJ-serien, HEM-12, HIST-9). Läsning för Idag och Dagboken och radering av en incheckning; övriga skrivningar –
 * också radering av en episod med sina incheckningar (SJ-9) – kommer med sjukdomsdetaljen och incheckningen (#240).
 */
interface IllnessRepository {
    /** Alla episoder ur cachen och sedan servern (offline först) – en användare har få. */
    fun observeEpisodes(): Flow<List<IllnessEpisode>>

    /** Incheckningarna under episoden [episodeId], offline först. */
    fun observeCheckins(episodeId: String): Flow<List<Checkin>>

    /** Tar bort incheckningen [id] under episoden [episodeId] permanent med sin anteckning (HIST-5, DAT-7). Offline först. */
    suspend fun deleteCheckin(episodeId: String, id: String): Result<Unit>
}

/** Tunn fasad över `illnessEpisodes` och `checkins` (skill firestore-data-layer). */
class DefaultIllnessRepository @Inject constructor(private val collections: CollectionFactory) : IllnessRepository {
    private val episodes = collections.illnessEpisodes()

    override fun observeEpisodes(): Flow<List<IllnessEpisode>> = episodes.observe()

    override fun observeCheckins(episodeId: String): Flow<List<Checkin>> = collections.checkins(episodeId).observe()

    override suspend fun deleteCheckin(episodeId: String, id: String): Result<Unit> = collections.checkins(episodeId).delete(id)
}
