package se.partee71.dagboken.data.legacy

import android.net.Uri
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import se.partee71.dagboken.core.legacy.BackupJson
import se.partee71.dagboken.core.legacy.BackupJsonConverter
import se.partee71.dagboken.core.legacy.ConversionReport
import se.partee71.dagboken.core.legacy.ConversionResult
import se.partee71.dagboken.core.legacy.LegacyRoomAssembler
import se.partee71.dagboken.core.legacy.LegacyRoomSchema
import se.partee71.dagboken.core.legacy.MigrationBatches
import se.partee71.dagboken.core.legacy.Warning
import se.partee71.dagboken.core.model.LegacyMigration
import se.partee71.dagboken.core.model.LegacySource
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.LegacyMigrationCodec
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.data.auth.AuthRepository
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.currentVersion
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.data.common.writeBlocker
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.firestore.RawDocumentWriter
import se.partee71.dagboken.data.firestore.RawDocuments
import se.partee71.dagboken.data.firestore.firestoreError
import se.partee71.dagboken.di.DefaultDispatcher
import se.partee71.dagboken.di.IoDispatcher

/** Vad läsningen av 3.x-filerna gav (steget "läser" på migreringsskärmen, OMB-2). */
sealed interface LegacyRead {
    /** Allt konverterat och klart att granska: antal före per entitet, varningar och kopian att spara. */
    data class Ready(val plan: LegacyMigrationPlan) : LegacyRead

    /** Konverteraren stoppade (OMB-3): rapporten – antal och fel per fält, aldrig innehåll – och ingenting skrivs. */
    data class Stopped(val report: ConversionReport, val warnings: List<Warning>) : LegacyRead

    /** Room-filens `user_version` är inte 11 – en äldre 3.x; fallbacken (OMB-5) gäller. */
    data class WrongVersion(val found: Int) : LegacyRead

    /** Ingen Room-fil på enheten. */
    data object NoDatabase : LegacyRead

    /** Room- eller DataStore-filen gick inte att läsa (trasig eller låst) – inget mer sägs om varför. */
    data object Unreadable : LegacyRead

    /** Ingen inloggad användare. */
    data class Failed(val error: DataError) : LegacyRead
}

/** Läget för kopian av 3.x-datan (OMB-8) – flytten kan bara starta med [Verified] för exakt den här Room-filen. */
sealed interface CopyStatus {
    /** Ingen kopia har sparats: kräver handling innan något kan skrivas. */
    data object Missing : CopyStatus

    /** Sparad och verifierad för Room-filen som den ser ut nu – visa datum, antal och filnamn. */
    data class Verified(val record: CopyRecord) : CopyStatus

    /** En kopia finns men Room-filen har ändrats efteråt (t.ex. 3.x använd igen) – en ny kopia krävs. */
    data class Stale(val record: CopyRecord) : CopyStatus
}

/** Varför en kopia inte blev klar; visas med "Spara igen". Aldrig innehåll. */
enum class CopyFailure {
    /** Filen gick inte att skriva (platsen otillgänglig). */
    WRITE_FAILED,

    /** Filen gick inte att läsa tillbaka eller parsas som en 3.x-backup. */
    UNREADABLE,

    /** Antalet poster per entitet i filen skiljer sig från det som lästes ur Room. */
    COUNT_MISMATCH,

    /** Konverteraren stoppar på filen (rapporten följer med). */
    CONVERTER_STOPPED,

    /** Filen ger inte exakt samma dokument som Room-raderna. */
    DOCUMENTS_DIFFER,

    /** Kopians läge gick inte att spara på telefonen (DataStore) – kopian räknas inte som verifierad. */
    STATE_FAILED,
}

/** Utfallet av [LegacyMigrationUseCase.saveCopy]. */
sealed interface CopyResult {
    /** Kopian är klar; planen bär [CopyStatus.Verified]. */
    data class Verified(val plan: LegacyMigrationPlan) : CopyResult

    data class Failed(val reason: CopyFailure, val report: ConversionReport? = null) : CopyResult
}

/** Kontot i bekräftelsesteget mot 3.x-sessionen (Firebase Auths konto vid första starten av 4.0). */
enum class AccountCheck {
    /** Samma konto som 3.x använde – e-posten visas som bekräftelse. */
    SAME,

    /** Ett annat konto än 3.x använde – varna tydligt innan flytten. */
    DIFFERENT,

    /** 3.x-sessionen är okänd (3.x kördes utan konto, eller den hann inte fångas): e-posten måste bekräftas uttryckligen. */
    UNKNOWN,
}

