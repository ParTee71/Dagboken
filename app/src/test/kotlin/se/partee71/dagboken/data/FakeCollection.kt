package se.partee71.dagboken.data

import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.encodeDate
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.FieldMerge
import se.partee71.dagboken.data.common.FieldPath
import se.partee71.dagboken.data.common.HistoryCoverage
import se.partee71.dagboken.data.common.MemoryDayMarks
import se.partee71.dagboken.data.common.MoveOutcome
import se.partee71.dagboken.data.common.PendingCommits
import se.partee71.dagboken.data.common.SERVER_WAIT
import se.partee71.dagboken.data.common.Snapshot
import se.partee71.dagboken.data.common.Stored
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.currentVersion
import se.partee71.dagboken.data.common.fieldsForMerge
import se.partee71.dagboken.data.common.fieldsForUpdate
import se.partee71.dagboken.data.common.firstFromCache
import se.partee71.dagboken.data.common.historyFlow
import se.partee71.dagboken.data.common.isDocumentAnswer
import se.partee71.dagboken.data.common.mayCreate
import se.partee71.dagboken.data.common.moveOutcome
import se.partee71.dagboken.data.common.movedDocument
import se.partee71.dagboken.data.common.prepareForWrite
import se.partee71.dagboken.data.common.readDocument
import se.partee71.dagboken.data.common.satisfies
import se.partee71.dagboken.data.common.sortForList
import se.partee71.dagboken.data.common.storedOf
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.data.common.testCoverage
import se.partee71.dagboken.data.common.writeBlocker
import se.partee71.dagboken.data.firestore.CollectionTable
import se.partee71.dagboken.data.firestore.Paths

/**
 * Minnesbaserad databas med Firestores semantik för det appen använder: merge är djup för
 * nästlade maps, `null` lagras som `null`, heltal lagras som `Long`.
 */
class FakeStore {
    val documents = MutableStateFlow<Map<String, Map<String, Doc>>>(emptyMap())

    /**
     * Om "servern" nås. Fejken är cache och server i ett; med `false` beter den sig som Firestore utan
     * nät: läsflöden, engångsläsningar ur cachen och skrivningar som läggs i cachen fungerar (svaren är
     * då `fromCache`), men det som kräver servern – `confirmed…`, en villkorad skrivning med något att
     * pröva, `awaitWrites` när appen skrivit något utan nät – blir `Offline` och skriver inget. Åter
     * online räknas de väntande skrivningarna som synkade.
     */
    @Volatile var online: Boolean = true
        set(value) {
            field = value
            if (value) hasPendingWrites = false
        }

    /** Appen har skrivit något medan [online] var `false` (som Firestores väntande skrivningar). */
    @Volatile var hasPendingWrites: Boolean = false
        private set

    /** En skrivning från appen: utan nät väntar den på servern. */
    fun noteWrite() {
        if (!online) hasPendingWrites = true
    }

    /**
     * Debiterade läsningar (NFR-24), räknade som Firestore gör: en lyssnare för sitt resultat när den startar (fejken
     * räknar varje start så – Firestore tar upp en lyssnare billigt inom 30 minuter, så siffran är en övre gräns), en
     * serverläsning för sitt resultat, båda minst en läsning även för ett tomt svar, och en läsning ur cachen ingenting.
     */
    val reads = AtomicLong()

    fun bill(results: Int) {
        reads.addAndGet(maxOf(1, results).toLong())
    }

    /**
     * Dokumenten (id:n per sökväg) som enhetens cache känner till – allt appen skrivit och allt som lästs från servern
     * eller via en lyssnare. Bara historiklägets cacheläsning begränsas av den; i övrigt är fejken cache och server i ett.
     */
    val cached = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    fun markCached(path: String, ids: Set<String>) = cached.update { all ->
        val known = all[path].orEmpty()
        if (known.containsAll(ids)) all else all + (path to known + ids)
    }

    /** Cachen känner inget – som en ny enhet, eller som om en annan enhet skrivit allt som finns (NFR-24). */
    fun clearCache() {
        cached.value = emptyMap()
    }

    fun cachedIn(path: String): Flow<Set<String>> = cached.map { it[path].orEmpty() }.distinctUntilChanged()

