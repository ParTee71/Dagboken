package se.partee71.dagboken.data.firestore

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.LegacyMigrationCodec
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.suspendRunCatching

/**
 * Råa dokument med merge, som `tools/db import.mjs` fast genom rules och med "`null` = rör inte" för
 * inställningarna (ARKITEKTUR.md → Migrering, punkt 3). Väntar på serverns kvitto: migreringen är inte
 * offline först – det som verifieras ska vara det som faktiskt finns i molnet (NFR-1).
 */
class FirestoreRawWriter @Inject constructor(private val firestore: FirestoreInstance) : RawDocumentWriter {
    override suspend fun writeBatch(documents: List<ExportFormat.Document>): Result<Unit> = suspendRunCatching(::firestoreError) {
        require(documents.size <= FirestoreCollection.MAX_BATCH) { "högst ${FirestoreCollection.MAX_BATCH} skrivningar per batch" }
        val db = firestore.db
        val batch = db.batch()
        for (document in documents) batch.set(db.document(document.path), toFirestore(withDeletes(document.data)), SetOptions.merge())
        awaitServer(batch.commit())
    }

    override suspend fun deleteBatch(paths: List<String>): Result<Unit> = suspendRunCatching(::firestoreError) {
        require(paths.size <= FirestoreCollection.MAX_BATCH) { "högst ${FirestoreCollection.MAX_BATCH} raderingar per batch" }
        val db = firestore.db
        val batch = db.batch()
        for (path in paths) batch.delete(db.document(path))
        awaitServer(batch.commit())
    }

    override suspend fun markLegacyMigration(uid: String, marker: Doc): Result<Unit> = suspendRunCatching(::firestoreError) {
        val value = toFirestore(marker) + (LegacyMigrationCodec.COMPLETED_AT to FieldValue.serverTimestamp())
        awaitServer(firestore.db.document(Paths.user(uid)).set(mapOf(LegacyMigrationCodec.FIELD to value), SetOptions.merge()))
    }

    /**
     * [RawDocumentWriter.DELETE] → `FieldValue.delete()`, rekursivt i nästlade objekt (merge tar bort fältet). Går inte
     * in i listor: en lista skrivs alltid hel, och Firestore tillåter ingen `delete()` inne i ett listelement.
     */
    private fun withDeletes(doc: Doc): Doc = doc.mapValues { (_, value) ->
        when {
            value === RawDocumentWriter.DELETE -> FieldValue.delete()
            value is Map<*, *> -> withDeletes(asDoc(value))
            else -> value
        }
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
