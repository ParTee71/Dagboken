package se.partee71.dagboken.data.firestore

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.suspendRunCatching

/**
 * Råa dokument med merge, som `tools/db import.mjs` fast genom rules och med "`null` = rör inte" för
 * inställningarna (ARKITEKTUR.md → Migrering). Väntar på serverns kvitto: importen är inte offline
 * först – det som verifieras ska vara det som faktiskt finns i molnet (NFR-1).
 */
class FirestoreRawWriter @Inject constructor(private val firestore: FirestoreInstance) : RawDocumentWriter {
    override suspend fun writeBatch(documents: List<ExportFormat.Document>): Result<Unit> = suspendRunCatching(::firestoreError) {
        require(documents.size <= FirestoreCollection.MAX_BATCH) { "högst ${FirestoreCollection.MAX_BATCH} skrivningar per batch" }
        val db = firestore.db
        val batch = db.batch()
        for (document in documents) batch.set(db.document(document.path), toFirestore(document.data), SetOptions.merge())
        awaitServer(batch.commit())
    }

    override suspend fun deleteBatch(paths: List<String>): Result<Unit> = suspendRunCatching(::firestoreError) {
        require(paths.size <= FirestoreCollection.MAX_BATCH) { "högst ${FirestoreCollection.MAX_BATCH} raderingar per batch" }
        val db = firestore.db
        val batch = db.batch()
        for (path in paths) batch.delete(db.document(path))
        awaitServer(batch.commit())
    }

    /** Serverns kvitto, eller `Offline` när det inte kommer i tid. `await()` ger null även när det lyckas (Task<Void>). */
    private suspend fun awaitServer(task: Task<Void>) {
        val committed = withTimeoutOrNull(COMMIT_TIMEOUT) {
            task.await()
            true
        }
        if (committed != true) throw DataError.Offline
    }

    private companion object {
        /** En batch om 500 dokument tar normalt sekunder; utan nät står den i kö för alltid. */
        val COMMIT_TIMEOUT = 60.seconds
    }
}
