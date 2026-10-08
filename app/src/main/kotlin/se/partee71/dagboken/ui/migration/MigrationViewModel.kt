package se.partee71.dagboken.ui.migration

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.legacy.Problem
import se.partee71.dagboken.core.legacy.Warning
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.legacy.AccountCheck
import se.partee71.dagboken.data.legacy.CopyFailure
import se.partee71.dagboken.data.legacy.CopyRecord
import se.partee71.dagboken.data.legacy.CopyResult
import se.partee71.dagboken.data.legacy.CopyStatus
import se.partee71.dagboken.data.legacy.LegacyAbortOutcome
import se.partee71.dagboken.data.legacy.LegacyMigrationPlan
import se.partee71.dagboken.data.legacy.LegacyMigrationUseCase
import se.partee71.dagboken.data.legacy.LegacyRead
import se.partee71.dagboken.data.legacy.LegacyWriteOutcome
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.failureOrNull

/** Kopians läge i granskningen (OMB-8) – flytten kan bara starta med [Saved]. */
sealed interface CopyStep {
    /** Ingen kopia: "Spara en kopia" (Mig-Kopia-Saknas). */
    data object Missing : CopyStep

    /** Kopian skrivs och kontrolleras (Mig-Kopia-Kontrollerar). */
    data object Checking : CopyStep

    /** Sparad och kontrollerad för Room-filen som den ser ut nu (Mig-Kopia-Klar). */
    data class Saved(val fileName: String?, val savedAt: LocalDateTime, val total: Int) : CopyStep

    /** 3.x-datan har ändrats sedan kopian från [savedOn] (Mig-Kopia-Inaktuell): en ny kopia krävs. */
    data class Stale(val savedOn: LocalDate) : CopyStep

    /** Kopian blev inte klar av skälet [reason] (Mig-Kopia-Fel): "Spara igen". */
    data class Failed(val reason: CopyFailure) : CopyStep
}

/** En rad i en stopprapport (OMB-3, OMB-7): samlingen, fältet och skälet med antal – aldrig innehåll. */
data class ReportLine(val collection: String, val field: String, val reason: String, val count: Int)

/** Ett läge per tavla i mockupen (avsnitt 15 och 15b). Bara antal, aldrig innehåll. */
sealed interface MigrationStage {
    /** Startkontrollen (NAV-6) pågår – en kort stund vid start. */
    data object Checking : MigrationStage

    /** Ingen migrering nu: flikarna visas. [openImport] = användaren valde "Importera backup" (OMB-5). */
    data class Closed(val openImport: Boolean = false) : MigrationStage

    /** Startkontrollen gick inte – utan nät "Ingen anslutning" (Mig-Offline), annars felet; "Försök igen" och "Inte nu". */
    data class CheckFailed(val error: DataError) : MigrationStage

    /** 3.x-filerna läses (Mig-Laser). */
    data object Reading : MigrationStage

    /**
     * Granskningen före flytten (Mig-Kopia-*): kopian, kontot, antal per entitet och varningar; att påminnelserna pausas
     * under flytten visas alltid. [started] = flytten har börjat (något är skrivet, OMB-7): "Inte nu" finns inte längre,
     * bara "Avbryt flytten".
     */
    data class Review(
        val counts: Map<String, Int>,
        val accountEmail: String?,
        /** Kontot mot 3.x-sessionen; [AccountCheck.UNKNOWN] kräver att [accountConfirmed] kryssas i. */
        val accountCheck: AccountCheck,
        val accountConfirmed: Boolean = false,
        val warnings: List<String>,
        val copy: CopyStep,
        /** Förslaget i filväljaren: `dagboken-3x-kopia-<datum>.json`. */
        val copyFileName: String,
        val started: Boolean = false,
    ) : MigrationStage {
        /**
         * Spärren (OMB-8): "Flytta till mitt konto" är aktiv först med en kontrollerad kopia – och, när 3.x-kontot är
         * okänt, när användaren uttryckligen bekräftat kontot.
         */
        val canMove: Boolean get() = copy is CopyStep.Saved && (accountCheck != AccountCheck.UNKNOWN || accountConfirmed)
    }

    /**
     * Skrivningen pågår (Mig-Skriver): före → klart hittills per samling (lika, behållna, raderade i 4.0 och skrivna);
     * [done] = `null` medan läget på servern läses, före första framsteget.
     */
    data class Writing(val before: Map<String, Int>, val done: Map<String, Int>?) : MigrationStage

