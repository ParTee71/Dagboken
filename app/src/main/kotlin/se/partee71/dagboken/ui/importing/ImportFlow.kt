package se.partee71.dagboken.ui.importing

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.partee71.dagboken.core.importing.Problem
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.importing.ImportPlan
import se.partee71.dagboken.data.importing.ImportRead
import se.partee71.dagboken.data.importing.ImportUseCase
import se.partee71.dagboken.data.importing.ImportWriteOutcome

/** En rad i en stopprapport: samlingen, fältet och skälet med antal – aldrig innehåll. */
data class ReportLine(val collection: String, val field: String, val reason: String, val count: Int)

/**
 * Importens lägen (BCK-6) i Inställningar → Export och import, ett per tavla i mockupen (avsnitt 16). Bara antal,
 * aldrig innehåll.
 */
sealed interface ImportStage {
    /** Raderna "Spara som JSON" och "Importera backup". */
    data object Choose : ImportStage

    /** Filen läses och kontrolleras – ingenting skrivs. */
    data object Reading : ImportStage

    /** Filen är ingen export från Dagboken 4: "Välj en annan fil". */
    data object NotABackup : ImportStage

    /**
     * Läsningen gick inte: [error] för kontot och servern (text via `toMessage()`, utan nät "Ingen anslutning"), `null`
     * när filen inte gick att öppna. "Försök igen" läser samma fil igen; "Välj en annan fil" finns alltid.
     */
    data class ReadFailed(val error: DataError?) : ImportStage

    /**
     * Granskningen före skrivningen: antal per typ ([counts], samlingsnamn i `CollectionNames`-ordning), filens dataformat
     * [schemaVersion] och notisen "poster med samma id ersätts". "Importera" sätter [confirming]: `ConfirmDialog` med
     * "Importera [total] poster?".
     */
    data class Review(
        val schemaVersion: Int,
        val counts: Map<String, Int>,
        /** Bekräftelsedialogen visas. */
        val confirming: Boolean = false,
        /** Poster som redan finns i kontot med samma id men andra värden – de som ersätts (0 = inget ersätts). */
        val replaced: Int = 0
    ) : ImportStage {
        val total: Int get() = counts.values.sum()

        /** En tom fil har inget att importera – "Importera" är inte aktiv. */
        val canImport: Boolean get() = total > 0
    }

    /** Filen stoppades: bara rapporten, ingenting skrivet, knappen "Välj en annan fil". */
    data class Stopped(val report: List<ReportLine>) : ImportStage

    /** Skrivningen pågår; [done] = `null` medan läget på servern läses. */
    data class Writing(val before: Map<String, Int>, val done: Map<String, Int>?) : ImportStage

    /** Allt skrivet och kontrollerat mot servern: importen är klar. */
    data class Done(val before: Map<String, Int>, val after: Map<String, Int>) : ImportStage

    /** Kontrollen mot servern stämde inte: [mismatched] per samling; "Försök igen" skriver om det som saknas. */
    data class Mismatch(val before: Map<String, Int>, val accounted: Map<String, Int>, val mismatched: Map<String, Int>) : ImportStage

    /** Skrivningen stannade med [error] och felkoden [code] för [batch]; "Försök igen" kör om utan dubbletter. */
    data class WriteFailed(val error: DataError, val batch: Int?, val code: String) : ImportStage
}

sealed interface ImportEvent {
    /** Dokumentväljaren (SAF, `OpenDocument`) gav filen – "Importera backup" och "Välj en annan fil". */
    data class FileChosen(val uri: Uri) : ImportEvent

    /** "Importera" i granskningen: bekräftelsedialogen. */
    data object Import : ImportEvent

    /** Dialogens "Importera": skrivningen börjar. */
    data object Confirm : ImportEvent

    /** Dialogens "Avbryt". */
    data object Dismiss : ImportEvent

    /** "Försök igen": läs samma fil igen, eller skriv om efter ett fel eller en avvikelse. */
    data object Retry : ImportEvent

    /** Tillbaka till valen ("Avbryt", "Klar") – inte medan något läses eller skrivs. */
    data object Reset : ImportEvent
}

/**
 * Importens tillståndsmaskin: ägs av en ViewModel och körs i dess [scope]. Planen hålls här och visas bara som antal;
 * all logik ligger i [ImportUseCase]. Ett steg i taget.
 */
class ImportFlow(private val scope: CoroutineScope, private val importer: ImportUseCase) {
    private val _state = MutableStateFlow<ImportStage>(ImportStage.Choose)
    val state: StateFlow<ImportStage> = _state.asStateFlow()

    private var plan: ImportPlan? = null
    private var lastFile: Uri? = null
    private var job: Job? = null

    /** Något läses eller skrivs – ingen väg ut ur skärmen då. */
    val busy: Boolean get() = job?.isActive == true

    fun onEvent(event: ImportEvent) {
        when (event) {
            is ImportEvent.FileChosen -> {
                if (busy) return
                lastFile = event.uri
                step { read(event.uri) }
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
        when (_state.value) {
            is ImportStage.ReadFailed -> lastFile?.let { file -> step { read(file) } }
            is ImportStage.Mismatch, is ImportStage.WriteFailed -> plan?.let { step { write(it) } }
            else -> Unit
        }
    }

    private suspend fun read(file: Uri) {
        plan = null
        _state.value = ImportStage.Reading
        _state.value = when (val read = importer.readFile(file)) {
            is ImportRead.Ready -> {
                plan = read.plan
                ImportStage.Review(read.plan.schemaVersion, read.plan.counts, replaced = read.plan.replaced)
            }

            is ImportRead.Stopped -> ImportStage.Stopped(reportOf(read.problems))

            ImportRead.NotABackup -> ImportStage.NotABackup

            is ImportRead.Failed -> ImportStage.ReadFailed(read.error)
        }
    }

    private suspend fun write(plan: ImportPlan) {
        _state.value = ImportStage.Writing(plan.counts, null)
        val result = importer.write(plan) { done -> _state.value = ImportStage.Writing(plan.counts, done) }
        _state.value = when (result) {
            is ImportWriteOutcome.Verified -> ImportStage.Done(result.before, result.after)
            is ImportWriteOutcome.Mismatch -> ImportStage.Mismatch(result.before, result.after, result.mismatched)
            is ImportWriteOutcome.Failed -> ImportStage.WriteFailed(result.error, result.batch, result.code)
        }
    }
}

/**
 * Stopprapporten per typ – samling, fält och skäl med antal; sökvägens id visas inte, och listindex slås ihop
 * (`symptom[0]`, `symptom[3]` → `symptom[…]`), så att varje typ av fynd står på en rad och alla typer syns.
 */
internal fun reportOf(problems: List<Problem>): List<ReportLine> = problems.groupingBy { Triple(reportCollection(it.path), it.field.replace(LIST_INDEX, "[…]"), it.reason) }.eachCount()
    .map { (key, count) -> ReportLine(key.first, key.second, key.third, count) }

private val LIST_INDEX = Regex("""\[\d+]""")

/** Samlingen i en rapportsökväg: `prescriptions/<id>` → `prescriptions`, `illnessEpisodes/<id>/checkins/<id>` → `checkins`, `options/activity#3` → `options`. */
internal fun reportCollection(path: String): String {
    val parts = path.split('/')
    return if (parts.size >= 2) parts[parts.size - 2] else parts.first().substringBefore('#')
}
