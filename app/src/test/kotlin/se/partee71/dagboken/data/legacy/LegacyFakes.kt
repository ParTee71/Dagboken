package se.partee71.dagboken.data.legacy

import android.net.Uri
import kotlin.time.Instant
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.LegacyMigrationCodec
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.data.FakeStore
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.UserFile
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.firestore.RawDocumentWriter
import se.partee71.dagboken.data.firestore.RawDocuments

// Fejkar för migreringens enhetsberoenden (skill testing-strategy): Firestore via FakeStore, filerna som värden.

class FakeLegacyRoomSource(var read: LegacyRoomRead = LegacyRoomRead.Missing, var fails: Boolean = false) : LegacyRoomSource {
    /** Kontrollsumman just nu – byts för att låtsas att 3.x skrivit i filen efter läsningen. */
    var fingerprint: String? = (read as? LegacyRoomRead.Tables)?.fingerprint

    override fun exists(): Boolean = read != LegacyRoomRead.Missing

    override suspend fun read(): LegacyRoomRead {
        if (fails) throw IllegalStateException("trasig fil")
        return read
    }

    var fingerprintFails = false

    override suspend fun fingerprint(): String? {
        if (fingerprintFails) throw java.io.IOException("filen låst")
        return fingerprint
    }
}

class FakeLegacyPreferencesSource(var values: Map<String, Any?> = emptyMap(), var fails: Boolean = false) : LegacyPreferencesSource {
    override suspend fun read(): Map<String, Any?> {
        if (fails) throw IllegalStateException("trasig fil")
        return values
    }
}

/**
 * Råa dokument över [store], som servern: batchen skrivs med merge (heltal som Long), markören sätts bara när den
 * saknas (rules), och [failAt]/[offline]/[tamper] framkallar fel i en batch, saknat nät vid läsningarna, respektive en
 * ändring på servern mellan skrivningen och verifieringen.
 */
class FakeRawFirestore(val store: FakeStore, private val now: Instant = Instant.fromEpochSeconds(1_790_000_000)) : RawDocuments, RawDocumentWriter {
    val batches = mutableListOf<List<ExportFormat.Document>>()
    var failAt: Int? = null
    var failWith: DataError = DataError.PermissionDenied
    var offline = false
    var markerFails: DataError? = null
    var markCalls = 0
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

    override suspend fun hasDocuments(path: String): Boolean {
        if (offline) throw DataError.Offline
        return store.documents.value[path].orEmpty().isNotEmpty()
    }

    override suspend fun writeBatch(documents: List<ExportFormat.Document>): Result<Unit> {
        if (failAt == batches.size) return Result.failure(failWith)
        batches += documents
        for (document in documents) {
            val collection = document.path.substringBeforeLast('/')
            val id = document.path.substringAfterLast('/')
            store.set(collection, id, mergeWithDeletes(store.read(collection, id).orEmpty(), document.data), merge = false)
        }
        return Result.success(Unit)
    }

    /** Som Firestores merge med `FieldValue.delete()` ([RawDocumentWriter.DELETE]): djup sammanslagning där ett markerat fält tas bort, även nästlat. */
    private fun mergeWithDeletes(old: Doc, new: Doc): Doc {
        val result = LinkedHashMap(old)
        for ((key, value) in new) {
            val previous = old[key]
            when {
                value === RawDocumentWriter.DELETE -> result.remove(key)
                value is Map<*, *> && previous is Map<*, *> -> result[key] = mergeWithDeletes(asDoc(previous), asDoc(value))
                else -> result[key] = value
            }
        }
        return result
    }

    val deletes = mutableListOf<List<String>>()
    var deleteFails: DataError? = null

    override suspend fun deleteBatch(paths: List<String>): Result<Unit> {
        deleteFails?.let { return Result.failure(it) }
        deletes += paths
        for (path in paths) store.delete(path.substringBeforeLast('/'), path.substringAfterLast('/'))
        return Result.success(Unit)
    }

    override suspend fun markLegacyMigration(uid: String, marker: Doc): Result<Unit> {
        markCalls++
        markerFails?.let { return Result.failure(it) }
        if (store.read(Paths.USERS, uid)?.get(LegacyMigrationCodec.FIELD) != null) return Result.failure(DataError.PermissionDenied)
        store.set(Paths.USERS, uid, mapOf(LegacyMigrationCodec.FIELD to marker + (LegacyMigrationCodec.COMPLETED_AT to now)), merge = true)
        return Result.success(Unit)
    }
}

class FakeMigrationState : MigrationDeviceState {
    val done = mutableSetOf<String>()
    var session: LegacySession? = null
    var copyRecord: CopyRecord? = null

