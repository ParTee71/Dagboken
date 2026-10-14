package se.partee71.dagboken.data.common

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.time.readParts
import se.partee71.dagboken.di.ApplicationScope

/**
 * Historiklägets täckning (NFR-24): per konto, samling och datumfält den äldsta dag från vilken enhetens lokala cache
 * har **allt** – för att den hämtats komplett från servern – och dagen det hämtades. Bara datum, i `dagboken_device`
 * ([DayMarks]); ingen hälsodata, aldrig i Firestore eller exporten.
 *
 * Firestores cache kan vara ofullständig för dagar som aldrig lästs från servern (bara dokument som andra frågor råkat
 * cacha) – därför hämtas det som inte täcks från servern **en gång** och antecknas ([ensure]). Appen körs på **en enhet**:
 * allt den själv skriver hamnar i cachen, så det täckta räknas som komplett. Täckningen hämtas ändå om efter
 * [REFRESH_DAYS] dagar – en billig omläsning som rättar det enda som annars kan saknas: dokument som Firestore själv rensat
 * ur cachen (LRU-rensning först över cachegränsen på 100 MB, långt över dagbokens ~2 000 dokument) och skrivningar förbi
 * appen (`tools/db import`/`migrate`, bara på begäran). Utloggningen glömmer all täckning ([forgetAll]) – en anteckning får
 * aldrig överleva cachen den beskriver.
 */
@Singleton
class HistoryCoverage @Inject constructor(
    private val marks: DayMarks,
    private val clock: Clock,
    /** Enhetens tidszon, läst vid varje användning. */
    private val zone: Provider<TimeZone>,
    /** Hämtningen fortsätter här när den som väntar avbryts, så att en debiterad läsning alltid antecknas. */
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val locks = mutableMapOf<String, Mutex>()

    /** Anteckningar och [forgetAll] en i taget; [generation] räknas upp av varje [forgetAll]. */
    private val writes = Mutex()

    @Volatile private var generation = 0L

    /**
     * Ser till att cachen har allt från [from] för samlingen [key] (sökvägen och fältet, med uid): ingenting när det
     * redan täcks; finns täckningen och är färsk hämtas bara luckan före den (`until` = dagen före); annars hämtas allt
     * från den äldsta av [from] och den gamla täckningen och framåt (`until` = `null`), så att en bredare täckning aldrig
     * krymper. Hämtningen ([fetch], från servern) körs i appens scope och antecknas när den är klar, också om den som
     * väntar har avbrutits – men bara om kontot fortfarande är detsamma ([isCurrent]) och ingen [forgetAll] kommit emellan.
     * Samtidiga anrop för samma samling går ett i taget, så att ingen ny hämtning startar medan en pågår. Ett fel från
     * [fetch] (också [DataError.Offline]) antecknar ingenting och kastas vidare. Går anteckningen inte att läsa räknas inget
     * som täckt (hellre en läsning för mycket än en post som saknas).
     */
    suspend fun ensure(key: String, from: LocalDate, isCurrent: () -> Boolean, fetch: suspend (from: LocalDate, until: LocalDate?) -> Unit) {
        val started = generation
        scope.async {
            lock(key).withLock {
                if (started != generation || !isCurrent()) return@withLock
                val today = clock.todayIn(zone.get())
                val coveredFrom = read(key + FROM)
                val fetchedOn = read(key + ON)
                val fresh = fetchedOn != null && fetchedOn <= today && fetchedOn > today.minus(REFRESH_DAYS, DateTimeUnit.DAY)
                when {
                    fresh && coveredFrom != null && from >= coveredFrom -> Unit
                    fresh && coveredFrom != null -> {
                        fetch(from, coveredFrom.minus(1, DateTimeUnit.DAY))
                        record(started, isCurrent) { write(key + FROM, from) }
                    }
                    else -> {
                        val start = coveredFrom?.let { minOf(it, from) } ?: from
                        fetch(start, null)
                        // Startdagen före hämtdagen: avbryts det mellan dem är täckningen ogiltig och hämtas om.
                        record(started, isCurrent) {
                            write(key + FROM, start)
                            write(key + ON, today)
                        }
                    }
                }
            }
        }.await()
    }

    /** Glömmer täckningen för alla konton på enheten – vid utloggningen, före och efter att cachen töms (AUTH-6). */
    suspend fun forgetAll() {
        writes.withLock {
            generation++
            marks.removeStartingWith(PREFIX)
        }
    }

    private suspend fun record(started: Long, isCurrent: () -> Boolean, block: suspend () -> Unit) {
        writes.withLock { if (started == generation && isCurrent()) block() }
    }

    private fun lock(key: String): Mutex = synchronized(locks) { locks.getOrPut(key) { Mutex() } }

    private suspend fun read(key: String): LocalDate? = suspendRunCatching({ DataError.Unknown }) { marks.get(PREFIX + key) }.getOrNull()

    private suspend fun write(key: String, day: LocalDate) {
        suspendRunCatching({ DataError.Unknown }) { marks.set(PREFIX + key, day) }
    }

    companion object {
        /** Täckningen hämtas om efter så här många dagar (NFR-24). */
        const val REFRESH_DAYS = 7

        /** Anteckningarnas nyckelprefix i [DayMarks]. */
        private const val PREFIX = "history."
        private const val FROM = ".from"
        private const val ON = ".on"
    }
}

