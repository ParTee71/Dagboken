package se.partee71.dagboken.data.legacy

import android.app.PendingIntent
import android.net.Uri
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import se.partee71.dagboken.core.legacy.ConversionReport
import se.partee71.dagboken.core.legacy.ImportFile
import se.partee71.dagboken.core.legacy.ImportFileResult
import se.partee71.dagboken.core.legacy.ImportFormat
import se.partee71.dagboken.core.legacy.MigrationBatches
import se.partee71.dagboken.core.legacy.Warning
import se.partee71.dagboken.core.model.LegacySource
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.UserFile
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.currentVersion
import se.partee71.dagboken.data.common.writeBlocker
import se.partee71.dagboken.data.firestore.RawDocumentWriter
import se.partee71.dagboken.data.firestore.RawDocuments
import se.partee71.dagboken.di.DefaultDispatcher

/** Vad läsningen av en importfil gav (BCK-6, BCK-14, OMB-5) – det granskningen visar. Aldrig innehåll. */
sealed interface ImportRead {
    /** Klart att granska: antal per typ, varningar och dokumenten att skriva. */
    data class Ready(val plan: ImportPlan) : ImportRead

    /** Filen stoppades (OMB-3): rapporten med alla fel – ingenting skrivs. */
    data class Stopped(val report: ConversionReport) : ImportRead

    /** Filen är varken en 3.x-backup eller en 4.0-export. */
    data object NotABackup : ImportRead

    /** Ingen 3.x-backup i Drive för kontot. */
    data object NoDriveBackup : ImportRead

    /** Drive kräver användarens samtycke till `appDataFolder` – skärmen startar [consent] och läser igen vid ja. */
    data class NeedsDriveConsent(val consent: PendingIntent) : ImportRead

    /** Läsningen gick inte: [error] för Drive och kontot, `null` när filen på enheten inte gick att öppna. */
    data class Failed(val error: DataError?) : ImportRead
}

/**
 * Det som ska skrivas till kontot [uid]: [documents] utan `users/{uid}`, "före" per entitet ([counts]) och varningarna
 * (3.x-konverterarens; tomt för en 4.0-export). [source] = varifrån filen kom.
 */
data class ImportPlan(
    val uid: String,
    val source: LegacySource,
    val format: ImportFormat,
    val documents: List<ExportFormat.Document>,
    val counts: Map<String, Int>,
    val warnings: List<Warning>,
    /** Dokument som redan finns i kontot med samma id men andra värden – de som ersätts (granskningens notis). */
    val replaced: Int = 0,
) {
    val total: Int get() = documents.size
}

/**
 * Importen (BCK-6, BCK-14, OMB-5): en 3.x-backup (Drive eller fil) eller en 4.0-export läses av `ImportFile` i `:core` –
 * den **enda** konverteraren för 3.x – och skrivs efter användarens bekräftelse i `MigrationBatches` form (≤ 500
 * skrivningar, ≤ 20 episoder, episoden med sina incheckningar) med serverns kvitto per batch och verifieras sedan per id,
 * som flytten (OMB-2). **Samma id ersätts** (DAT-13, inga dubbletter): dokumentets fält skrivs med merge; fält som bara
 * finns på servern står kvar – importen tar aldrig bort något. Ett dokument som redan är lika skrivs inte. Ett stopp i
 * filen skriver ingenting; ett fel i en batch stannar och kan göras om utan dubbletter. Ingen markör och ingen liggare:
 * importen kan alltid köras igen och har inget att avbryta. Påminnelserna pausas medan den skriver. Loggar ingenting (NFR-13).
 */
