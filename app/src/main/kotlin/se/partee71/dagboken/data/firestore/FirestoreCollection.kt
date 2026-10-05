package se.partee71.dagboken.data.firestore

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.snapshots
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.currentVersion
import se.partee71.dagboken.data.common.FieldPath
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

    override suspend fun delete(id: String): Result<Unit> = write { ref -> ref.document(id).delete() }

    /** `update`, inte `set`: ett dokument som raderats under tiden återuppstår inte som spöke. */
    override suspend fun setArchived(id: String, archived: Boolean): Result<Unit> = write { ref ->
        ref.document(id).update("archived", archived)
    }

    /** `update`, inte `set`: bara [fields] (och `updatedAt`) ändras, och ett dokument som raderats återuppstår inte. */
    override suspend fun updateAll(items: List<T>, fields: Set<String>): Result<Unit> =
        commit { items.map { BatchOp.Update(it.id, fieldsForUpdate(encode(it), fields)) } }

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

        data class Set(val id: String, val data: Map<String, Any?>) : BatchOp {
            override fun applyTo(batch: WriteBatch, ref: CollectionReference) {
                batch.set(ref.document(id), data, SetOptions.merge())
            }
        }

        data class Update(val id: String, val data: Map<String, Any?>) : BatchOp {
            override fun applyTo(batch: WriteBatch, ref: CollectionReference) {
                batch.update(ref.document(id), data)
            }
        }

        data class Delete(val id: String) : BatchOp {
            override fun applyTo(batch: WriteBatch, ref: CollectionReference) {
                batch.delete(ref.document(id))
            }
        }
    }

    companion object {
        /** Firestores gräns för antal skrivningar i en batch. */
        const val MAX_BATCH = 500
    }
}
