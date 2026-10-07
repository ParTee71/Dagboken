package se.partee71.dagboken.data.repository

import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.engine.endDateError
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.schema.CheckinCodec
import se.partee71.dagboken.core.schema.IllnessEpisodeCodec
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.DataError

/**
 * Sjukdomsepisoderna i samlingen `illnessEpisodes` och deras incheckningar i undersamlingen `checkins`
 * (SJ-serien, HEM-12, HIST-9). Läsning för Idag, Dagboken och sjukdomsdetaljen, en ny episod med sin första
 * incheckning, incheckningarnas formulär (SJ-1, SJ-2, SJ-11), att avsluta en episod (SJ-4) och radering av en episod
 * med sina incheckningar (SJ-9, kaskad).
 */
interface IllnessRepository {
    /** Alla episoder ur cachen och sedan servern (offline först) – en användare har få. */
    fun observeEpisodes(): Flow<List<IllnessEpisode>>

    /** Incheckningarna under episoden [episodeId], offline först. */
    fun observeCheckins(episodeId: String): Flow<List<Checkin>>

    /**
     * Sjukdomsdetaljen (#240): episoden [id] med sina incheckningar, offline först – ny utsändning när någon av dem
     * ändras. `null` när episoden inte finns (raderad, också på en annan enhet). Sammanfattningen räknas i `:core`
     * (`illnessSummary`).
     */
    fun observeEpisode(id: String): Flow<EpisodeWithCheckins?>

    /** Bara episoden [id], utan incheckningarna (incheckningens formulär), offline först; `null` = finns inte. */
    fun observeEpisodeOnly(id: String): Flow<IllnessEpisode?>

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

    /**
     * SJ-4: avslutar [loaded] med slutdatumet [end] – bara fältet `end` skrivs (`updateChanged`), så att andra fält och
     * okända fält står kvar och en episod som raderats under tiden inte återuppstår. Ingen incheckning skapas (SJ-12).
     * [end] ska ligga i start…[today] (`endDateError` i `:core`); annars [IllegalArgumentException] och ingenting
     * skrivs. Offline först.
     */
    suspend fun finishEpisode(loaded: IllnessEpisode, end: LocalDate, today: LocalDate): Result<Unit>

    /**
     * SJ-9, DAT-7: tar bort episoden [id] permanent med alla sina incheckningar och deras anteckningar (kaskad).
     * Incheckningarna raderas **före** episoden – i batchar om högst 500 (`EntityCollection.batch`) – och episoden
     * först när servern bekräftat att listan är tom, så att en avbruten radering aldrig lämnar incheckningar utan
     * episod: det som blir kvar är episoden med några av sina incheckningar, och ett nytt försök tar resten. Listan är
     * **serverns** (efter appens egna väntande skrivningar), aldrig cachens, och läses om efter varje batch tills den
     * är tom (högst [MAX_CASCADE_ROUNDS] varv, annars [DataError.Unknown] och episoden står kvar). Det krymper
     * fönstret för en incheckning från en annan enhet men stänger det inte – det kräver en transaktion över
     * undersamlingen: en incheckning som skrivs i sista stund, mellan den sista tomma läsningen och raderingen av
     * episoden, kan bli kvar utan episod (rules `existsAfter` hindrar bara dem som kommer efter). Den bevaras och syns
     * i exporten och kan raderas. Kräver nät: offline [DataError.Offline] och ingenting raderas.
     */
    suspend fun deleteEpisode(id: String): Result<Unit>

    /**
     * Antalet incheckningar under episoden [episodeId] för radera-bekräftelsens text (SJ-9) – **serverns** lista, samma
     * som [deleteEpisode] raderar. Kräver nät som raderingen: offline [DataError.Offline].
     */
    suspend fun checkinCount(episodeId: String): Result<Int>

    /**
     * Formulärets läsning, sparning och radering av incheckningarna under episoden [episodeId] (SJ-2, SJ-11, HIST-5).
     * En ny incheckning skrivs bara när episoden finns (som rules `existsAfter`): saknas den blir det
     * [DataError.NotFound] och ingenting skrivs. En ändrad behåller `id` och `createdAt` och skriver bara ändrade
     * fält; summan av symptomen (`somatic`) lagras inte utan räknas ur symptomen (SJ-11, DAT-6).
     */
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

