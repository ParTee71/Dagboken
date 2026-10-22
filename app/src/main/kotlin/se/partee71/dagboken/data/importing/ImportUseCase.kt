package se.partee71.dagboken.data.importing

import android.net.Uri
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import se.partee71.dagboken.core.importing.ImportBatches
import se.partee71.dagboken.core.importing.ImportFile
import se.partee71.dagboken.core.importing.ImportFileResult
import se.partee71.dagboken.core.importing.Problem
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.UserFile
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.asDataError
import se.partee71.dagboken.data.common.currentVersion
import se.partee71.dagboken.data.common.readOrNull
import se.partee71.dagboken.data.common.writeBlocker
import se.partee71.dagboken.data.firestore.RawDocumentWriter
import se.partee71.dagboken.data.firestore.RawDocuments
import se.partee71.dagboken.di.DefaultDispatcher

/** Vad läsningen av en importfil gav (BCK-6) – det granskningen visar. Aldrig innehåll. */
sealed interface ImportRead {
    /** Klart att granska: antal per typ och dokumenten att skriva. */
    data class Ready(val plan: ImportPlan) : ImportRead

    /** Filen stoppades: [problems] med alla fel – ingenting skrivs. */
    data class Stopped(val problems: List<Problem>) : ImportRead

    /** Filen är ingen export från Dagboken 4. */
    data object NotABackup : ImportRead

    /** Läsningen gick inte: [error] för kontot och servern, `null` när filen på enheten inte gick att öppna. */
    data class Failed(val error: DataError?) : ImportRead
}

/**
 * Det som ska skrivas till kontot [uid]: [documents] utan `users/{uid}` och "före" per samling ([counts]);
 * [schemaVersion] = filens dataformat.
 */
data class ImportPlan(
    val uid: String,
    val schemaVersion: Int,
    val documents: List<ExportFormat.Document>,
    val counts: Map<String, Int>,
    /** Dokument som redan finns i kontot med samma id men andra värden – de som ersätts (granskningens notis). */
    val replaced: Int = 0
) {
    val total: Int get() = documents.size
}

/**
 * Hur skrivningen gick. [before] är planens antal per samling; [after] antal dokument som är lika med filens på servern
 * och verifierade.
 */
sealed interface ImportWriteOutcome {
    val before: Map<String, Int>
    val after: Map<String, Int>

    /** Varje dokument är verifierat lika på servern – importen är klar. */
    data class Verified(override val before: Map<String, Int>, override val after: Map<String, Int>) : ImportWriteOutcome

    /** Något skrivet dokument saknas eller skiljer sig på servern ([mismatched] per samling): "Försök igen" skriver om det. */
    data class Mismatch(override val before: Map<String, Int>, override val after: Map<String, Int>, val mismatched: Map<String, Int>) : ImportWriteOutcome

    /**
     * Skrivningen stannade: [error] (texten via `toMessage()`) och felkoden [code] ([codeOf]) för [batch] (index, `null`
     * utanför batcharna) – aldrig innehåll. [after] är det som var klart när det stannade (verifierat lika och skrivet med
     * kvitto). "Försök igen" kör om utan dubbletter.
     */
    data class Failed(
        override val before: Map<String, Int>,
        override val after: Map<String, Int>,
        val error: DataError,
        val batch: Int?,
        val code: String
    ) : ImportWriteOutcome
}

/**
 * Importen (BCK-6): en 4.x-export (appens egen, BCK-13, eller veckobackupen) läses av `ImportFile` i `:core` och skrivs
 * efter användarens bekräftelse i `ImportBatches` form (≤ 500 skrivningar, ≤ 20 episoder, episoden med sina
 * incheckningar) med serverns kvitto per batch och verifieras sedan per id. **Samma id ersätts** (DAT-13, inga
 * dubbletter): dokumentets fält skrivs med merge; fält som bara finns på servern står kvar – importen tar aldrig bort
 * något. Ett dokument som redan är lika skrivs inte. Ett stopp i filen skriver ingenting; ett fel i en batch stannar och
 * kan göras om utan dubbletter. Ingen markör och ingen liggare: importen kan alltid köras igen och har inget att avbryta.
 * Loggar ingenting (NFR-13).
 */
class ImportUseCase @Inject constructor(
    private val files: UserFile,
    private val writer: RawDocumentWriter,
    private val documents: RawDocuments,
    private val scope: UserScope,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) {
    /**
     * Filen användaren valt i dokumentväljaren (SAF) genom `ImportFile`; för en giltig fil läses sedan kontots läge på id
     * från servern (efter enhetens köade skrivningar) för att räkna hur många dokument som ersätts. Utan nät
     * [ImportRead.Failed] med `Offline`. Ingenting skrivs.
     */
    suspend fun readFile(uri: Uri): ImportRead {
        val text = readOrNull { files.read(uri) } ?: return ImportRead.Failed(null)
        return withContext(computation) {
            val uid = scope.uid.value ?: return@withContext ImportRead.Failed(DataError.NotSignedIn)
            when (val result = ImportFile.read(text, uid)) {
                ImportFileResult.NotABackup -> ImportRead.NotABackup

                is ImportFileResult.Stopped -> ImportRead.Stopped(result.problems)

                is ImportFileResult.Ready -> {
                    val stored = serverState(result.documents).getOrElse { return@withContext ImportRead.Failed(it.asDataError()) }
                    val replaced = result.documents.count { document -> stored[document.path]?.let { !landed(it, document.data) } == true }
                    ImportRead.Ready(ImportPlan(uid, result.schemaVersion, result.documents, result.counts, replaced))
                }
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
     * resten i batchar och läser tillbaka det skrivna: [ImportWriteOutcome.Verified] när allt finns, annars
     * [ImportWriteOutcome.Mismatch] eller [ImportWriteOutcome.Failed] (felkod, aldrig innehåll). [onProgress] får antal
     * klara per samling efter varje batch.
     */
    suspend fun write(plan: ImportPlan, onProgress: (Map<String, Int>) -> Unit = {}): ImportWriteOutcome = withContext(computation) { writeChecked(plan, onProgress) }

    private suspend fun writeChecked(plan: ImportPlan, onProgress: (Map<String, Int>) -> Unit): ImportWriteOutcome {
        val tally = Tally(plan.counts)
        fun failed(error: DataError, batch: Int? = null) = ImportWriteOutcome.Failed(plan.counts, tally.doneSoFar(), error, batch, codeOf(error))
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

        ImportBatches.plan(pending).forEachIndexed { index, batch ->
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
            ImportWriteOutcome.Verified(plan.counts, tally.after())
        } else {
            ImportWriteOutcome.Mismatch(plan.counts, tally.after(), tally.mismatched())
        }
    }
}