    fun set(path: String, id: String, doc: Doc, merge: Boolean) = documents.update { all ->
        val collection = all[path].orEmpty()
        val stored = firestoreValues(doc)
        val merged = if (merge) deepMerge(collection[id].orEmpty(), stored) else stored
        all + (path to collection + (id to merged))
    }.also { markCached(path, setOf(id)) }

    /**
     * Som Firestores `update` i en batch: varje fält ersätts helt, även en map – inget slås ihop
     * på djupet. Allt eller inget: finns ett dokument inte avvisar servern hela batchen.
     */
    fun patch(path: String, patches: Map<String, Doc>) = documents.update { all ->
        val collection = all[path].orEmpty()
        if (!collection.keys.containsAll(patches.keys)) return@update all
        all + (path to collection + patches.mapValues { (id, fields) -> (collection.getValue(id) + firestoreValues(fields)).filterValues { it !== DELETE } })
    }.also { markCached(path, patches.keys) }

    companion object {
        /** Som Firestores `FieldValue.delete()` i en [patch]: fältet tas bort. */
        val DELETE = Any()
    }

    fun delete(path: String, id: String) = documents.update { all -> all + (path to all[path].orEmpty() - id) }

    /** En batch i en enda ändring: [sets] med merge och [deletes]. */
    fun batch(path: String, sets: List<Pair<String, Doc>>, deletes: List<String>) = documents.update { all ->
        var collection = all[path].orEmpty()
        for ((id, doc) in sets) collection = collection + (id to deepMerge(collection[id].orEmpty(), firestoreValues(doc)))
        all + (path to collection - deletes.toSet())
    }.also { markCached(path, sets.mapTo(mutableSetOf()) { it.first }) }

    /** Som en transaktion: [to] får [doc] (Firestores värden) och [from] tas bort – i en och samma ändring. */
    fun move(path: String, from: String, to: String, doc: Doc) = documents.update { all ->
        all + (path to all[path].orEmpty() - from + (to to firestoreValues(doc)))
    }.also { markCached(path, setOf(to)) }

    fun read(path: String, id: String): Doc? = documents.value[path]?.get(id)

    fun collection(path: String): Flow<Map<String, Doc>> = documents.map { it[path].orEmpty() }.distinctUntilChanged()

    private fun deepMerge(old: Doc, new: Doc): Doc = old + new.mapValues { (key, value) ->
        val previous = old[key]
        if (value is Map<*, *> && previous is Map<*, *>) deepMerge(asDoc(previous), asDoc(value)) else value
    }

    @Suppress("UNCHECKED_CAST")
    private fun asDoc(value: Map<*, *>) = value as Doc

    private fun firestoreValues(doc: Doc): Doc = doc.mapValues { (_, v) -> firestoreValue(v) }

    private fun firestoreValue(value: Any?): Any? = when (value) {
        is Int -> value.toLong()
        is Map<*, *> -> value.mapValues { firestoreValue(it.value) }
        is List<*> -> value.map(::firestoreValue)
        else -> value
    }
}