        /** [deleteEpisode]: högst så många varv "läs serverns incheckningar → radera dem" innan episoden raderas. */
        const val MAX_CASCADE_ROUNDS = 5
    }
}

/** Sjukdomsdetaljens underlag ([IllnessRepository.observeEpisode]): [episode] och dess [checkins]. */
data class EpisodeWithCheckins(val episode: IllnessEpisode, val checkins: List<Checkin>)

/** Tunn fasad över `illnessEpisodes` och `checkins` (skill firestore-data-layer). */
class DefaultIllnessRepository @Inject constructor(private val collections: CollectionFactory, private val clock: Clock) : IllnessRepository {
    private val episodes = collections.illnessEpisodes()

    private val episodeEntries = StoredEntries(episodes, IllnessEpisodeCodec, { it.createdAt }, { episode, id -> episode.copy(id = id) })

    override fun observeEpisodes(): Flow<List<IllnessEpisode>> = episodes.observe()

    override fun observeCheckins(episodeId: String): Flow<List<Checkin>> = collections.checkins(episodeId).observe()

    override fun observeEpisode(id: String): Flow<EpisodeWithCheckins?> =
        combine(episodes.observe(id), collections.checkins(id).observe()) { episode, checkins -> episode?.let { EpisodeWithCheckins(it, checkins) } }

    override fun observeEpisodeOnly(id: String): Flow<IllnessEpisode?> = episodes.observe(id)

    override suspend fun getEpisode(id: String): Result<IllnessEpisode?> = episodes.get(id)

    override suspend fun saveEpisode(loaded: IllnessEpisode?, edited: IllnessEpisode): Result<Unit> = episodeEntries.save(loaded, edited)

    override suspend fun startEpisode(episode: IllnessEpisode, first: Checkin): Result<Unit> {
        if (first.createdAt == null) return Result.failure(IllegalArgumentException("En ny incheckning skapas med newCheckin()"))
        return saveEpisode(null, episode).fold({ checkins(episode.id).save(null, first) }, { Result.failure(it) })
    }

    override suspend fun finishEpisode(loaded: IllnessEpisode, end: LocalDate, today: LocalDate): Result<Unit> {
        endDateError(loaded.start, end, today)?.let { return Result.failure(IllegalArgumentException("Slutdatumet godtas inte: $it")) }
        return saveEpisode(loaded, loaded.copy(end = end))
    }

    override suspend fun deleteEpisode(id: String): Result<Unit> {
        val checkins = collections.checkins(id)
        // Appens egna väntande skrivningar (en incheckning sparad nyss) ska finnas på servern innan listan läses.
        checkins.awaitWrites().onFailure { return Result.failure(it) }
        repeat(IllnessRepository.MAX_CASCADE_ROUNDS) {
            val stored = checkins.confirmed().getOrElse { return Result.failure(it) }
            // Episoden raderas först när servern visar en tom lista – ett avbrott före det lämnar episoden kvar.
            if (stored.isEmpty()) return episodes.delete(id)
            checkins.batch(emptyList(), deletes = stored.map { it.id }).onFailure { return Result.failure(it) }
            checkins.awaitWrites().onFailure { return Result.failure(it) }
        }
        return Result.failure(DataError.Unknown)
    }

    override suspend fun checkinCount(episodeId: String): Result<Int> = collections.checkins(episodeId).confirmed().map { it.size }

    override fun checkins(episodeId: String): EntryStore<Checkin> {
        val stored = StoredEntries(collections.checkins(episodeId), CheckinCodec)
        return object : EntryStore<Checkin> by stored {
            override suspend fun save(loaded: Checkin?, edited: Checkin): Result<Unit> {
                if (loaded == null) {
                    val episode = episodes.cached(episodeId).getOrElse { return Result.failure(it) }
                    if (episode == null) return Result.failure(DataError.NotFound)
                }
                return stored.save(loaded, edited)
            }
        }
    }

    override suspend fun deleteCheckin(episodeId: String, id: String): Result<Unit> = checkins(episodeId).delete(id)

    override fun newEpisode(start: LocalDate): IllnessEpisode = IllnessEpisode(episodes.newId(), start = start, createdAt = clock.now())

    override fun newCheckin(episodeId: String, date: LocalDate, time: LocalTime): Checkin =
        Checkin(collections.checkins(episodeId).newId(), date, time, severity = IllnessRepository.DEFAULT_SEVERITY, createdAt = clock.now())
}