    /** DataStore-filen går inte att läsa eller skriva (IOException), som på en full eller trasig lagring. */
    var fails = false

    private fun io() {
        if (fails) throw java.io.IOException("dagboken_device gick inte att nå")
    }

    override suspend fun isDone(uid: String) = uid in done

    override suspend fun markDone(uid: String) {
        done += uid
    }

    override suspend fun session(): LegacySession? = io().let { session }

    override suspend fun rememberSession(session: LegacySession) {
        if (this.session == null) this.session = session
    }

    override suspend fun copy(): CopyRecord? = io().let { copyRecord }

    override suspend fun rememberCopy(record: CopyRecord) {
        io()
        copyRecord = record
    }

    override suspend fun clearCopy() {
        io()
        copyRecord = null
    }

    var backupJobCancelled = false

    override suspend fun backupJobCancelled(): Boolean = backupJobCancelled

    override suspend fun markBackupJobCancelled() {
        backupJobCancelled = true
    }
}

/** Liggaren i minnet: skrivna (W), verifierade (V) och avvikande (M) sökvägar med hash per konto, sista raden vinner – som filen fast utan fil. [fails] = IO-fel. */
class FakeMigrationLedger : MigrationLedger {
    val pending = mutableMapOf<String, MutableMap<String, String>>()
    val verified = mutableMapOf<String, MutableMap<String, String>>()
    val mismatched = mutableMapOf<String, MutableMap<String, String>>()
    val planned = mutableMapOf<String, MutableMap<String, String>>()
    var deleted = 0
    var fails = false

    /** Antal tillägg som går innan IO-felet slår till (`null` = aldrig), för fel mitt i en körning. */
    var failsAfter: Int? = null
    private var appends = 0

    private fun io() {
        if (fails) throw java.io.IOException("liggaren gick inte att nå")
        failsAfter?.let { if (++appends > it) throw java.io.IOException("liggaren gick inte att skriva") }
    }

    override suspend fun read(uid: String): Ledger {
        io()
        return Ledger(pending[uid].orEmpty().toMap(), verified[uid].orEmpty().toMap(), mismatched[uid].orEmpty().toMap(), planned[uid].orEmpty().toMap())
    }

    override suspend fun appendPlanned(uid: String, hashes: Map<String, String>) {
        io()
        for ((path, hash) in hashes) planned.getOrPut(uid) { linkedMapOf() }[path] = hash
    }

    override suspend fun appendWritten(uid: String, hashes: Map<String, String>) = append(pending, uid, hashes)

    override suspend fun appendVerified(uid: String, hashes: Map<String, String>) = append(verified, uid, hashes)

    override suspend fun appendMismatched(uid: String, hashes: Map<String, String>) = append(mismatched, uid, hashes)

    override suspend fun appendCancelled(uid: String, paths: Collection<String>) {
        io()
        for (path in paths) planned[uid]?.remove(path)
    }

    private fun append(target: MutableMap<String, MutableMap<String, String>>, uid: String, hashes: Map<String, String>) {
        io()
        for ((path, hash) in hashes) {
            for (other in listOf(pending, verified, mismatched, planned)) other[uid]?.remove(path)
            target.getOrPut(uid) { linkedMapOf() }[path] = hash
        }
    }

    override suspend fun delete(uid: String) {
        deleted++
        pending.remove(uid)
        verified.remove(uid)
        mismatched.remove(uid)
        planned.remove(uid)
    }
}

class FakeLegacyWork : LegacyWork {
    var cancelled = 0
    var fails = false

    override suspend fun cancelBackupJob() {
        cancelled++
        if (fails) throw IllegalStateException("WorkManager saknas")
    }
}

/** Filen som SAF ger den: det skrivna läses tillbaka – genom [readBack], som kan förvanska det (en trasig lagring). */
class FakeCopyFile : UserFile {
    val files = mutableMapOf<Uri, String>()
    var writeFails = false
    var readBack: (String) -> String = { it }
    var name: String? = "dagboken-3x.json"

    override suspend fun write(uri: Uri, text: String) {
        if (writeFails) throw java.io.IOException("ingen plats")
        files[uri] = text
    }

    override suspend fun read(uri: Uri): String = readBack(files.getValue(uri))

    override suspend fun displayName(uri: Uri): String? = name
}

/** Drive som värden (BCK-14): det [read] säger, och antal anrop – samtycket och nätet styrs av testet. */
class FakeDriveBackups(var read: DriveRead = DriveRead.NoBackup) : DriveBackups {
    var calls = 0

    override suspend fun downloadLatestBackup(): DriveRead {
        calls++
        return read
    }
}
