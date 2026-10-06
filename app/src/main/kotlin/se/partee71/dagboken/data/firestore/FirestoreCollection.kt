package se.partee71.dagboken.data.firestore

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.snapshots
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.asDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.currentVersion
import se.partee71.dagboken.data.common.FieldPath
import se.partee71.dagboken.data.common.PendingCommits
import se.partee71.dagboken.data.common.Stored
import se.partee71.dagboken.data.common.MoveOutcome
import se.partee71.dagboken.data.common.mayCreate
import se.partee71.dagboken.data.common.moveOutcome
import se.partee71.dagboken.data.common.movedDocument
import se.partee71.dagboken.data.common.satisfies
import se.partee71.dagboken.data.common.storedOf
import se.partee71.dagboken.data.common.SERVER_WAIT
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

/**
 * Den enda Firestore-koden för samlingar (skill firestore-data-layer). [path] ger samlingens
 * sökväg för ett uid, eller kastar [DataError.NotSignedIn] när ingen är inloggad.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreCollection<T : Identified>(
    private val firestore: FirestoreInstance,
    private val scope: UserScope,
    private val sync: FirestoreSyncStatus,
    private val clock: Clock,
    private val codec: DocCodec<T>,
    private val name: String,
    private val path: (uid: String?) -> String,
    /** Hur länge en läsning eller villkorad skrivning som kräver servern väntar innan den blir `Offline`. */
    private val serverWait: Duration = SERVER_WAIT,
) : EntityCollection<T> {
    /** Hämtas vid varje anrop – instansen byts när cachen tömts (AUTH-6). */
    private val db: FirebaseFirestore get() = firestore.db

    override fun observe(): Flow<List<T>> = scope.uid.flatMapLatest { uid ->
        val ref = refOrNull(uid) ?: return@flatMapLatest emptyFlow()
        ref.snapshots().map(::decodeList)
    }.catch { throw firestoreError(it) }

    override fun observe(id: String): Flow<T?> = scope.uid.flatMapLatest { uid ->
        val ref = refOrNull(uid) ?: return@flatMapLatest emptyFlow()
        ref.document(id).snapshots().map(::decodeDocument)
    }.catch { throw firestoreError(it) }

    /** Ett intervall på ett fält (`>=` och `<=` på samma fält) täcks av Firestores automatiska enkelfältsindex. */
    override fun observeBetween(field: String, from: Any, to: Any): Flow<List<T>> = scope.uid.flatMapLatest { uid ->
        val ref = refOrNull(uid) ?: return@flatMapLatest emptyFlow()
        ref.between(field, from, to).snapshots().map(::decodeList)
    }.catch { throw firestoreError(it) }

    override suspend fun cachedBetween(field: String, from: Any, to: Any): Result<List<T>> = firstFromCache(::firestoreError) {
        ref().between(field, from, to).snapshots().map { Snapshot(decodeList(it), it.metadata.isFromCache) }
    }

    private fun CollectionReference.between(field: String, from: Any, to: Any): Query =
        whereGreaterThanOrEqualTo(field, from).whereLessThanOrEqualTo(field, to)

    override suspend fun get(id: String): Result<T?> = suspendRunCatching(::firestoreError) {
        decodeDocument(ref().document(id).get().await())
    }

    override suspend fun getAll(): Result<List<T>> = suspendRunCatching(::firestoreError) {
        ref().get().await().documents.map { decode(it.id, it.data.orEmpty()) }
    }

    override suspend fun upsert(item: T): Result<Unit> = write { ref ->
        ref.document(item.id).set(encode(item), SetOptions.merge())
    }

    /** `set` med merge av bara [fields]: djup merge, skapar dokumentet om det saknas, läser inget. */
    override suspend fun merge(item: T, fields: Set<FieldPath>): Result<Unit> = write { ref ->
        ref.document(item.id).set(fieldsForMerge(encode(item), fields), SetOptions.merge())
    }

    override suspend fun cached(): Result<List<T>> = firstFromCache(::firestoreError) {
        ref().snapshots().map { Snapshot(decodeList(it), it.metadata.isFromCache) }
    }

    override suspend fun cached(id: String): Result<T?> = firstFromCache(::firestoreError, { it.isDocumentAnswer() }) {
        ref().document(id).snapshots().map { Snapshot(decodeDocument(it), it.metadata.isFromCache) }
    }

    override suspend fun confirmedFrom(field: String, from: Any): Result<List<T>> = fromServer { ref().whereGreaterThanOrEqualTo(field, from) }

    override suspend fun confirmed(): Result<List<T>> = fromServer { ref() }

    override suspend fun confirmed(id: String): Result<T?> = suspendRunCatching(::firestoreError) {
        val doc = ref().document(id)
        decodeDocument(withTimeoutOrNull(serverWait) { doc.get(Source.SERVER).await() } ?: throw DataError.Offline)
    }

    override suspend fun awaitWrites(): Result<Unit> = suspendRunCatching(::firestoreError) {
        val queued = withTimeoutOrNull(serverWait) { db.waitForPendingWrites().await() ?: Unit }
        if (queued == null || !commits.awaitAll(serverWait)) throw DataError.Offline
    }

    /** `get(Source.SERVER)`: aldrig ur cachen; offline ger Firestore UNAVAILABLE (→ `Offline`). */
    private suspend fun fromServer(query: () -> Query): Result<List<T>> = suspendRunCatching(::firestoreError) {
        val q = query()
        decodeList(withTimeoutOrNull(serverWait) { q.get(Source.SERVER).await() } ?: throw DataError.Offline)
    }

    override suspend fun createIfAbsent(items: List<T>): Result<Unit> {
        val byId = items.associateBy { it.id }
        return transact(byId.keys) { id, stored -> if (stored.mayCreate()) BatchOp.Set(id, encode(byId.getValue(id))) else null }
    }

    override suspend fun deleteIf(ids: List<String>, condition: (T) -> Boolean): Result<Unit> =
        transact(ids.toSet()) { id, stored -> if (stored.satisfies(condition)) BatchOp.Delete(id) else null }

    override suspend fun updateIf(items: List<T>, fields: Set<String>, condition: (T) -> Boolean): Result<Unit> {
        // Kodas före transaktionen, så att ett okänt fält är ett fel innan något läses.
        val patches = runCatching { items.associate { it.id to fieldsForUpdate(encode(it), fields) } }
            .getOrElse { return Result.failure(firestoreError(it)) }
        return transact(patches.keys) { id, stored -> if (stored.satisfies(condition)) BatchOp.Update(id, patches.getValue(id)) else null }
    }

    /**
     * Källan och målet läses i en transaktion (Firestore gör om den om något av dem ändras före commit), så att
     * källan som skrivs till målet är serverns version. Utfallet ([MoveOutcome]) ges ur transaktionen, inte som
     * ett kastat fel – det kommer då fram oinlindat.
     */
    override suspend fun move(from: String, item: T, fields: Set<String>, remove: Set<String>): Result<Unit> {
        // Kodas före transaktionen, så att ett okänt fält är ett fel innan något läses.
        val patch = runCatching { fieldsForUpdate(encode(item), fields) }
            .getOrElse { return Result.failure(firestoreError(it)) }
        return suspendRunCatching(::firestoreError) {
            scope.writeBlocker()?.let { throw it }
            val ref = ref()
            val source = ref.document(from)
            val target = ref.document(item.id)
            val task = db.runTransaction { tx ->
                val stored = tx.get(source).data
                val outcome = moveOutcome(stored, tx.get(target).exists())
                if (outcome == MoveOutcome.Moved) {
                    tx.set(target, movedDocument(checkNotNull(stored), patch, remove))
                    tx.delete(source)
                }
                outcome
            }
            awaitCommit(task)
        }.getOrElse { return Result.failure(it) }.toResult()
    }

    override suspend fun delete(id: String): Result<Unit> = write { ref -> ref.document(id).delete() }

    /** `update`, inte `set`: ett dokument som raderats under tiden återuppstår inte som spöke. */
    override suspend fun setArchived(id: String, archived: Boolean): Result<Unit> = write { ref ->
        ref.document(id).update("archived", archived)
    }

    /** `update`, inte `set`: bara [fields] (och `updatedAt`) ändras, och ett dokument som raderats återuppstår inte. */
    override suspend fun updateAll(items: List<T>, fields: Set<String>, remove: Set<String>): Result<Unit> =
        commit { items.map { BatchOp.Update(it.id, fieldsForUpdate(encode(it), fields) + remove.associateWith { FieldValue.delete() }) } }

    override suspend fun batch(upserts: List<T>, deletes: List<String>): Result<Unit> =
        commit { upserts.map { BatchOp.Set(it.id, encode(it)) } + deletes.map { BatchOp.Delete(it) } }

    /** Atomärt inom varje bit om 500 skrivningar (Firestores gräns), inte över bitar. */
    private suspend fun commit(operations: () -> List<BatchOp>): Result<Unit> =
        suspendRunCatching(::firestoreError) {
            scope.writeBlocker()?.let { throw it }
            val ref = ref()
            operations().chunked(MAX_BATCH).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { op -> op.applyTo(batch, ref) }
                sync.track(batch.commit())
            }
        }

    /**
     * Villkorade skrivningar: varje id läses från servern i en transaktion och [decide] avgör utifrån
     * det lagrade (`null` = finns inte) vad som skrivs. Firestore gör om transaktionen om något av
     * dokumenten ändras innan den bekräftats, så beslutet gäller alltid serverns version. Offline
     * misslyckas transaktionen ([DataError.Offline]) i stället för att läggas i kö. Svarar den inte
     * inom [serverWait] blir det också `Offline`, men den kan fortfarande committa: den spåras då i
     * [commits] (som [awaitWrites] väntar in) och i [sync] (ett sent fel syns som skrivfel). Ett
     * dokument som inte går att avkoda fäller inte bitens övriga ([Stored.Unreadable]).
     */
    private suspend fun transact(ids: Set<String>, decide: (id: String, stored: Stored<T>) -> BatchOp?): Result<Unit> =
        suspendRunCatching(::firestoreError) {
            scope.writeBlocker()?.let { throw it }
            val ref = ref()
            ids.chunked(MAX_BATCH).forEach { chunk ->
                val task = db.runTransaction { tx ->
                    // Alla läsningar före första skrivningen (Firestores regel för transaktioner).
                    val stored = chunk.associateWith { id ->
                        val snapshot = tx.get(ref.document(id))
                        storedOf(snapshot.exists()) { decodeDocument(snapshot) }
                    }
                    stored.forEach { (id, value) -> decide(id, value)?.applyTo(tx, ref) }
                }
                awaitCommit(task)
            }
        }

    /**
     * Väntar på transaktionen [task] högst [serverWait]. Svarar den inte i tid blir det [DataError.Offline], men
     * den kan fortfarande committa: den spåras då i [commits] (som [awaitWrites] väntar in) och i [sync].
     */
    private suspend fun <R : Any> awaitCommit(task: Task<R>): R =
        commits.await(task.asDeferred(), serverWait) ?: run {
            sync.track(task)
            throw DataError.Offline
        }

    /** Transaktioner som inte hann bekräftas i tid men fortfarande kan committa. */
    private val commits = PendingCommits()

    override fun newId(): String = db.collection(Paths.USERS).document().id

    private fun encode(item: T) = toFirestore(prepareForWrite(codec.encode(item), clock.now()))

    private fun decodeList(snapshot: QuerySnapshot) = sortForList(snapshot.documents.map { decode(it.id, it.data.orEmpty()) })

    private fun decodeDocument(doc: DocumentSnapshot) = doc.data?.let { decode(doc.id, it) }

    private fun decode(id: String, data: Map<String, Any?>) =
        readDocument(codec, name, scope.currentVersion(), id, fromFirestore(data))

    /** Skrivningen läggs i cachen direkt; servern svarar senare (offline först). */
    private suspend fun write(block: (CollectionReference) -> Task<*>): Result<Unit> =
        suspendRunCatching(::firestoreError) {
            scope.writeBlocker()?.let { throw it }
            sync.track(block(ref()))
        }

    private fun ref(): CollectionReference = db.collection(path(scope.uid.value))

    private fun refOrNull(uid: String?): CollectionReference? =
        runCatching { db.collection(path(uid)) }.getOrNull()

    private sealed interface BatchOp {
        fun applyTo(batch: WriteBatch, ref: CollectionReference)

        /** Samma skrivning i en transaktion ([transact]). */
        fun applyTo(tx: Transaction, ref: CollectionReference)

        data class Set(val id: String, val data: Map<String, Any?>) : BatchOp {
            override fun applyTo(batch: WriteBatch, ref: CollectionReference) {
                batch.set(ref.document(id), data, SetOptions.merge())
            }

            override fun applyTo(tx: Transaction, ref: CollectionReference) {
                tx.set(ref.document(id), data, SetOptions.merge())
            }
        }

        data class Update(val id: String, val data: Map<String, Any?>) : BatchOp {
            override fun applyTo(batch: WriteBatch, ref: CollectionReference) {
                batch.update(ref.document(id), data)
            }

            override fun applyTo(tx: Transaction, ref: CollectionReference) {
                tx.update(ref.document(id), data)
            }
        }

        data class Delete(val id: String) : BatchOp {
            override fun applyTo(batch: WriteBatch, ref: CollectionReference) {
                batch.delete(ref.document(id))
            }

            override fun applyTo(tx: Transaction, ref: CollectionReference) {
                tx.delete(ref.document(id))
            }
        }
    }

    companion object {
        /** Firestores gräns för antal skrivningar i en batch. */
        const val MAX_BATCH = 500
    }
}
