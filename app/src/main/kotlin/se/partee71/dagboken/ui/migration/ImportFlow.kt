package se.partee71.dagboken.ui.migration

import android.app.PendingIntent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.partee71.dagboken.core.legacy.ImportFormat
import se.partee71.dagboken.core.model.LegacySource
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.legacy.ImportPlan
import se.partee71.dagboken.data.legacy.ImportRead
import se.partee71.dagboken.data.legacy.LegacyImportUseCase
import se.partee71.dagboken.data.legacy.LegacyWriteOutcome

/**
 * Importens lägen (BCK-6, BCK-14, OMB-5), ett per tavla i mockupen (avsnitt 16) – **samma** i första starten utan
 * Room-fil ([MigrationStage.Fallback]) och i Inställningar → Export och import. Bara antal, aldrig innehåll.
 */
sealed interface ImportStage {
    /** Valen: "Importera från Google Drive", "Importera från fil" (och i första starten "Börja tomt"). */
    data object Choose : ImportStage

    /** Drive-backupen eller filen läses och kontrolleras – ingenting skrivs. */
    data class Reading(val source: LegacySource) : ImportStage

    /**
     * Drive kräver samtycke till `appDataFolder` (begärs bara här, vid importen): skärmen startar [consent] med
     * `ActivityResultContracts.StartIntentSenderForResult` och skickar [ImportEvent.DriveConsent] med svaret.
     */
    data class DriveConsent(val consent: PendingIntent) : ImportStage

    /** Ingen backup på Drive (notisen): "Importera från fil" – och i första starten "Börja tomt". */
    data object NoDriveBackup : ImportStage

    /** Filen är varken en 3.x-backup eller en 4.0-export: "Välj en annan fil". */
    data object NotABackup : ImportStage

    /**
     * Läsningen gick inte: [error] för Drive och kontot (text via `toMessage()`, utan nät "Ingen anslutning"), `null` när
     * filen inte gick att öppna. "Försök igen" läser samma källa igen; "Välj en annan fil" finns alltid.
     */
    data class ReadFailed(val source: LegacySource, val error: DataError?) : ImportStage

    /**
     * Granskningen före skrivningen: antal per typ ([counts], samlingsnamn i `CollectionNames`-ordning), varningarna och
     * notisen "poster med samma id ersätts". "Importera" sätter [confirming]: `ConfirmDialog` med "Importera [total] poster?".
     */
    data class Review(
        val source: LegacySource,
        val format: ImportFormat,
        val counts: Map<String, Int>,
        val warnings: List<String>,
        /** Bekräftelsedialogen visas. */
        val confirming: Boolean = false,
        /** Poster som redan finns i kontot med samma id men andra värden – de som ersätts (0 = inget ersätts). */
        val replaced: Int = 0,
    ) : ImportStage {
        val total: Int get() = counts.values.sum()

        /** En tom fil har inget att importera – "Importera" är inte aktiv. */
        val canImport: Boolean get() = total > 0
    }

    /** Filen stoppades (OMB-3): bara rapporten, ingenting skrivet, knappen "Välj en annan fil". */
    data class Stopped(val report: List<ReportLine>) : ImportStage

    /** Skrivningen pågår – samma läge som flyttens (Mig-Skriver); [done] = `null` medan läget på servern läses. */
    data class Writing(val before: Map<String, Int>, val done: Map<String, Int>?) : ImportStage

    /** Allt skrivet och kontrollerat mot servern – samma läge som flyttens (Mig-Klar), utan bekräftelse: importen är klar. */
    data class Done(val before: Map<String, Int>, val after: Map<String, Int>) : ImportStage

    /** Kontrollen mot servern stämde inte: [mismatched] per samling; "Försök igen" skriver om det som saknas. */
    data class Mismatch(val before: Map<String, Int>, val accounted: Map<String, Int>, val mismatched: Map<String, Int>) : ImportStage

    /** Skrivningen stannade med [error] och felkoden [code] för [batch]; "Försök igen" kör om utan dubbletter. */
    data class WriteFailed(val error: DataError, val batch: Int?, val code: String) : ImportStage
}

sealed interface ImportEvent {
    /** "Importera från Google Drive". */
    data object FromDrive : ImportEvent

    /** Dokumentväljaren (SAF, `OpenDocument`) gav filen – "Importera från fil" och "Välj en annan fil". */
    data class FileChosen(val uri: Uri) : ImportEvent

    /** Svaret på Drive-samtycket: ja läser Drive igen, nej tillbaka till valen. */
    data class DriveConsent(val granted: Boolean) : ImportEvent

