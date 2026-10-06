package se.partee71.dagboken.data

import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.currentVersion
import se.partee71.dagboken.data.common.FieldPath
import se.partee71.dagboken.data.common.PendingCommits
import se.partee71.dagboken.data.common.SERVER_WAIT
import se.partee71.dagboken.data.common.Stored
import se.partee71.dagboken.data.common.mayCreate
import se.partee71.dagboken.data.common.satisfies
import se.partee71.dagboken.data.common.storedOf
import se.partee71.dagboken.data.common.Snapshot
import se.partee71.dagboken.data.common.fieldsForMerge
import se.partee71.dagboken.data.common.firstFromCache
import se.partee71.dagboken.data.common.isDocumentAnswer
import se.partee71.dagboken.data.common.fieldsForUpdate
import se.partee71.dagboken.data.common.prepareForWrite
import se.partee71.dagboken.data.common.readDocument
import se.partee71.dagboken.data.common.sortForList
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.data.common.writeBlocker
import se.partee71.dagboken.data.firestore.CollectionTable
import se.partee71.dagboken.data.firestore.Paths

/**
 * Minnesbaserad databas med Firestores semantik för det appen använder: merge är djup för
 * nästlade maps, `null` lagras som `null`, heltal lagras som `Long`.
 */
class FakeStore {
    val documents = MutableStateFlow<Map<String, Map<String, Doc>>>(emptyMap())

    fun set(path: String, id: String, doc: Doc, merge: Boolean) = documents.update { all ->
        val collection = all[path].orEmpty()
        val stored = firestoreValues(doc)
        val merged = if (merge) deepMerge(collection[id].orEmpty(), stored) else stored
        all + (path to collection + (id to merged))
    }

    /**
     * Som Firestores `update` i en batch: varje fält ersätts helt, även en map – inget slås ihop
     * på djupet. Allt eller inget: finns ett dokument inte avvisar servern hela batchen.
     */
    fun patch(path: String, patches: Map<String, Doc>) = documents.update { all ->
        val collection = all[path].orEmpty()
        if (!collection.keys.containsAll(patches.keys)) return@update all
        all + (path to collection + patches.mapValues { (id, fields) -> (collection.getValue(id) + firestoreValues(fields)).filterValues { it !== DELETE } })
    }

    companion object {
        /** Som Firestores `FieldValue.delete()` i en [patch]: fältet tas bort. */
        val DELETE = Any()
    }