/**
 * Det som ska skrivas för [uid]: konverterarens dokument (utan `users/{uid}`, som finns sedan inloggningen),
 * "före" per entitet = rapportens antal, varningarna från läsaren och konverteraren (utan innehåll), 3.x-datan som
 * [backup] för kopian och kopians läge ([copy], OMB-8) – [LegacyMigrationUseCase.write] kräver [CopyStatus.Verified]
 * för Room-filen med just [fingerprint]. Påminnelserna pausas alltid medan [LegacyMigrationUseCase.write] körs.
 */
data class LegacyMigrationPlan(
    val uid: String,
    /** Den inloggades e-post, för bekräftelsesteget ("migreras till …"). */
    val accountEmail: String?,
    /** 3.x-sessionens konto; `null` när det är okänt eller inget. */
    val previousUid: String?,
    /** Hur kontot förhåller sig till 3.x-sessionen. */
    val accountCheck: AccountCheck,
    val backup: BackupJson,
    /** Room-filens kontrollsumma när den lästes. */
    val fingerprint: String,
    /** Antal rader per Room-tabell – det kopian ska ge tillbaka. */
    val tableCounts: Map<String, Int>,
    val documents: List<ExportFormat.Document>,
    /** Antal per entitet (samlingsnamn) i `CollectionNames`-ordning – det som visas som "före". */
    val counts: Map<String, Int>,
    val warnings: List<Warning>,
    val report: ConversionReport,
    val copy: CopyStatus = CopyStatus.Missing,
) {
    /** Inloggad med ett annat konto än 3.x-sessionen – visas som varning i bekräftelsesteget. */
    val accountMismatch: Boolean get() = accountCheck == AccountCheck.DIFFERENT

    val total: Int get() = documents.size
}

/**
 * Hur skrivningen gick. [before] är rapportens antal; [after] antal dokument som är lika med konverterarens på servern
 * och verifierade; [existing] – dokument som fanns före första skrivningen med andra värden (bara saknade fält fylldes,
 * bara entiteter > 0). Före = efter + existing. Det migreringen själv skrivit skrivs om vid en omkörning tills det stämmer
 * (OMB-7) – det har inga egna hinkar.
 */
sealed interface LegacyWriteOutcome {
    val before: Map<String, Int>
    val after: Map<String, Int>
    val existing: Map<String, Int>

    /** Varje dokument är verifierat lika eller fanns före flytten. Bekräfta med [LegacyMigrationUseCase.confirm]. */
    data class Verified(
        override val before: Map<String, Int>,
        override val after: Map<String, Int>,
        override val existing: Map<String, Int> = emptyMap(),
    ) : LegacyWriteOutcome

    /** Något skrivet dokument saknas eller skiljer sig på servern ([mismatched] per entitet): "Försök igen" skriver om det. */
    data class Mismatch(
        override val before: Map<String, Int>,
        override val after: Map<String, Int>,
        val mismatched: Map<String, Int>,
        override val existing: Map<String, Int> = emptyMap(),
    ) : LegacyWriteOutcome

    /**
     * Skrivningen stannade: [error] (texten via `toMessage()`, [DataError.QuotaExceeded] har egen) och felkoden
     * [code] (`PERMISSION_DENIED`, `RESOURCE_EXHAUSTED`, `UNAVAILABLE` …, eller en egen: [LegacyMigrationUseCase.COPY_NOT_VERIFIED],
     * [LegacyMigrationUseCase.SOURCE_CHANGED], [LegacyMigrationUseCase.LEDGER_FAILED]) för [batch] (index, `null` utanför
     * batcharna) – aldrig innehåll. [after] är det som var klart när det stannade (verifierat lika och skrivet med
     * kvitto). Ingen markör sätts; "Försök igen" kör om (OMB-7).
     */
    data class Failed(
        override val before: Map<String, Int>,
        override val after: Map<String, Int>,
        val error: DataError,
        val batch: Int?,
        val code: String,
        override val existing: Map<String, Int> = emptyMap(),
    ) : LegacyWriteOutcome
}

/** Hur "Avbryt flytten" gick ([LegacyMigrationUseCase.abort]). */
sealed interface LegacyAbortOutcome {
    /** Allt migreringen skrivit är borta från servern; liggaren är rensad. [deleted] = antal borttagna per entitet. */
    data class Done(val deleted: Map<String, Int>) : LegacyAbortOutcome

    /** Raderingen stannade eller något finns kvar på servern ([remaining] per entitet, tom vid ett fel): "Försök igen". */
    data class Failed(val error: DataError, val code: String, val remaining: Map<String, Int> = emptyMap()) : LegacyAbortOutcome
}

