package se.partee71.dagboken.data.repository

import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.schema.CheckinCodec
import se.partee71.dagboken.core.schema.IllnessEpisodeCodec
import se.partee71.dagboken.data.common.CollectionFactory

/**
 * Sjukdomsepisoderna i samlingen `illnessEpisodes` och deras incheckningar i undersamlingen `checkins`
 * (SJ-serien, HEM-12, HIST-9). Läsning för Idag och Dagboken, en ny episod med sin första incheckning och
 * incheckningarnas formulär (SJ-1, SJ-2, SJ-11); radering av en episod med sina incheckningar (SJ-9) kommer med
 * sjukdomsdetaljen (#240).
 */
interface IllnessRepository {
    /** Alla episoder ur cachen och sedan servern (offline först) – en användare har få. */
    fun observeEpisodes(): Flow<List<IllnessEpisode>>

    /** Incheckningarna under episoden [episodeId], offline först. */
    fun observeCheckins(episodeId: String): Flow<List<Checkin>>

    /** Episoden [id] (`null` = finns inte), offline först – incheckningens formulär skriver bara under en som finns. */
    suspend fun getEpisode(id: String): Result<IllnessEpisode?>

    /**
     * Sparar episoden (SJ-12): [loaded] `null` = ny, skrivs som den är (med `createdAt`); en ändrad skriver bara de
     * ändrade fälten (`updateChanged`), som dagbokens andra poster ([EntryStore.save]).
     */
    suspend fun saveEpisode(loaded: IllnessEpisode?, edited: IllnessEpisode): Result<Unit>

    /**
     * SJ-1, SJ-2 (som 3.x): en ny episod och dess första incheckning – episoden först, så att incheckningen skrivs
     * under en episod som finns (rules: `existsAfter`); båda med sina id:n och `createdAt` från [newEpisode] och
     * [newCheckin]. Offline först: skrivningarna köas i ordning.
     */
    suspend fun startEpisode(episode: IllnessEpisode, first: Checkin): Result<Unit>

    /** Formulärets läsning, sparning och radering av incheckningarna under episoden [episodeId] (SJ-2, SJ-11, HIST-5). */
    fun checkins(episodeId: String): EntryStore<Checkin>

    /** Tar bort incheckningen [id] under episoden [episodeId] permanent med sin anteckning (HIST-5, DAT-7). Offline först. */
    suspend fun deleteCheckin(episodeId: String, id: String): Result<Unit>

    /** En ny, osparad episod som börjar [start] (pågående): nytt slumpat id och `createdAt` = nu, satta **en gång** här. */
    fun newEpisode(start: LocalDate): IllnessEpisode

    /**
     * En ny, osparad incheckning på [date] kl. [time] med svårighetsgraden [DEFAULT_SEVERITY]: nytt slumpat id och
     * `createdAt` = nu, satta **en gång** här. [episodeId] är episoden den skrivs under.
     */
    fun newCheckin(episodeId: String, date: LocalDate, time: LocalTime): Checkin

    companion object {
        /** En ny incheckning svårighetsgrad – mitt på skalan 0–10, som 3.x (och en ny händelse). */
        const val DEFAULT_SEVERITY = EventRepository.DEFAULT_SEVERITY
    }
}

/** Tunn fasad över `illnessEpisodes` och `checkins` (skill firestore-data-layer). */
class DefaultIllnessRepository @Inject constructor(private val collections: CollectionFactory, private val clock: Clock) : IllnessRepository {
    private val episodes = collections.illnessEpisodes()

    private val episodeEntries = StoredEntries(episodes, IllnessEpisodeCodec, { it.createdAt }, { episode, id -> episode.copy(id = id) })

    override fun observeEpisodes(): Flow<List<IllnessEpisode>> = episodes.observe()

    override fun observeCheckins(episodeId: String): Flow<List<Checkin>> = collections.checkins(episodeId).observe()

    override suspend fun getEpisode(id: String): Result<IllnessEpisode?> = episodes.get(id)

    override suspend fun saveEpisode(loaded: IllnessEpisode?, edited: IllnessEpisode): Result<Unit> = episodeEntries.save(loaded, edited)

    override suspend fun startEpisode(episode: IllnessEpisode, first: Checkin): Result<Unit> {
        if (first.createdAt == null) return Result.failure(IllegalArgumentException("En ny incheckning skapas med newCheckin()"))
        return saveEpisode(null, episode).fold({ checkins(episode.id).save(null, first) }, { Result.failure(it) })
    }

    override fun checkins(episodeId: String): EntryStore<Checkin> = StoredEntries(collections.checkins(episodeId), CheckinCodec)

    override suspend fun deleteCheckin(episodeId: String, id: String): Result<Unit> = checkins(episodeId).delete(id)

    override fun newEpisode(start: LocalDate): IllnessEpisode = IllnessEpisode(episodes.newId(), start = start, createdAt = clock.now())

    override fun newCheckin(episodeId: String, date: LocalDate, time: LocalTime): Checkin =
        Checkin(collections.checkins(episodeId).newId(), date, time, severity = IllnessRepository.DEFAULT_SEVERITY, createdAt = clock.now())
}