    fun delete(path: String, id: String) = documents.update { all -> all + (path to all[path].orEmpty() - id) }

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
) : EntityCollection<T> {
    private val commits = PendingCommits()

    override fun observe(): Flow<List<T>> = scope.uid.flatMapLatest { uid ->
        val p = pathOrNull(uid) ?: return@flatMapLatest emptyFlow()
        store.collection(p).map(::decodeList)
    }

    override fun observe(id: String): Flow<T?> = scope.uid.flatMapLatest { uid ->
        val p = pathOrNull(uid) ?: return@flatMapLatest emptyFlow()
        store.collection(p).map { docs -> docs[id]?.let { decode(id, it) } }
    }

    override suspend fun get(id: String) = run { store.read(path(), id)?.let { decode(id, it) } }

    override suspend fun getAll() = run { store.documents.value[path()].orEmpty().map { (id, doc) -> decode(id, doc) } }

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
     * text, tal med tal). Fejken är alltid "online" – offline prövas i `FirestoreOfflineTest`.
     */
    override suspend fun confirmedFrom(field: String, from: Any) = run {
        decodeList(store.documents.value[path()].orEmpty().filterValues { atLeast(it[field], from) })
    }

    override suspend fun confirmed() = run { decodeList(store.documents.value[path()].orEmpty()) }

    override suspend fun confirmed(id: String) = run { store.read(path(), id)?.let { decode(id, it) } }

    override suspend fun awaitWrites() = run { if (!commits.awaitAll(serverWait)) throw DataError.Offline }

    /** Fejken är alltid "online": varje id prövas mot det lagrade när den committar, som Firestores transaktion. */
    override suspend fun createIfAbsent(items: List<T>): Result<Unit> {
        val byId = items.associateBy { it.id }
        return transact(byId.keys) { p, id, stored -> if (stored.mayCreate()) store.set(p, id, encode(byId.getValue(id)), merge = true) }
    }

    override suspend fun deleteIf(ids: List<String>, condition: (T) -> Boolean) =
        transact(ids.toSet()) { p, id, stored -> if (stored.satisfies(condition)) store.delete(p, id) }

    override suspend fun updateIf(items: List<T>, fields: Set<String>, condition: (T) -> Boolean): Result<Unit> {
        val patches = runCatching { items.associate { it.id to fieldsForUpdate(encode(it), fields) } }.getOrElse { return Result.failure(DataError.Unknown) }
        return transact(patches.keys) { p, id, stored -> if (stored.satisfies(condition)) store.patch(p, mapOf(id to patches.getValue(id))) }
    }

    private suspend fun transact(ids: Set<String>, apply: (path: String, id: String, stored: Stored<T>) -> Unit) = write {
        val p = path()
        val commit = { ids.forEach { id -> apply(p, id, storedOf(store.read(p, id) != null) { store.read(p, id)?.let { decode(id, it) } }) } }
        val background = commitScope
        if (ids.isEmpty()) {
            Unit // som Firestore: ingen transaktion alls
        } else if (background == null) {
            commit()
        } else {
            val pending = background.async { delay(commitLatency); commit() }
            commits.await(pending, serverWait) ?: throw DataError.Offline
        }
    }

    override suspend fun delete(id: String) = write { store.delete(path(), id) }

    /** Som Firestores `update`: ett dokument som inte finns skapas inte. */
    override suspend fun setArchived(id: String, archived: Boolean) = write {
        val p = path()
        if (store.read(p, id) != null) store.set(p, id, mapOf("archived" to archived), merge = true)
    }

    override suspend fun updateAll(items: List<T>, fields: Set<String>, remove: Set<String>) = write {
        store.patch(path(), items.associate { it.id to fieldsForUpdate(encode(it), fields) + remove.associateWith { FakeStore.DELETE } })
    }

    override suspend fun batch(upserts: List<T>, deletes: List<String>) = write {
        val p = path()
        upserts.forEach { store.set(p, it.id, encode(it), merge = true) }
        deletes.forEach { store.delete(p, it) }
    }

    override fun newId(): String = "id-" + nextId++

    private fun encode(item: T) = prepareForWrite(codec.encode(item), clock.now())

    private fun decodeList(docs: Map<String, Doc>) = sortForList(docs.map { (id, doc) -> decode(id, doc) })

    private fun decode(id: String, doc: Doc) = readDocument(codec, name, scope.currentVersion(), id, doc)

    private fun path(): String = path(scope.uid.value)

    private fun atLeast(value: Any?, from: Any): Boolean = when {
        value is String && from is String -> value >= from
        value is Number && from is Number -> value.toDouble() >= from.toDouble()
        else -> false
    }

    private fun pathOrNull(uid: String?) = runCatching { path(uid) }.getOrNull()

    private suspend fun <R> run(block: suspend () -> R): Result<R> = suspendRunCatching({ DataError.Unknown }) { block() }

    private suspend fun write(block: suspend () -> Unit): Result<Unit> = run {
        scope.writeBlocker()?.let { throw it }
        block()
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
) : CollectionTable() {
    override fun <T : Identified> create(codec: DocCodec<T>, name: String, path: (uid: String?) -> String) =
        FakeCollection(store, scope, clock, codec, name, path)

    fun <T : Identified> collection(codec: DocCodec<T>, name: String, path: (uid: String?) -> String) = create(codec, name, path)
}
