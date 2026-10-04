package se.partee71.dagboken.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.data.common.DataError

/**
 * Läser alltid från servern, efter att enhetens egna skrivningar nått dit: cachen innehåller bara
 * det som visats på enheten, och en export ur den skulle se hel ut men sakna dokument – som
 * `tools/db import --replace` då skulle radera. Utan nät blir det `DataError.Offline`. Används av
 * exporten i appen (BCK, etapp 5).
 */
class FirestoreRawDocuments @Inject constructor(private val db: FirebaseFirestore) : RawDocuments {
    override suspend fun awaitPendingWrites() {
        // await() ger null även när det lyckas (Task<Void>) – därför ett eget kvitto.
        withTimeoutOrNull(PENDING_TIMEOUT) {
            db.waitForPendingWrites().await()
            true
        } ?: throw DataError.Offline
    }

    override suspend fun document(path: String): Doc? = db.document(path).get(Source.SERVER).await().data?.let(::fromFirestore)

    override suspend fun collection(path: String): Map<String, Doc> =
        db.collection(path).get(Source.SERVER).await().documents.associate { it.id to fromFirestore(it.data.orEmpty()) }

    private companion object {
        /** Når skrivningarna inte servern på den tiden räknas det som offline. */
        val PENDING_TIMEOUT = 60.seconds
    }
}