class LegacyImportUseCase @Inject constructor(
    private val drive: DriveBackups,
    private val files: UserFile,
    private val writer: RawDocumentWriter,
    private val documents: RawDocuments,
    private val pause: LegacyMigrationPause,
    private val scope: UserScope,
    @DefaultDispatcher private val computation: CoroutineDispatcher,
) {
    /** Filen användaren valt i dokumentväljaren (SAF). Ingenting skrivs. */
    suspend fun readFile(uri: Uri): ImportRead {
        val text = readOrNull { files.read(uri) } ?: return ImportRead.Failed(null)
        return interpret(text, LegacySource.JSON)
    }

    /** Den senaste 3.x-backupen i Drive (`appDataFolder`); kan kräva samtycke. Ingenting skrivs. */
    suspend fun readDrive(): ImportRead = when (val read = drive.downloadLatestBackup()) {
        is DriveRead.Found -> interpret(read.text, LegacySource.DRIVE)
        DriveRead.NoBackup -> ImportRead.NoDriveBackup
        is DriveRead.NeedsConsent -> ImportRead.NeedsDriveConsent(read.consent)
        is DriveRead.Failed -> ImportRead.Failed(read.error)
    }

    /**
     * Filen genom `ImportFile`; för en giltig fil läses sedan kontots läge på id från servern (efter enhetens köade
     * skrivningar) för att räkna hur många dokument som ersätts. Utan nät [ImportRead.Failed] med `Offline`.
     */
    private suspend fun interpret(text: String, source: LegacySource): ImportRead = withContext(computation) {
        val uid = scope.uid.value ?: return@withContext ImportRead.Failed(DataError.NotSignedIn)
        when (val result = ImportFile.read(text, uid)) {
            ImportFileResult.NotABackup -> ImportRead.NotABackup
            is ImportFileResult.Stopped -> ImportRead.Stopped(result.report)
            is ImportFileResult.Ready -> {
                val stored = serverState(result.documents).getOrElse { return@withContext ImportRead.Failed(it.asDataError()) }
                val replaced = result.documents.count { document -> stored[document.path]?.let { !landed(it, document.data) } == true }
                ImportRead.Ready(ImportPlan(uid, source, result.format, result.documents, result.counts, result.report.warnings, replaced))
            }
        }
    }

    /**
     * Serverns läge för [documents] på id – **efter** att enhetens köade skrivningar nått servern
     * (`awaitPendingWrites`, högst en minut, annars `Offline`), så att importen aldrig jämför med eller skriver över en
     * lokal ändring som ännu inte synkats.
     */
    private suspend fun serverState(documents: List<ExportFormat.Document>) = fromServer {
        this.documents.awaitPendingWrites()
        this.documents.documents(documents.map { it.path })
    }

    /**
     * Skriver [plan] efter bekräftelsen. Läser först läget på servern på id (det som redan är lika räknas som klart), skriver
     * resten i batchar och läser tillbaka det skrivna: [LegacyWriteOutcome.Verified] när allt finns, annars
     * [LegacyWriteOutcome.Mismatch] eller [LegacyWriteOutcome.Failed] (felkod som flytten, aldrig innehåll). [onProgress]
     * får antal klara per entitet efter varje batch.
     */
    suspend fun write(plan: ImportPlan, onProgress: (Map<String, Int>) -> Unit = {}): LegacyWriteOutcome = withContext(computation) {
        pause.during { writePaused(plan, onProgress) }
    }

    private suspend fun writePaused(plan: ImportPlan, onProgress: (Map<String, Int>) -> Unit): LegacyWriteOutcome {
        val tally = Tally(plan.counts)
        fun failed(error: DataError, batch: Int? = null) =
            LegacyWriteOutcome.Failed(plan.counts, tally.doneSoFar(), error, batch, LegacyMigrationUseCase.codeOf(error))
        if (scope.uid.value != plan.uid) return failed(DataError.NotSignedIn)
        scope.writeBlocker()?.let { return failed(it) }
        // Dokumenten är i appens format; ett konto i ett äldre format måste lyftas med migrate.mjs först.
        if (scope.currentVersion() != Schema.CURRENT_VERSION) return failed(DataError.UpdateRequired)

        val stored = serverState(plan.documents).getOrElse { return failed(it.asDataError()) }
        val pending = plan.documents.filter { document ->
            val same = stored[document.path]?.let { landed(it, document.data) } == true
            if (same) tally.after.add(CollectionNames.collectionOf(document.path))
            !same
        }
        onProgress(tally.progress())

        MigrationBatches.plan(pending).forEachIndexed { index, batch ->
            writer.writeBatch(batch).onFailure { return failed(it.asDataError(), index) }
            tally.written.addAll(batch.map { CollectionNames.collectionOf(it.path) })
            onProgress(tally.progress())
        }

        val afterWrite = if (pending.isEmpty()) emptyMap() else fromServer { documents.documents(pending.map { it.path }) }.getOrElse { return failed(it.asDataError()) }
        for (document in pending) {
            val collection = CollectionNames.collectionOf(document.path)
            if (afterWrite[document.path]?.let { landed(it, document.data) } == true) tally.afterWritten.add(collection) else tally.mismatched.add(collection)
        }
        return if (tally.mismatched.isEmpty()) {
            LegacyWriteOutcome.Verified(plan.counts, tally.after())
        } else {
            LegacyWriteOutcome.Mismatch(plan.counts, tally.after(), tally.mismatched())
        }
    }
}