    /**
     * Allt skrivet och kontrollerat mot servern (Mig-Klar). [existing] = fanns före flytten med andra värden, bara det
     * saknade ifyllt (glest, OMB-7); före = efter + existing. [confirming] = bekräftelsen skrivs. Vägarna ut: bekräfta
     * eller avbryt (det flyttade tas bort).
     */
    data class Written(
        val before: Map<String, Int>,
        val after: Map<String, Int>,
        val existing: Map<String, Int> = emptyMap(),
        val confirming: Boolean = false,
    ) : MigrationStage

    /** Kontrollen mot servern stämde inte (Mig-Avvikelse): [mismatched] per samling; [accounted] = efter + existing. "Försök igen" eller avbryt. */
    data class Mismatch(val before: Map<String, Int>, val accounted: Map<String, Int>, val mismatched: Map<String, Int>) : MigrationStage

    /**
     * Skrivningen stannade med [error] och felkoden [code] för [batch] – "Försök igen" kör om (OMB-7). [started] = något
     * är skrivet: då "Avbryt flytten" i stället för "Inte nu".
     */
    data class WriteFailed(val error: DataError, val batch: Int?, val code: String, val started: Boolean = true) : MigrationStage

    /** "Avbryt flytten" pågår (Mig-Avbryter): det migreringen skrev tas bort från kontot. */
    data object Aborting : MigrationStage

    /** Konverteraren stoppade (Mig-Stopp): rapporten, ingenting skrivet. */
    data class Stopped(val report: List<ReportLine>) : MigrationStage

    /** Room-filen är från en äldre 3.x (Mig-Version). */
    data class WrongVersion(val found: Int) : MigrationStage

    /** 3.x-filerna gick inte att läsa ([error] = `null`) eller ingen var inloggad. */
    data class ReadFailed(val error: DataError?) : MigrationStage
}

/** [failure] = bekräftelsen misslyckades – visas som meddelande, knappen kan tryckas igen. */
data class MigrationUiState(val stage: MigrationStage = MigrationStage.Checking, val failure: Failure? = null)

sealed interface MigrationEvent {
    /** "Försök igen" – gör om det steg som inte gick: kontrollen, läsningen eller skrivningen. */
    data object Retry : MigrationEvent

    /** "Inte nu" och "Börja tomt": in i appen; migreringen erbjuds igen vid nästa start (NAV-6). Gör ingenting när flytten börjat. */
    data object NotNow : MigrationEvent

    /** "Avbryt flytten", bekräftad i dialogen: det migreringen skrev tas bort, sedan granskningen igen (OMB-7). */
    data object Abort : MigrationEvent

    /** "Importera backup" (OMB-5): in i appen till Export och import. */
    data object ImportBackup : MigrationEvent

    /** Kryssrutan "Ja, flytta till …" när 3.x-kontot är okänt. */
    data class ConfirmAccount(val confirmed: Boolean) : MigrationEvent

    /** Filväljaren gav platsen för kopian (OMB-8). */
    data class CopyChosen(val uri: Uri) : MigrationEvent

    /** "Flytta till mitt konto" – bara med kontrollerad kopia. */
    data object Move : MigrationEvent

    /** "Bekräfta och öppna Dagboken". */
    data object Confirm : MigrationEvent

    data object ErrorShown : MigrationEvent
}

/**
 * Migreringen på enheten (OMB-2, OMB-7, OMB-8, NAV-6) från startkontrollen till bekräftelsen, ett läge i taget.
 * Planen hålls här och visas bara som antal; all logik ligger i [LegacyMigrationUseCase]. Loggar ingenting (NFR-13).
 */