    /** "Importera" i granskningen: bekräftelsedialogen. */
    data object Import : ImportEvent

    /** Dialogens "Importera": skrivningen börjar. */
    data object Confirm : ImportEvent

    /** Dialogens "Avbryt". */
    data object Dismiss : ImportEvent

    /** "Försök igen": läs samma källa igen, eller skriv om efter ett fel eller en avvikelse. */
    data object Retry : ImportEvent

    /** Tillbaka till valen ("Avbryt", "Klar") – inte medan något läses eller skrivs. */
    data object Reset : ImportEvent
}

/**
 * Importens tillståndsmaskin, **en** gång för båda ingångarna (regel 4): ägs av en ViewModel och körs i dess [scope].
 * Planen hålls här och visas bara som antal; all logik ligger i [LegacyImportUseCase]. Ett steg i taget.
 */
class ImportFlow(private val scope: CoroutineScope, private val importer: LegacyImportUseCase) {
    private val _state = MutableStateFlow<ImportStage>(ImportStage.Choose)
    val state: StateFlow<ImportStage> = _state.asStateFlow()

    private var plan: ImportPlan? = null
    private var lastFile: Uri? = null
    private var job: Job? = null

    /** Något läses eller skrivs – ingen väg ut ur skärmen då. */
    val busy: Boolean get() = job?.isActive == true

    fun onEvent(event: ImportEvent) {
        when (event) {
            ImportEvent.FromDrive -> step { read(LegacySource.DRIVE) }
            is ImportEvent.FileChosen -> {
                lastFile = event.uri
                step { read(LegacySource.JSON) }
            }
            is ImportEvent.DriveConsent -> if (_state.value is ImportStage.DriveConsent) {
                if (event.granted) step { read(LegacySource.DRIVE) } else _state.value = ImportStage.Choose
            }
            ImportEvent.Import -> review { if (it.canImport) it.copy(confirming = true) else it }
            ImportEvent.Dismiss -> review { it.copy(confirming = false) }
            ImportEvent.Confirm -> if ((_state.value as? ImportStage.Review)?.confirming == true) plan?.let { step { write(it) } }
            ImportEvent.Retry -> retry()
            ImportEvent.Reset -> if (!busy) reset()
        }
    }

    private fun step(block: suspend () -> Unit) {
        if (busy) return
        job = scope.launch { block() }
    }

    private fun review(change: (ImportStage.Review) -> ImportStage.Review) {
        (_state.value as? ImportStage.Review)?.let { _state.value = change(it) }
    }

    private fun reset() {
        plan = null
        _state.value = ImportStage.Choose
    }

    private fun retry() {
        when (val stage = _state.value) {
            is ImportStage.ReadFailed -> if (stage.source == LegacySource.DRIVE || lastFile != null) step { read(stage.source) }
            is ImportStage.Mismatch, is ImportStage.WriteFailed -> plan?.let { step { write(it) } }
            else -> Unit
        }
    }

    private suspend fun read(source: LegacySource) {
        plan = null
        _state.value = ImportStage.Reading(source)
        val read = when (source) {
            LegacySource.DRIVE -> importer.readDrive()
            else -> importer.readFile(lastFile ?: return reset())
        }
        _state.value = when (read) {
            is ImportRead.Ready -> {
                plan = read.plan
                ImportStage.Review(read.plan.source, read.plan.format, read.plan.counts, read.plan.warnings.messages(), replaced = read.plan.replaced)
            }
            is ImportRead.Stopped -> ImportStage.Stopped(reportOf(read.report.problems))
            ImportRead.NotABackup -> ImportStage.NotABackup
            ImportRead.NoDriveBackup -> ImportStage.NoDriveBackup
            is ImportRead.NeedsDriveConsent -> ImportStage.DriveConsent(read.consent)
            is ImportRead.Failed -> ImportStage.ReadFailed(source, read.error)
        }
    }

    private suspend fun write(plan: ImportPlan) {
        _state.value = ImportStage.Writing(plan.counts, null)
        val result = importer.write(plan) { done -> _state.value = ImportStage.Writing(plan.counts, done) }
        _state.value = when (result) {
            is LegacyWriteOutcome.Verified -> ImportStage.Done(result.before, result.after)
            is LegacyWriteOutcome.Mismatch -> ImportStage.Mismatch(result.before, accounted(result.after, result.existing), result.mismatched)
            is LegacyWriteOutcome.Failed -> ImportStage.WriteFailed(result.error, result.batch, result.code)
        }
    }
}
