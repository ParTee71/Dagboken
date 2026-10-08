package se.partee71.dagboken.data.firestore

import com.google.firebase.firestore.FieldPath as FirestoreFieldPath
import com.google.firebase.firestore.Source
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.data.common.DataError

/**
 * Läser alltid från servern: cachen innehåller bara
 * det som visats på enheten, och en export ur den skulle se hel ut men sakna dokument – som
 * `tools/db import --replace` då skulle radera. Utan nät blir det `DataError.Offline`. Används av
 * exporten i appen (BCK, etapp 5) och av migreringens läge, verifiering och startkontroll (OMB-2).
 */
class FirestoreRawDocuments @Inject constructor(private val firestore: FirestoreInstance) : RawDocuments {
    override suspend fun awaitPendingWrites() {
        if (!firestore.awaitPendingWrites(PENDING_TIMEOUT)) throw DataError.Offline
    }

    override suspend fun document(path: String): Doc? = firestore.db.document(path).get(Source.SERVER).await().data?.let(::fromFirestore)

    override suspend fun collection(path: String): Map<String, Doc> =
        firestore.db.collection(path).get(Source.SERVER).await().documents.associate { it.id to fromFirestore(it.data.orEmpty()) }

    /**
     * Grupper om 30 id:n per förälder, högst [PARALLEL] frågor i taget. Ett dokument med köade lokala skrivningar
     * (`metadata.hasPendingWrites`) utelämnas – serverns läge är inte känt för det, så migreringen räknar det som
     * overifierat i stället för att vänta in hela kön (`waitForPendingWrites` kan ta en minut utan nät).
     */
    override suspend fun documents(paths: Collection<String>): Map<String, Doc> = coroutineScope {
        val gate = Semaphore(PARALLEL)
        val groups = paths.groupBy({ it.substringBeforeLast('/') }, { it.substringAfterLast('/') })
            .flatMap { (parent, ids) -> ids.chunked(RawDocuments.ID_GROUP).map { group -> parent to group } }
            .map { (parent, ids) ->
                async {
                    gate.withPermit {
                        firestore.db.collection(parent).whereIn(FirestoreFieldPath.documentId(), ids).get(Source.SERVER).await()
                            .documents.filterNot { it.metadata.hasPendingWrites() }.associate { "$parent/${it.id}" to fromFirestore(it.data.orEmpty()) }
                    }
                }
            }
            .awaitAll()
        buildMap(paths.size) { groups.forEach(::putAll) }
    }

    private companion object {
        /** Når skrivningarna inte servern på den tiden räknas det som offline. */
        val PENDING_TIMEOUT = 60.seconds

        /** Samtidiga id-frågor – tillräckligt för tusentals dokument på sekunder utan att svälta nätet. */
        const val PARALLEL = 8
    }
}