/**
 * [EntityCollection.observeHistory] – en gång för båda implementationerna. För den inloggade ([uid]): [cache] direkt – en
 * lyssnare bara på den lokala cachen, som inte debiteras och som ser appens egna skrivningar – och samtidigt
 * [HistoryCoverage.ensure] för samlingen [key] (`null` när ingen sökväg finns) från [from] med [fetch] (serverläsningen
 * för just det kontot). Det som hämtas hamnar i cachen, och cachelyssnaren fyller på (offline först, NFR-1). Offline – och
 * när molnets kvot är slut ([DataError.QuotaExceeded]) – görs hämtningen om med växande paus (högst en minut, så att den
 * kommer snart efter att nätet kommit tillbaka) tills den lyckas; bara efter ett fel, aldrig medan en pågår. Andra fel
 * avslutar flödet. Utloggad emitteras inget; flödet följer kontot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> historyFlow(
    uid: StateFlow<String?>,
    coverage: HistoryCoverage,
    key: (uid: String) -> String?,
    from: LocalDate,
    to: LocalDate,
    fetch: suspend (uid: String, from: LocalDate, until: LocalDate?) -> Unit,
    cache: (uid: String) -> Flow<List<T>>,
): Flow<List<T>> = uid.flatMapLatest { account ->
    val coverageKey = account?.let(key) ?: return@flatMapLatest emptyFlow()
    // En tom period: ingen hämtning och ingen lyssnare.
    if (to < from) return@flatMapLatest flowOf(emptyList())
    channelFlow {
        launch {
            retryWithBackoffWhile(retryOn = { failure: DataError? -> failure != null }) {
                try {
                    coverage.ensure(coverageKey, from, { uid.value == account }) { start, until -> fetch(account, start, until) }
                    null
                } catch (e: DataError) {
                    if (e != DataError.Offline && e != DataError.QuotaExceeded) throw e
                    e
                }
            }
        }
        cache(account).collect { send(it) }
    }
}

/**
 * Läsningar av samma dagar slagna ihop: en post finns en gång, i den **senaste** listans version (lyssnade delar läggs
 * sist och vinner över historiken), sorterad som en läsning ([sortForList]).
 */
fun <T : Identified> mergeReads(lists: List<List<T>>): List<T> = sortForList(lists.flatten().associateBy { it.id }.values.toList())

/**
 * Dagarna [from]…[to] på datumfältet [field] (NFR-24): historikläget för hela perioden ([EntityCollection.observeHistory] –
 * cachen, hämtad en gång) och lyssnare ([observeDates]) bara på de dagar som ligger i [live], så att servern skickar
 * ändringar där. Dagar från idag och framåt ska ligga i [live]. När [live] byts (Idags veckosvep) byts bara lyssnarna –
 * historiken står still; delarna räknas i `:core` (`readParts`, ihopslagna och kapade till perioden), så att ett byte som
 * ger samma delar inte startar om något. Listorna slås ihop med [mergeReads]; tom period ger en tom lista.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T : Identified> EntityCollection<T>.observeDates(field: String, from: LocalDate, to: LocalDate, live: Flow<List<ClosedRange<LocalDate>>>): Flow<List<T>> {
    if (to < from) return flowOf(emptyList())
    val listened = live.map { ranges -> readParts(from, to, ranges).filter { it.live }.map { it.days } }.distinctUntilChanged().flatMapLatest { parts ->
        if (parts.isEmpty()) flowOf(emptyList()) else combine(parts.map { observeDates(field, it.start, it.endInclusive) }) { it.toList() }
    }
    return combine(observeHistory(field, from, to), listened) { history, lists -> mergeReads(listOf(history) + lists) }
}

/** [observeDates] med fasta lyssnade dagar [live] – Dagboken och Trender. */
fun <T : Identified> EntityCollection<T>.observeDates(field: String, from: LocalDate, to: LocalDate, live: List<ClosedRange<LocalDate>>): Flow<List<T>> =
    observeDates(field, from, to, flowOf(live))

/**
 * Historikläget **en gång** (NFR-24): dagarna [from]…[to] på datumfältet [field] som de första gången kommer ur cachen
 * ([EntityCollection.observeHistory]) – ingen lyssnare mot servern, så ingenting debiteras när perioden redan är täckt;
 * det som inte täcks hämtas en gång i bakgrunden och antecknas, också när svaret redan getts. Offline först, som
 * [EntityCollection.cachedBetween] var för en ny post: hellre ett svar ur cachen än att formuläret väntar. `null` när inget
 * kommit inom [wait], vid ett fel och utloggad – för förval som får utebli (AKT-12).
 */
suspend fun <T : Identified> EntityCollection<T>.historyOnce(field: String, from: LocalDate, to: LocalDate, wait: Duration): List<T>? =
    withTimeoutOrNull(wait) { observeHistory(field, from, to).catch { }.firstOrNull() }