@HiltViewModel
class MigrationViewModel @Inject constructor(
    private val migration: LegacyMigrationUseCase,
    private val clock: Clock,
    private val zone: Provider<TimeZone>,
) : ViewModel() {
    private val _state = MutableStateFlow(MigrationUiState())
    val state: StateFlow<MigrationUiState> = _state.asStateFlow()

    private var plan: LegacyMigrationPlan? = null
    private var outcome: LegacyWriteOutcome.Verified? = null
    private var job: Job? = null
    private var accountConfirmed = false

    init {
        check()
    }

    fun onEvent(event: MigrationEvent) {
        when (event) {
            MigrationEvent.Retry -> retry()
            MigrationEvent.NotNow -> close(openImport = false)
            MigrationEvent.ImportBackup -> close(openImport = true)
            is MigrationEvent.ConfirmAccount -> confirmAccount(event.confirmed)
            is MigrationEvent.CopyChosen -> saveCopy(event.uri)
            MigrationEvent.Move -> move()
            MigrationEvent.Confirm -> confirm()
            MigrationEvent.Abort -> abort()
            MigrationEvent.ErrorShown -> _state.update { it.copy(failure = null) }
        }
    }

    private val stage: MigrationStage get() = _state.value.stage

    private fun show(stage: MigrationStage) = _state.update { it.copy(stage = stage) }

    /** Ett steg i taget: ett nytt startas inte medan ett annat arbetar. */
    private fun step(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch { block() }
    }

    private fun check() = step {
        show(MigrationStage.Checking)
        migration.isPending().fold(
            onSuccess = { pending -> if (pending) read() else leaveTo(openImport = false) },
            onFailure = { show(MigrationStage.CheckFailed(it as? DataError ?: DataError.Unknown)) },
        )
    }

    private suspend fun read() {
        show(MigrationStage.Reading)
        when (val read = migration.read()) {
            is LegacyRead.Ready -> review(read.plan)
            is LegacyRead.Stopped -> show(MigrationStage.Stopped(reportOf(read.report.problems)))
            is LegacyRead.WrongVersion -> show(MigrationStage.WrongVersion(read.found))
            // Filen försvann mellan kontrollen och läsningen: inget att flytta.
            LegacyRead.NoDatabase -> leaveTo(openImport = false)
            LegacyRead.Unreadable -> show(MigrationStage.ReadFailed(null))
            is LegacyRead.Failed -> show(MigrationStage.ReadFailed(read.error))
        }
    }

    private suspend fun review(plan: LegacyMigrationPlan, copy: CopyStep = copyStep(plan.copy)) {
        this.plan = plan
        // Bekräftelsen av kontot gäller tills skärmen lämnas, även när granskningen byggs om efter kopian.
        val confirmed = (stage as? MigrationStage.Review)?.accountConfirmed ?: accountConfirmed
        show(
            MigrationStage.Review(
                counts = plan.counts,
                accountEmail = plan.accountEmail,
                accountCheck = plan.accountCheck,
                accountConfirmed = confirmed,
                warnings = plan.warnings.messages(),
                copy = copy,
                copyFileName = "dagboken-3x-kopia-${clock.now().toLocalDateTime(zone.get()).date}.json",
                started = migration.started(),
            ),
        )
    }

    private fun copyStep(status: CopyStatus): CopyStep = when (status) {
        CopyStatus.Missing -> CopyStep.Missing
        is CopyStatus.Verified -> saved(status.record)
        is CopyStatus.Stale -> CopyStep.Stale(status.record.savedAt.toLocalDateTime(zone.get()).date)
    }

    private fun saved(record: CopyRecord) = CopyStep.Saved(record.fileName, record.savedAt.toLocalDateTime(zone.get()), record.total)

    private fun retry() {
        when (stage) {
            is MigrationStage.CheckFailed -> check()
            is MigrationStage.Stopped, is MigrationStage.ReadFailed -> step { read() }
            is MigrationStage.Mismatch, is MigrationStage.WriteFailed -> plan?.let { step { write(it) } }
            else -> Unit
        }
    }

    private fun close(openImport: Boolean) {
        // Under skrivningen och bekräftelsen finns ingen väg ut – de är korta och kan inte lämnas halvgjorda.
        if (job?.isActive == true && stage !is MigrationStage.Review) return
        // När flytten börjat finns bara bekräfta eller avbryt (OMB-7).
        if (started()) return
        job?.cancel()
        leaveTo(openImport)
    }

    /** Flytten har börjat enligt det visade läget – skärmen släpper då inte in i appen. */
    private fun started(): Boolean = when (val stage = stage) {
        is MigrationStage.Review -> stage.started
        is MigrationStage.WriteFailed -> stage.started
        is MigrationStage.Written, is MigrationStage.Mismatch, MigrationStage.Aborting -> true
        else -> false
    }

    private fun confirmAccount(confirmed: Boolean) {
        val review = stage as? MigrationStage.Review ?: return
        if (review.accountCheck != AccountCheck.UNKNOWN) return
        accountConfirmed = confirmed
        show(review.copy(accountConfirmed = confirmed))
    }

    /** Ut ur migreringen utan bekräftelse: flikarna. Påminnelserna pausas bara medan `write()` körs, så inget behöver släppas här. */
    private fun leaveTo(openImport: Boolean) {
        show(MigrationStage.Closed(openImport))
    }

    private fun saveCopy(uri: Uri) {
        val review = stage as? MigrationStage.Review ?: return
        val plan = plan ?: return
        step {
            show(review.copy(copy = CopyStep.Checking))
            when (val result = migration.saveCopy(plan, uri)) {
                is CopyResult.Verified -> review(result.plan)
                is CopyResult.Failed -> (stage as? MigrationStage.Review)?.let { show(it.copy(copy = CopyStep.Failed(result.reason))) }
            }
        }
    }

    private fun move() {
        val review = stage as? MigrationStage.Review ?: return
        val plan = plan?.takeIf { review.canMove } ?: return
        step { write(plan) }
    }

    private suspend fun write(plan: LegacyMigrationPlan) {
        // Först läses läget på servern; det första framsteget kommer när det är läst.
        show(MigrationStage.Writing(plan.counts, null))
        val result = migration.write(plan) { done -> show(MigrationStage.Writing(plan.counts, done)) }
        when (result) {
            is LegacyWriteOutcome.Verified -> {
                outcome = result
                show(MigrationStage.Written(result.before, result.after, result.existing))
            }
            is LegacyWriteOutcome.Mismatch -> show(MigrationStage.Mismatch(result.before, result.accounted(), result.mismatched))
            // Kopian gäller inte (längre) Room-filen som den ser ut nu: läs om och granska med en ny kopia (OMB-8).
            is LegacyWriteOutcome.Failed -> if (result.code in COPY_CODES) read() else show(MigrationStage.WriteFailed(result.error, result.batch, result.code, migration.started()))
        }
    }

    /** "Avbryt flytten": det migreringen skrev tas bort; sedan granskningen igen (utan "började"), eller felet med läget kvar. */
    private fun abort() {
        val from = stage
        if (!started()) return
        step {
            show(MigrationStage.Aborting)
            when (val result = migration.abort()) {
                is LegacyAbortOutcome.Done -> read()
                is LegacyAbortOutcome.Failed -> _state.update { it.copy(stage = from, failure = Failure(result.error)) }
            }
        }
    }

    private fun confirm() {
        val written = stage as? MigrationStage.Written ?: return
        val plan = plan ?: return
        val outcome = outcome ?: return
        step {
            show(written.copy(confirming = true))
            val result = migration.confirm(plan, outcome)
            if (result.isSuccess) {
                show(MigrationStage.Closed())
            } else {
                _state.update { it.copy(stage = written, failure = result.failureOrNull()) }
            }
        }
    }

    private companion object {
        val COPY_CODES = setOf(LegacyMigrationUseCase.COPY_NOT_VERIFIED, LegacyMigrationUseCase.SOURCE_CHANGED)
    }
}

