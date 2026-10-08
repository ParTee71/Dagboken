package se.partee71.dagboken.data.export

import android.net.Uri
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.UserFile
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.firestore.RawDocuments
import se.partee71.dagboken.data.firestore.firestoreError
import se.partee71.dagboken.data.legacy.readOrNull
import se.partee71.dagboken.di.DefaultDispatcher

/** En klar export: antal dokument per samling (`CollectionNames.ALL`-ordning, användardokumentet med) – aldrig innehåll. */
data class ExportSummary(val counts: Map<String, Int>) {
    /** Antal poster i filen – snackbaren "Sparade N poster"; användardokumentet räknas inte som en post. */
    val total: Int get() = (counts - CollectionNames.USERS).values.sum()
}

/**
 * Manuell export (BCK-13, SET-8): hela `users/{uid}` **rått** längs `Paths` – användardokumentet, varje samling i
 * Datamodell-ordning och incheckningarna efter sin episod, som `tools/db export` går igenom användaren – skrivs i
 * `ExportFormat` (= `tools/db export`, läsbar av `import.mjs` och av appens import, BCK-6) till filen användaren valt
 * (SAF). Först väntas enhetens köade skrivningar in (`awaitPendingWrites`), sedan läses allt från **servern**: en export
 * ur cachen kunde se hel ut men sakna dokument. Okända fält följer med (codecarna används inte). Health Connect-data
 * ligger aldrig i Firestore och kommer därför aldrig med (HLS-5). Loggar ingenting (NFR-13).
 */
class ExportUseCase @Inject constructor(
    private val documents: RawDocuments,
    private val files: UserFile,
    private val scope: UserScope,
    private val clock: Clock,
    @DefaultDispatcher private val computation: CoroutineDispatcher,
) {
    /** Förslaget i filväljaren: `dagboken-export-<datum>.json` (datum i [zone]). */
    fun fileName(zone: TimeZone): String = "$FILE_PREFIX${clock.now().toLocalDateTime(zone).date}.json"

    /**
     * Läser hela kontot och skriver filen på [uri]. Utan nät `Offline` (köade skrivningar når inte servern, eller
     * läsningen); en fil som inte går att skriva `Unknown`. Ett fel lämnar ingen halv export: texten byggs färdig först.
     */
    suspend fun export(uri: Uri): Result<ExportSummary> = suspendRunCatching(::firestoreError) {
        val uid = scope.uid.value ?: throw DataError.NotSignedIn
        documents.awaitPendingWrites()
        val all = read(uid)
        val user = all.firstOrNull { it.path == Paths.user(uid) }?.data
        val text = withContext(computation) {
            ExportFormat.encode(clock.now(), Schema.versionOf(user?.get(ExportFormat.SCHEMA_VERSION)), all)
        }
        // Ett fel i filen blir `Unknown` – inte ett Firestore-fel, och meddelandet kan nämna filens plats.
        readOrNull { files.write(uri, text) } ?: throw DataError.Unknown
        ExportSummary(CollectionNames.countsOf(all.map { it.path }))
    }

    /**
     * Användaren som `tools/db/lib/walk.mjs` går igenom den: användardokumentet, sedan samlingarna och under varje episod
     * dess incheckningar. Samlingarna läses parallellt, och undersamlingarna (en fråga per episod) högst [PARALLEL] i taget.
     * **Begränsning:** en incheckning vars episod saknar dokument hittas inte – klienten kan inte lista undersamlingar, och
     * en collectionGroup-fråga kan inte avgränsas till kontot i rules (sökvägen binds inte i en fråga). Appen kan inte skapa
     * en sådan (rules `existsAfter`); `tools/db export` tar med den.
     */
    private suspend fun read(uid: String): List<ExportFormat.Document> = coroutineScope {
        val user = async { documents.document(Paths.user(uid)) }
        val gate = Semaphore(PARALLEL)
        val collections = Paths.USER_COLLECTIONS.map { name ->
            async {
                val path = Paths.collection(uid, name)
                documents.collection(path).toSortedMap().map { (id, data) ->
                    val parent = ExportFormat.Document("$path/$id", data)
                    val children = Paths.SUBCOLLECTIONS[name].orEmpty().map { child ->
                        async { gate.withPermit { "${parent.path}/$child".let { childPath -> documents.collection(childPath).toSortedMap().map { (childId, childData) -> ExportFormat.Document("$childPath/$childId", childData) } } } }
                    }
                    parent to children
                }
            }
        }
        buildList {
            user.await()?.let { add(ExportFormat.Document(Paths.user(uid), it)) }
            for (collection in collections.awaitAll()) {
                for ((parent, children) in collection) {
                    add(parent)
                    children.awaitAll().forEach(::addAll)
                }
            }
        }
    }

    companion object {
        const val FILE_PREFIX = "dagboken-export-"

        /** Samtidiga frågor efter incheckningar – som `RawDocuments.documents`. */
        private const val PARALLEL = 8
    }
}