/** `EntityCollection` i minnet – samma regler som `FirestoreCollection` (bevisat av `CollectionContract`). */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeCollection<T : Identified>(
    private val store: FakeStore,
    private val scope: UserScope,
    private val clock: Clock,
    private val codec: DocCodec<T>,
    private val name: String,
    private val path: (uid: String?) -> String,
    /**
     * Fördröjd commit av villkorade skrivningar (dåligt nät): med [commitScope] committar de efter
     * [commitLatency] i bakgrunden, och den som väntar ger upp efter [serverWait] – som i Firestore.
     */
    private val commitLatency: Duration = Duration.ZERO,
    private val serverWait: Duration = SERVER_WAIT,
    private val commitScope: CoroutineScope? = null,
    /** Historiklägets täckning (NFR-24); [FakeCollectionFactory] delar en mellan sina samlingar, som appen. */
    private val coverage: HistoryCoverage = testCoverage(MemoryDayMarks(), clock)
) : EntityCollection<T> {
    private val commits = PendingCommits()

    override fun observe(): Flow<List<T>> = scope.uid.flatMapLatest { uid ->
        val p = pathOrNull(uid) ?: return@flatMapLatest emptyFlow()
        listen(p) { it }.map(::decodeList)
    }

    override fun observe(id: String): Flow<T?> = scope.uid.flatMapLatest { uid ->
        val p = pathOrNull(uid) ?: return@flatMapLatest emptyFlow()
        listen(p) { docs -> docs.filterKeys { it == id } }.map { docs -> docs[id]?.let { decode(id, it) } }
    }

    override fun observeBetween(field: String, from: Any, to: Any): Flow<List<T>> = scope.uid.flatMapLatest { uid ->
        val p = pathOrNull(uid) ?: return@flatMapLatest emptyFlow()
        listen(p) { docs -> inRange(docs, field, from, to) }.map(::decodeList)
    }

    /**
     * En lyssnare på [select] ur samlingen [p]: debiteras för sitt första resultat ([FakeStore.bill], som Firestore när
     * den startar), och det den ser hamnar i cachen ([FakeStore.markCached]).
     */
    private fun listen(p: String, select: (Map<String, Doc>) -> Map<String, Doc>): Flow<Map<String, Doc>> = flow {
        var started = false
        store.collection(p).collect { all ->
            val docs = select(all)
            if (!started) store.bill(docs.size)
            started = true
            store.markCached(p, docs.keys)
            emit(docs)
        }
    }

    /** En serverläsning av [docs] ur [p]: debiteras och hamnar i cachen. */
    private fun served(p: String, docs: Map<String, Doc>): Map<String, Doc> {
        store.bill(docs.size)
        store.markCached(p, docs.keys)
        return docs
    }

    /**
     * Som Firestore: ur cachen också utan nät ([FakeStore.online]), då märkt `fromCache`. Med "server" är svaret serverns –
     * en vanlig lyssnare som hann få svar – och debiteras för sitt resultat (NFR-24); utan nät kostar det inget.
     */
    override suspend fun cachedBetween(field: String, from: Any, to: Any) = firstFromCache({ DataError.Unknown }) {
        val p = path()
        store.collection(p).map { all ->
            val docs = inRange(all, field, from, to)
            Snapshot(decodeList(if (store.online) served(p, docs) else docs), fromCache = !store.online)
        }
    }

    /**
     * Som Firestore: serverläsningen (utan "server" `Offline`) debiteras och lägger dokumenten i cachen; cacheläsningen ser
     * bara de dokument cachen känner till ([FakeStore.cached]) – en ofullständig cache syns alltså, som på en ny enhet.
     */
    override fun observeHistory(field: String, from: LocalDate, to: LocalDate): Flow<List<T>> = historyFlow(
        uid = scope.uid,
        coverage = coverage,
        key = { uid -> pathOrNull(uid)?.let { "$it.$field" } },
        from = from,
        to = to,
        fetch = { uid, start, until ->
            server {
                val p = path(uid)
                served(p, store.documents.value[p].orEmpty().filterValues { atLeast(it[field], date(start)) && (until == null || atMost(it[field], date(until))) })
            }.getOrThrow()
        },
        cache = { uid ->
            val p = path(uid)
            combine(store.collection(p), store.cachedIn(p)) { docs, known -> decodeList(inRange(docs.filterKeys { it in known }, field, date(from), date(to))) }
        }
    )

    private fun date(day: LocalDate): String = checkNotNull(day.encodeDate())

    private fun inRange(docs: Map<String, Doc>, field: String, from: Any, to: Any) = docs.filterValues { atLeast(it[field], from) && atMost(it[field], to) }

    override suspend fun get(id: String) = run { path().let { p -> served(p, store.read(p, id)?.let { mapOf(id to it) }.orEmpty())[id]?.let { decode(id, it) } } }

    override suspend fun getAll() = run { path().let { p -> served(p, store.documents.value[p].orEmpty()).map { (id, doc) -> decode(id, doc) } } }

    override suspend fun upsert(item: T) = write { store.set(path(), item.id, encode(item), merge = true) }

    override suspend fun merge(item: T, fields: Set<FieldPath>) = write {
        store.set(path(), item.id, fieldsForMerge(encode(item), fields), merge = true)
    }

    /** Fejken är cache och server i ett: varje svar är bekräftat (`fromCache = false`). */
    override suspend fun cached() = firstFromCache({ DataError.Unknown }) {
        store.collection(path()).map { Snapshot(decodeList(it), fromCache = false) }
    }

    override suspend fun cached(id: String) = firstFromCache<T?>({ DataError.Unknown }, { it.isDocumentAnswer() }) {
        store.collection(path()).map { docs -> Snapshot(docs[id]?.let { decode(id, it) }, fromCache = false) }
    }

    /**
     * Som Firestores `whereGreaterThanOrEqualTo` med `Source.SERVER`: bara samma typ jämförs (text med
     * text, tal med tal). Utan "server" ([FakeStore.online]) `Offline`, som i `FirestoreOfflineTest`.
     */
    override suspend fun confirmedFrom(field: String, from: Any) = server {
        val p = path()
        decodeList(served(p, store.documents.value[p].orEmpty().filterValues { atLeast(it[field], from) }))
    }

    override suspend fun confirmed() = server { path().let { p -> decodeList(served(p, store.documents.value[p].orEmpty())) } }

    override suspend fun confirmed(id: String) = server { path().let { p -> served(p, store.read(p, id)?.let { mapOf(id to it) }.orEmpty())[id]?.let { decode(id, it) } } }

    /** Som Firestore: utan väntande skrivningar klar direkt, också utan nät. */
    override suspend fun awaitWrites() = run {
        path()
        if (store.hasPendingWrites || !commits.awaitAll(serverWait)) throw DataError.Offline
    }

    /** Varje id prövas mot det lagrade när den committar, som Firestores transaktion; utan "server" `Offline`. */
    override suspend fun createIfAbsent(items: List<T>): Result<Unit> {
        val byId = items.associateBy { it.id }
        return transact(byId.keys) { p, id, stored -> if (stored.mayCreate()) store.set(p, id, encode(byId.getValue(id)), merge = true) }
    }

    override suspend fun deleteIf(ids: List<String>, condition: (T) -> Boolean) = transact(ids.toSet()) { p, id, stored -> if (stored.satisfies(condition)) store.delete(p, id) }

    override suspend fun updateIf(items: List<T>, fields: Set<String>, condition: (T) -> Boolean): Result<Unit> {
        val patches = runCatching { items.associate { it.id to fieldsForUpdate(encode(it), fields) } }.getOrElse { return Result.failure(DataError.Unknown) }
        return transact(patches.keys) { p, id, stored -> if (stored.satisfies(condition)) store.patch(p, mapOf(id to patches.getValue(id))) }
    }

    private suspend fun transact(ids: Set<String>, apply: (path: String, id: String, stored: Stored<T>) -> Unit) = write(queued = false) {
        if (ids.isEmpty()) {
            path() // som Firestore: ingen transaktion alls, men utloggad är fortfarande ett fel
            return@write
        }
        transaction { p -> ids.forEach { id -> apply(p, id, storedOf(store.read(p, id) != null) { store.read(p, id)?.let { decode(id, it) } }) } }
    }

    /** Källan och målet prövas när den committar, som Firestores transaktion; utan "server" `Offline`. */
    override suspend fun move(from: String, item: T, fields: Set<String>, remove: Set<String>): Result<Unit> {
        val patch = runCatching { fieldsForUpdate(encode(item), fields) }.getOrElse { return Result.failure(DataError.Unknown) }
        var outcome = MoveOutcome.Moved
        write(queued = false) {
            transaction { p ->
                val stored = store.read(p, from)
                outcome = moveOutcome(stored, store.read(p, item.id) != null)
                if (outcome == MoveOutcome.Moved) store.move(p, from, item.id, movedDocument(checkNotNull(stored), patch, remove))
            }
        }.onFailure { return Result.failure(it) }
        return outcome.toResult()
    }

    /** En transaktion: kräver "servern"; med [commitScope] committar den efter [commitLatency], som i Firestore. */
    private suspend fun <R> transaction(commit: (path: String) -> R): R {
        val p = path()
        if (!store.online) throw DataError.Offline
        val background = commitScope ?: return commit(p)
        val pending = background.async {
            delay(commitLatency)
            Box(commit(p))
        }
        return (commits.await(pending, serverWait) ?: throw DataError.Offline).value
    }

    /** [PendingCommits.await] kräver ett icke-null-värde. */
    private class Box<R>(val value: R)

    override suspend fun delete(id: String) = write { store.delete(path(), id) }

    /** Som Firestores `update`: ett dokument som inte finns skapas inte. */
    override suspend fun setArchived(id: String, archived: Boolean) = write {
        val p = path()
        if (store.read(p, id) != null) store.set(p, id, mapOf("archived" to archived), merge = true)
    }

    override suspend fun updateAll(items: List<T>, fields: Set<String>, remove: Set<String>) = write {
        store.patch(path(), items.associate { it.id to fieldsForUpdate(encode(it), fields) + remove.associateWith { FakeStore.DELETE } })
    }

    /** Som Firestores batch: allt eller inget – ett okänt fält skriver inget. */
    override suspend fun batch(upserts: List<T>, deletes: List<String>, merges: List<FieldMerge<T>>) = write {
        val p = path()
        val sets = upserts.map { it.id to encode(it) } + merges.map { it.item.id to fieldsForMerge(encode(it.item), it.fields) }
        store.batch(p, sets, deletes)
    }

    override fun newId(): String = "id-" + nextId++

    private fun encode(item: T) = prepareForWrite(codec.encode(item), clock.now())

    private fun decodeList(docs: Map<String, Doc>) = sortForList(docs.map { (id, doc) -> decode(id, doc) })

    private fun decode(id: String, doc: Doc) = readDocument(codec, name, scope.currentVersion(), id, doc)

    private fun path(): String = path(scope.uid.value)

    private fun atLeast(value: Any?, bound: Any) = compare(value, bound)?.let { it >= 0 } == true

    private fun atMost(value: Any?, bound: Any) = compare(value, bound)?.let { it <= 0 } == true

    /** Som Firestores jämförelse i en fråga: bara samma typ (text med text, tal med tal); annars `null`. */
    private fun compare(value: Any?, bound: Any): Int? = when {
        value is String && bound is String -> value.compareTo(bound)
        value is Number && bound is Number -> value.toDouble().compareTo(bound.toDouble())
        else -> null
    }

    private fun pathOrNull(uid: String?) = runCatching { path(uid) }.getOrNull()

    private suspend fun <R> run(block: suspend () -> R): Result<R> = suspendRunCatching({ DataError.Unknown }) { block() }

    /** Läsning som kräver servern: utloggad `NotSignedIn` först, sedan `Offline` utan "server". */
    private suspend fun <R> server(block: suspend () -> R): Result<R> = run {
        path()
        if (!store.online) throw DataError.Offline
        block()
    }

    /** [queued] = läggs i cachen och väntar på servern utan nät; en transaktion köas aldrig. */
    private suspend fun write(queued: Boolean = true, block: suspend () -> Unit): Result<Unit> = run {
        scope.writeBlocker()?.let { throw it }
        block()
        if (queued) store.noteWrite()
    }

    private companion object {
        var nextId = 0
    }
}

/** Samma tabell som appen (`CollectionTable`), men med [FakeCollection]; [collection] ger en godtycklig samling (kontraktstestet). */
class FakeCollectionFactory(
    val store: FakeStore = FakeStore(),
    val scope: UserScope = TestUserScope(),
    private val clock: Clock = FixedClock(),
    /** Enhetens lokala anteckningar – historiklägets täckning ligger här (NFR-24). */
    val marks: MemoryDayMarks = MemoryDayMarks()
) : CollectionTable() {
    /** En täckning för alla samlingar, som appens `@Singleton`. */
    val coverage = testCoverage(marks, clock)

    override fun <T : Identified> create(codec: DocCodec<T>, name: String, path: (uid: String?) -> String) = FakeCollection(store, scope, clock, codec, name, path, coverage = coverage)

    fun <T : Identified> collection(codec: DocCodec<T>, name: String, path: (uid: String?) -> String) = create(codec, name, path)
}
