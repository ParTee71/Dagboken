package se.partee71.dagboken.data.importing

import android.net.Uri
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.data.FakeStore
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.UserFile
import se.partee71.dagboken.data.firestore.RawDocumentWriter
import se.partee71.dagboken.data.firestore.RawDocuments

// Fejkar för importens beroenden (skill testing-strategy): Firestore via FakeStore, filerna som värden.

/**
 * Råa dokument över [store], som servern: batchen skrivs med merge (heltal som Long), och [failAt]/[offline]/[tamper]
 * framkallar fel i en batch, saknat nät vid läsningarna, respektive en ändring på servern mellan skrivningen och
 * verifieringen.
 */
class FakeRawFirestore(val store: FakeStore) :
    RawDocuments,
    RawDocumentWriter {
    val batches = mutableListOf<List<ExportFormat.Document>>()
    var failAt: Int? = null
    var failWith: DataError = DataError.PermissionDenied
    var offline = false
    var tamper: (() -> Unit)? = null

    override suspend fun awaitPendingWrites() {
        if (offline) throw DataError.Offline
    }

    /** Som servern: bara de som finns, på sökväg. [tamper] körs en gång före första läsningen efter en skriven batch. */
    var reads = 0

    override suspend fun documents(paths: Collection<String>): Map<String, Doc> {
        reads++
        if (offline) throw DataError.Offline
        if (batches.isNotEmpty()) tamper?.invoke().also { tamper = null }
        return paths.mapNotNull { path -> store.read(path.substringBeforeLast('/'), path.substringAfterLast('/'))?.let { path to it } }.toMap()
    }

    override suspend fun document(path: String): Doc? {
        if (offline) throw DataError.Offline
        return store.read(path.substringBeforeLast('/'), path.substringAfterLast('/'))
    }

    override suspend fun collection(path: String): Map<String, Doc> {
        if (offline) throw DataError.Offline
        return store.documents.value[path].orEmpty()
    }

    override suspend fun writeBatch(documents: List<ExportFormat.Document>): Result<Unit> {
        if (failAt == batches.size) return Result.failure(failWith)
        batches += documents
        for (document in documents) {
            val collection = document.path.substringBeforeLast('/')
            val id = document.path.substringAfterLast('/')
            store.set(collection, id, merge(store.read(collection, id).orEmpty(), document.data), merge = false)
        }
        return Result.success(Unit)
    }

    /** Som Firestores merge: djup sammanslagning av nästlade objekt; listor skrivs hela. */
    private fun merge(old: Doc, new: Doc): Doc {
        val result = LinkedHashMap(old)
        for ((key, value) in new) {
            val previous = old[key]
            result[key] = if (value is Map<*, *> && previous is Map<*, *>) merge(asDoc(previous), asDoc(value)) else value
        }
        return result
    }

    override suspend fun deleteBatch(paths: List<String>): Result<Unit> {
        for (path in paths) store.delete(path.substringBeforeLast('/'), path.substringAfterLast('/'))
        return Result.success(Unit)
    }
}

/** Filen som SAF ger den: det skrivna läses tillbaka – genom [readBack], som kan förvanska det (en trasig lagring). */
class FakeUserFile : UserFile {
    val files = mutableMapOf<Uri, String>()
    var writeFails = false
    var readBack: (String) -> String = { it }
    var name: String? = "dagboken-export.json"

    override suspend fun write(uri: Uri, text: String) {
        if (writeFails) throw java.io.IOException("ingen plats")
        files[uri] = text
    }

    override suspend fun read(uri: Uri): String = readBack(files.getValue(uri))

    override suspend fun displayName(uri: Uri): String? = name
}