/** Före = efter + fanns redan (OMB-7): det som är avklarat per samling. */
internal fun accounted(after: Map<String, Int>, vararg extra: Map<String, Int>): Map<String, Int> =
    after.mapValues { (collection, count) -> count + extra.sumOf { it[collection] ?: 0 } }

private fun LegacyWriteOutcome.accounted(): Map<String, Int> = accounted(after, existing)

/** Varningarnas texter, var och en en gång – sökvägen (med dokument-id) visas inte. */
private fun List<Warning>.messages(): List<String> = map { it.message }.distinct()

/** Stopprapporten per samling, fält och skäl med antal; sökvägens id visas inte. */
private fun reportOf(problems: List<Problem>): List<ReportLine> =
    problems.groupingBy { Triple(reportCollection(it.path), it.field, it.reason) }.eachCount()
        .map { (key, count) -> ReportLine(key.first, key.second, key.third, count) }

/** Samlingen i en rapportsökväg: `prescriptions/<id>` → `prescriptions`, `illnessEpisodes/<id>/checkins/<id>` → `checkins`, `options/activity#3` → `options`. */
internal fun reportCollection(path: String): String {
    val parts = path.split('/')
    return if (parts.size >= 2) parts[parts.size - 2] else parts.first().substringBefore('#')
}