/**
 * Migreringen på enheten (OMB-2, OMB-7, OMB-8, NAV-6) i stegen läs → `LegacyRoomAssembler` → `BackupJsonConverter` →
 * obligatorisk verifierad kopia → läs målens läge på servern → skriv bara det som saknas, i batchar → verifiera varje
 * skrivet dokument mot servern → bekräfta (markör på servern, flagga på enheten) – eller avbryt och ta bort det som
 * skrevs ([abort]). När flytten börjat ([started]) finns ingen annan väg ut. Ingen egen mappning: `:core` gör allt från
 * rader till dokument. Loggar ingenting (NFR-13).
 */
class LegacyMigrationUseCase @Inject constructor(
    private val room: LegacyRoomSource,
    private val preferences: LegacyPreferencesSource,
    private val writer: RawDocumentWriter,
    private val documents: RawDocuments,
    private val state: MigrationDeviceState,
    private val ledger: MigrationLedger,
    private val work: LegacyWork,
    private val copies: LegacyCopyFile,
    private val pause: LegacyMigrationPause,
    private val appVersion: AppVersion,
    private val auth: AuthRepository,
    private val scope: UserScope,
    private val clock: Clock,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val computation: CoroutineDispatcher,
) {
    /**
     * Startkontrollen (NAV-6): Room-filen finns, flaggan för kontot saknas **och** servern har ingen markör
     * `users/{uid}.legacyMigration` – finns den sätts flaggan och ingen migrering erbjuds, oavsett enhetens läge. Har
     * flytten börjat (liggaren har rader, OMB-7) är den alltid väntande – ingen väg in i appen förrän den bekräftats eller
     * avbrutits. En väntande migrering pausar inga påminnelser. Finns Room-filen avbokas 3.x:s backupjobb vid varje start tills det
     * lyckats (flagga per installation, oberoende av kontots flagga); ett fel där fäller aldrig kontrollen. Utan nät `Offline`.
     */
    suspend fun isPending(): Result<Boolean> = suspendRunCatching(::firestoreError) {
        // Utan 3.x-data finns inget att fråga om – aldrig ett fel för de användarna.
        if (!room.exists()) return@suspendRunCatching false
        val uid = scope.uid.value ?: throw DataError.NotSignedIn
        cancelBackupJob()
        if (state.isDone(uid)) return@suspendRunCatching false
        // Flytten har börjat (något är skrivet): skärmen visas tills bekräftelsen eller avbrytandet – utan att fråga servern.
        if (readOrNull { ledger.read(uid).started } == true) return@suspendRunCatching true
        val marker = LegacyMigrationCodec.decode(documents.document(Paths.user(uid))?.get(LegacyMigrationCodec.FIELD))
        if (marker != null) {
            state.markDone(uid)
            return@suspendRunCatching false
        }
        true
    }

    /**
     * Flytten har börjat (OMB-7): liggaren har någon rad – migreringen har skrivit eller tänkt skriva dokument i kontot.
     * Då visas migreringsskärmen tills [confirm] eller [abort] är klar; "Inte nu" finns inte längre. Ett läsfel räknas som
     * inte börjat (nästa [write] stannar ändå med [LEDGER_FAILED]).
     */
    suspend fun started(): Boolean {
        val uid = scope.uid.value ?: return false
        return readOrNull { ledger.read(uid).started } ?: false
    }

    /** 3.x:s backupjobb (`dagboken_daily_backup`) avbokas tills det lyckats; flaggan sätts först då. Fel fångas. */
    private suspend fun cancelBackupJob() {
        if (readOrNull { state.backupJobCancelled() } == true) return
        readOrNull { work.cancelBackupJob() } ?: return
        readOrNull { state.markBackupJobCancelled() }
    }

    /** Läser och konverterar – ingenting skrivs. */
    suspend fun read(): LegacyRead = withContext(dispatcher) {
        val uid = scope.uid.value ?: return@withContext LegacyRead.Failed(DataError.NotSignedIn)
        val tables = when (val stored = readOrNull { room.read() } ?: return@withContext LegacyRead.Unreadable) {
            LegacyRoomRead.Missing -> return@withContext LegacyRead.NoDatabase
            is LegacyRoomRead.WrongVersion -> return@withContext LegacyRead.WrongVersion(stored.found)
            is LegacyRoomRead.Tables -> stored
        }
        val prefs = readOrNull { preferences.read() } ?: return@withContext LegacyRead.Unreadable
        val assembly = LegacyRoomAssembler.assemble(tables.tables, prefs, LegacyRoomAssembler.createdAt(tables.lastModifiedMillis))
        when (val result = BackupJsonConverter.convert(assembly.backup, uid)) {
            is ConversionResult.Stopped -> LegacyRead.Stopped(result.report, assembly.warnings)
            is ConversionResult.Converted -> {
                val session = readOrNull { state.session() }
                LegacyRead.Ready(
                    LegacyMigrationPlan(
                        uid = uid,
                        accountEmail = auth.authState.first()?.email,
                        previousUid = session?.uid,
                        accountCheck = when (session?.uid) {
                            null -> AccountCheck.UNKNOWN
                            uid -> AccountCheck.SAME
                            else -> AccountCheck.DIFFERENT
                        },
                        backup = assembly.backup,
                        fingerprint = tables.fingerprint,
                        tableCounts = LegacyRoomSchema.TABLES.associateWith { tables.tables[it].orEmpty().size },
                        copy = copyStatus(tables.fingerprint),
                        documents = result.documents.filterNot { CollectionNames.collectionOf(it.path) == CollectionNames.USERS },
                        counts = result.report.counts - CollectionNames.USERS,
                        warnings = assembly.warnings + result.report.warnings,
                        report = result.report,
                    ),
                )
            }
        }
    }

    /** Kopians läge för Room-filen med [fingerprint]: den sparade kopian gäller bara exakt den filen. */
    private suspend fun copyStatus(fingerprint: String): CopyStatus {
        val record = readOrNull { state.copy() } ?: return CopyStatus.Missing
        return if (record.fingerprint == fingerprint) CopyStatus.Verified(record) else CopyStatus.Stale(record)
    }

    /**
     * Sparar 3.x-datan som en 3.x-backupfil (läsbar av 3.27.0:s "Välj fil") till [uri] och **verifierar** den (OMB-8):
     * filen läses tillbaka och parsas som 3.x gör (`BackupJson.parse`), antalet per entitet ska vara det som lästes
     * ur Room, konverteraren ska gå igenom utan stopp och ge exakt planens dokument. Först då sparas kopian som
     * verifierad för Room-filen som den ser ut nu; annars [CopyResult.Failed] med skälet. En tidigare kopia glöms
     * innan filen skrivs, så ett misslyckat försök aldrig lämnar ett gammalt "verifierad" kvar.
     */
    suspend fun saveCopy(plan: LegacyMigrationPlan, uri: Uri): CopyResult = withContext(computation) {
        fun failed(reason: CopyFailure, report: ConversionReport? = null) = CopyResult.Failed(reason, report)
        readOrNull { state.clearCopy() } ?: return@withContext failed(CopyFailure.STATE_FAILED)
        val cleared = plan.copy(copy = CopyStatus.Missing)
        val text = BackupJson.encode(cleared.backup)
        readOrNull { copies.write(uri, text) } ?: return@withContext failed(CopyFailure.WRITE_FAILED)
        val parsed = readOrNull { BackupJson.parse(copies.read(uri)) } ?: return@withContext failed(CopyFailure.UNREADABLE)
        if (LegacyRoomAssembler.tableCounts(parsed) != cleared.tableCounts) return@withContext failed(CopyFailure.COUNT_MISMATCH)
        val documents = when (val result = BackupJsonConverter.convert(parsed, cleared.uid)) {
            is ConversionResult.Stopped -> return@withContext failed(CopyFailure.CONVERTER_STOPPED, result.report)
            is ConversionResult.Converted -> result.documents.filterNot { CollectionNames.collectionOf(it.path) == CollectionNames.USERS }
        }
        if (documents.size != cleared.documents.size || !documents.zip(cleared.documents).all { (a, b) -> a.path == b.path && MigrationLedger.hashOf(a.data, null) == MigrationLedger.hashOf(b.data, null) }) {
            return@withContext failed(CopyFailure.DOCUMENTS_DIFFER)
        }
        val record = CopyRecord(cleared.fingerprint, clock.now(), readOrNull { copies.displayName(uri) }, cleared.total)
        readOrNull { state.rememberCopy(record) } ?: return@withContext failed(CopyFailure.STATE_FAILED)
        CopyResult.Verified(cleared.copy(copy = CopyStatus.Verified(record)))
    }

    /**
     * Flytten (OMB-2, OMB-7), även som **återupptagning** efter en avbruten eller lämnad körning. Påminnelserna pausas
     * bara medan den körs ([LegacyMigrationPause], try/finally – aldrig avbokade larm, bara ingen ny schemaläggning).
     *
     * Likhet avgörs på **ett** sätt: hashen över den kanoniska formen ([MigrationLedger.hashOf] – nycklar sorterade
     * rekursivt, även i listor, inom samlingens fasta fältmängd `DocumentRules.fieldTree`), en gång per dokument. Per
     * dokument, mot serverns läge (läst på id) och liggaren:
     * - saknas på servern → skrivs;
     * - lika med vårt → verifierat (V-rad om det är antecknat; ett dokument som var lika redan före första skrivningen är
     *   inte flyttens – det gör den inte "påbörjad" och raderas aldrig av [abort]);
     * - antecknat i liggaren (P/W/V/M – migreringen har skrivit eller tänkt skriva det) men skiljer sig → skrivs om helt
     *   inom fältmängden (fält utanför vårt dokument tas bort, [RawDocumentWriter.DELETE]) – ändringar som gjorts i 4.0 på
     *   ett sådant dokument under flytten, även från en annan enhet, skyddas inte;
     * - aldrig vårt och olika → bara saknade fält fylls ([LegacyWriteOutcome.existing]); inställningar från 4.0 skrivs aldrig över.
     * Bara det som saknas eller skiljer sig skrivs, i `MigrationBatches` form med serverns kvitto per batch; P-raden skrivs
     * **före** batchens commit och W-raden efter kvittot, så ett skrivet dokument aldrig faller ur liggaren. Vid timeout
     * stannar körningen direkt (ingen mer batch köas). De skrivna dokumenten läses tillbaka och hashas på samma sätt; de
     * som stämmer sparas som verifierade, de andra som avvikande (M, med serverns hash eller `MISSING`) och ger
     * [LegacyWriteOutcome.Mismatch]. Liggarens IO-fel ger [LegacyWriteOutcome.Failed] med [LEDGER_FAILED]. [onProgress]
     * får antal klara per entitet efter varje batch. Kräver en verifierad kopia för Room-filen som den ser ut nu (OMB-8);
     * ett fel stannar utan markör och kan göras om.
     */
    suspend fun write(plan: LegacyMigrationPlan, onProgress: (Map<String, Int>) -> Unit = {}): LegacyWriteOutcome = withContext(computation) {
        pause.set(true)
        try {
            writePaused(plan, onProgress)
        } finally {
            pause.set(false)
        }
    }

    private suspend fun writePaused(plan: LegacyMigrationPlan, onProgress: (Map<String, Int>) -> Unit): LegacyWriteOutcome {
        val tally = Tally(plan.counts)
        fun failed(error: DataError, batch: Int? = null, code: String = codeOf(error)) =
            LegacyWriteOutcome.Failed(plan.counts, tally.doneSoFar(), error, batch, code, tally.existing())
        fun ledgerFailed(batch: Int? = null) = failed(DataError.Unknown, batch, LEDGER_FAILED)
        if (scope.uid.value != plan.uid) return failed(DataError.NotSignedIn)
        if (plan.copy !is CopyStatus.Verified) return failed(DataError.Unknown, code = COPY_NOT_VERIFIED)
        // Room-filen får inte ha ändrats sedan kopian (3.x använd igen) – då krävs en ny kopia först. Ett läsfel räknas lika.
        if (readOrNull { room.fingerprint() } != plan.fingerprint) return failed(DataError.Unknown, code = SOURCE_CHANGED)
        scope.writeBlocker()?.let { return failed(it) }
        // Dokumenten är i appens format; ett konto i ett äldre format måste lyftas med migrate.mjs först.
        if (scope.currentVersion() != Schema.CURRENT_VERSION) return failed(DataError.UpdateRequired)

        // 1. Läget på servern och liggaren; en hash per dokument, inom samlingens fasta fältmängd.
        val stored = fromServer { documents.documents(plan.documents.map { it.path }) }.getOrElse { return failed(it.asDataError()) }
        val before = readOrNull { ledger.read(plan.uid) } ?: return ledgerFailed()
        val trees = Trees()
        val hashes = HashMap<String, String>(plan.documents.size * 2)
        val full = mutableListOf<ExportFormat.Document>()
        val partial = mutableListOf<ExportFormat.Document>()
        val lateVerified = linkedMapOf<String, String>()
        for (document in plan.documents) {
            val collection = CollectionNames.collectionOf(document.path)
            val tree = trees[collection]
            val rel = relative(plan.uid, document.path)
            val ours = MigrationLedger.hashOf(document.data, tree)
            hashes[document.path] = ours
            val existing = stored[document.path]
            val onServer = existing?.let { MigrationLedger.hashOf(it, tree) }
            when {
                existing == null -> full += document
                onServer == ours -> {
                    tally.after.add(collection)
                    // Bara flyttens egna dokument antecknas; ett som var lika redan före första skrivningen (t.ex. efter en import) är inte flyttens och raderas aldrig.
                    if (rel in before && rel !in before.verified) lateVerified[rel] = ours
                }
                rel in before -> full += ExportFormat.Document(document.path, withDeletions(document.data, existing, tree))
                else -> {
                    val missing = missingFields(document.data, existing)
                    if (missing.isNotEmpty()) partial += ExportFormat.Document(document.path, missing) else tally.existing.add(collection)
                }
            }
        }
        if (lateVerified.isNotEmpty()) readOrNull { ledger.appendVerified(plan.uid, lateVerified) } ?: return ledgerFailed()
        onProgress(tally.progress())

        // 2. Skriv; hela dokument antecknas som planerade (P) före batchens commit och som skrivna (W) efter kvittot (delvisa fyller bara luckor).
        val fullPaths = full.mapTo(HashSet()) { it.path }
        MigrationBatches.plan(full + partial).forEachIndexed { index, batch ->
            val inBatch = batch.filter { it.path in fullPaths }.associate { relative(plan.uid, it.path) to hashes.getValue(it.path) }
            readOrNull { ledger.appendPlanned(plan.uid, inBatch) } ?: return ledgerFailed(index)
            writer.writeBatch(batch).onFailure { error ->
                // Avvisad definitivt (inte timeout): inget skrevs – sökvägarna är inte flyttens. Vid timeout kan batchen landa senare: P står kvar.
                if (error.asDataError() != DataError.Offline) readOrNull { ledger.appendCancelled(plan.uid, inBatch.keys) }
                return failed(error.asDataError(), index)
            }
            tally.written.addAll(batch.map { CollectionNames.collectionOf(it.path) })
            readOrNull { ledger.appendWritten(plan.uid, inBatch) } ?: return ledgerFailed(index)
            onProgress(tally.progress())
        }

        // 3. Verifiera det skrivna på id från servern; det som stämmer sparas som verifierat med sin hash.
        val written = full + partial
        val afterWrite = if (written.isEmpty()) emptyMap() else fromServer { documents.documents(written.map { it.path }) }.getOrElse { return failed(it.asDataError()) }
        val verifiedNow = linkedMapOf<String, String>()
        val mismatchedNow = linkedMapOf<String, String>()
        for (document in written) {
            val collection = CollectionNames.collectionOf(document.path)
            val got = afterWrite[document.path]
            when {
                got == null -> {
                    tally.mismatched.add(collection)
                    if (document.path in fullPaths) mismatchedNow[relative(plan.uid, document.path)] = MigrationLedger.MISSING
                }
                document.path in fullPaths -> {
                    val onServer = MigrationLedger.hashOf(got, trees[collection])
                    if (onServer == hashes.getValue(document.path)) {
                        tally.afterWritten.add(collection)
                        verifiedNow[relative(plan.uid, document.path)] = hashes.getValue(document.path)
                    } else {
                        tally.mismatched.add(collection)
                        mismatchedNow[relative(plan.uid, document.path)] = onServer
                    }
                }
                MigrationLedger.hashOf(project(got, document.data), null) == MigrationLedger.hashOf(document.data, null) -> tally.existing.add(collection)
                else -> tally.mismatched.add(collection)
            }
        }
        if (verifiedNow.isNotEmpty()) readOrNull { ledger.appendVerified(plan.uid, verifiedNow) } ?: return ledgerFailed()
        if (mismatchedNow.isNotEmpty()) readOrNull { ledger.appendMismatched(plan.uid, mismatchedNow) } ?: return ledgerFailed()
        return if (tally.mismatched.isEmpty()) {
            LegacyWriteOutcome.Verified(plan.counts, tally.after(), tally.existing())
        } else {
            LegacyWriteOutcome.Mismatch(plan.counts, tally.after(), tally.mismatched(), tally.existing())
        }
    }

    /**
     * "Avbryt flytten" (OMB-7): raderar exakt de sökvägar liggaren har (P/W/V/M – det migreringen skrivit eller tänkt
     * skriva; dokument som fanns före flytten och bara fyllts i har ingen rad och rörs aldrig), incheckningar före sina
     * episoder, i batchar med serverns kvitto, läser sedan tillbaka på id och kräver att inget finns kvar. Först då rensas
     * liggaren. Påminnelserna pausas medan det pågår. Ett fel eller något som finns kvar ger [LegacyAbortOutcome.Failed]
     * och kan göras om; liggaren står kvar tills allt är borta. Utan rader: klart direkt.
     */
    suspend fun abort(): LegacyAbortOutcome = withContext(computation) {
        pause.set(true)
        try {
            abortPaused()
        } finally {
            pause.set(false)
        }
    }

    private suspend fun abortPaused(): LegacyAbortOutcome {
        val uid = scope.uid.value ?: return LegacyAbortOutcome.Failed(DataError.NotSignedIn, codeOf(DataError.NotSignedIn))
        val recorded = readOrNull { ledger.read(uid) } ?: return LegacyAbortOutcome.Failed(DataError.Unknown, LEDGER_FAILED)
        val paths = recorded.paths.map { "${Paths.user(uid)}/$it" }.sortedByDescending { it.count { c -> c == '/' } }
        for (batch in paths.chunked(MigrationBatches.MAX_WRITES)) {
            writer.deleteBatch(batch).onFailure { return LegacyAbortOutcome.Failed(it.asDataError(), codeOf(it.asDataError())) }
        }
        val remaining = fromServer { documents.documents(paths) }.getOrElse { return LegacyAbortOutcome.Failed(it.asDataError(), codeOf(it.asDataError())) }
        if (remaining.isNotEmpty()) {
            return LegacyAbortOutcome.Failed(DataError.Unknown, ABORT_INCOMPLETE, remaining.keys.groupingBy { CollectionNames.collectionOf(it) }.eachCount())
        }
        readOrNull { ledger.delete(uid) } ?: return LegacyAbortOutcome.Failed(DataError.Unknown, LEDGER_FAILED)
        return LegacyAbortOutcome.Done(paths.groupingBy { CollectionNames.collectionOf(it) }.eachCount())
    }



    /**
     * Sista steget efter [LegacyWriteOutcome.Verified] och användarens bekräftelse: markören på servern (med `counts` =
     * rapportens "före"), flaggan på enheten för kontot och liggaren raderas. **Idempotent:** finns markören redan –
     * efter en timeout eller ett nekande från en tidigare bekräftelse – räknas det som lyckat.
     */
    suspend fun confirm(plan: LegacyMigrationPlan, outcome: LegacyWriteOutcome.Verified): Result<Unit> = suspendRunCatching({ DataError.Unknown }) {
        if (scope.uid.value != plan.uid) throw DataError.NotSignedIn
        val marker = LegacyMigration(
            source = LegacySource.ROOM,
            sourceCreatedAt = BackupJsonConverter.sourceCreatedAt(plan.backup),
            appVersion = appVersion.name,
            counts = outcome.before,
        )
        val wrote = writer.markLegacyMigration(plan.uid, asDoc(LegacyMigrationCodec.encode(marker)) - LegacyMigrationCodec.COMPLETED_AT)
        if (wrote.isFailure) {
            val existing = fromServer { documents.document(Paths.user(plan.uid))?.get(LegacyMigrationCodec.FIELD) }.getOrNull()
            if (LegacyMigrationCodec.decode(existing) == null) throw wrote.exceptionOrNull()!!.asDataError()
        }
        state.markDone(plan.uid)
        ledger.delete(plan.uid)
    }

    /** Det lagrade dokumentet begränsat till [template]s nycklar, rekursivt – det som ska hasha lika med en delvis skrivning. */
    private fun project(stored: Doc, template: Doc): Doc = template.mapNotNull { (field, value) ->
        if (!stored.containsKey(field)) return@mapNotNull null
        val got = stored[field]
        field to if (value is Map<*, *> && got is Map<*, *>) project(asDoc(got), asDoc(value)) else got
    }.toMap()

    /** Konverterarens fält som saknas på servern, rekursivt – det som skrivs (med merge) till ett dokument som redan finns. */
    private fun missingFields(expected: Doc, stored: Doc): Doc = expected.mapNotNull { (field, value) ->
        val got = stored[field]
        when {
            value is Map<*, *> && got is Map<*, *> -> missingFields(asDoc(value), asDoc(got)).takeIf { it.isNotEmpty() }?.let { field to it }
            !stored.containsKey(field) -> field to value
            else -> null
        }
    }.toMap()

    /** Samlingarnas fasta fältmängder, en gång per körning. */
    private class Trees {
        private val trees = HashMap<String, DocumentRules.FieldTree>()

        operator fun get(collection: String): DocumentRules.FieldTree = trees.getOrPut(collection) { DocumentRules.fieldTree(collection) }
    }

    /** Antal per entitet under skrivningen, i "före"-ordningen. */
    private class Tally(private val before: Map<String, Int>) {
        /** Lika på servern redan före skrivningen. */
        val after = Counter()

        /** Skrivna i den här körningen och verifierade lika – separat från [written], så att inget räknas två gånger. */
        val afterWritten = Counter()
        val existing = Counter()
        val written = Counter()
        val mismatched = Counter()

        /** Lika på servern per entitet, i "före"-ordningen med 0 där inget är lika. */
        fun after() = before.keys.associateWith { after[it] + afterWritten[it] }

        /** Det som var klart när skrivningen stannade: lika på servern och skrivet med kvitto. */
        fun doneSoFar() = before.keys.associateWith { after[it] + written[it] }

        fun existing() = existing.sparse()

        fun mismatched() = mismatched.sparse()

        /** Klara per entitet: lika, befintliga och skrivna hittills. */
        fun progress(): Map<String, Int> = before.keys.associateWith { after[it] + existing[it] + written[it] }

        /** Bara entiteter med något att rapportera, i "före"-ordningen – tom map = inget. */
        private fun Counter.sparse(): Map<String, Int> = before.keys.filter { this[it] > 0 }.associateWith { this[it] }
    }

    private class Counter {
        private val counts = linkedMapOf<String, Int>()

        operator fun get(key: String): Int = counts[key] ?: 0

        fun add(key: String) {
            counts.merge(key, 1, Int::plus)
        }

        fun addAll(keys: Collection<String>) = keys.forEach(::add)

        fun isEmpty() = counts.isEmpty()
    }

    private fun relative(uid: String, path: String): String = path.removePrefix("${Paths.user(uid)}/")

    private suspend fun <T> fromServer(block: suspend () -> T): Result<T> = suspendRunCatching(::firestoreError) { block() }

    private fun Throwable.asDataError(): DataError = this as? DataError ?: DataError.Unknown

    /** Lokala fel blir `null`; ett avbrott släpps igenom. */
    private inline fun <T> readOrNull(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        /** Felkoden när planen skulle skrivas utan verifierad kopia – ett fel i flödet, inte hos servern (OMB-8). */
        const val COPY_NOT_VERIFIED = "COPY_NOT_VERIFIED"

        /** Felkoden när Room-filen ändrats (eller inte går att läsa) sedan den lästes och kopian togs – läs om och ta en ny kopia. */
        const val SOURCE_CHANGED = "SOURCE_CHANGED"

        /** Felkoden när liggaren på enheten inte gick att läsa eller skriva – inget på servern är fel; "Försök igen" kör om. */
        const val LEDGER_FAILED = "LEDGER_FAILED"

        /** Felkoden när något migreringen skrivit finns kvar på servern efter "Avbryt flytten" – gör om. */
        const val ABORT_INCOMPLETE = "ABORT_INCOMPLETE"

        /**
         * Det nya 3.x-dokumentet att skriva över vårt eget på servern: fält inom [tree] som servern har men dokumentet
         * saknar markeras [RawDocumentWriter.DELETE], rekursivt – så att servern efteråt hashar som [ours]. (Konverteraren
         * skriver ett tömt fält som `null`, så det här gäller bara nycklar den inte längre skriver alls.)
         */
        internal fun withDeletions(ours: Doc, stored: Doc, tree: DocumentRules.FieldTree): Doc {
            val result = LinkedHashMap(ours)
            for ((field, sub) in tree.fields) {
                val mine = ours[field]
                val theirs = stored[field]
                when {
                    !ours.containsKey(field) -> if (stored.containsKey(field)) result[field] = RawDocumentWriter.DELETE
                    sub != null && mine is Map<*, *> && theirs is Map<*, *> -> result[field] = withDeletions(asDoc(mine), asDoc(theirs), sub)
                }
            }
            return result
        }

        /** Felkoden i rapporten – Firestores namn för det `DataError` som mappades, aldrig innehåll. */
        fun codeOf(error: DataError): String = when (error) {
            DataError.PermissionDenied -> "PERMISSION_DENIED"
            DataError.QuotaExceeded -> "RESOURCE_EXHAUSTED"
            DataError.Offline -> "UNAVAILABLE"
            DataError.NotSignedIn -> "UNAUTHENTICATED"
            DataError.UpdateRequired -> "UPDATE_REQUIRED"
            DataError.Cancelled -> "CANCELLED"
            DataError.NotFound -> "NOT_FOUND"
            DataError.SignInRejected, DataError.Unknown -> "UNKNOWN"
        }
    }
}
