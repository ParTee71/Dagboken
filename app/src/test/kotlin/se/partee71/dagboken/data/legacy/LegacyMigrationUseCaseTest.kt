package se.partee71.dagboken.data.legacy

import android.net.Uri
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.legacy.BackupJson
import se.partee71.dagboken.core.legacy.LegacyRoomSchema
import se.partee71.dagboken.core.legacy.MigrationBatches
import se.partee71.dagboken.core.legacy.Row
import se.partee71.dagboken.core.model.LegacySource
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.LegacyMigrationCodec
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.FakeStore
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.firestore.RawDocumentWriter
import se.partee71.dagboken.testing.FakeAuthRepository

/**
 * Migreringen på enheten mot fejkar (OMB-2, OMB-7, NAV-6): startkontrollen mot flaggan och servermarkören, läsningen
 * ur `room-v11.json` (samma dokument som konverteraren, antal före = rapportens), kopian som 3.x läser, skrivningen i
 * batchar med verifiering, avbruten körning som görs om utan dubbletter, avvikelse på servern, stopp och fel version
 * utan skrivning, och bekräftelsen (markör, flagga, larm). Syntetisk data.
 */
@RunWith(RobolectricTestRunner::class)
class LegacyMigrationUseCaseTest {
    private val uid = "uid-legacy"
    private val repoRoot = File("").absoluteFile.let { if (it.name == "app") it.parentFile else it }
    private val fixture = Json.parseToJsonElement(File(repoRoot, "tools/db/test/fixtures/legacy/room-v11.json").readText()).jsonObject
    private val expected = ExportFormat.decode(File(repoRoot, "tools/db/test/fixtures/legacy/backup-v2.expected.json").readText())
        .filterNot { CollectionNames.collectionOf(it.path) == CollectionNames.USERS }

    @Suppress("UNCHECKED_CAST")
    private val tables = ExportFormat.fromJson(fixture.getValue("tables")) as Map<String, List<Row>>

    @Suppress("UNCHECKED_CAST")
    private val preferences = ExportFormat.fromJson(fixture.getValue("preferences")) as Map<String, Any?>

    private val store = FakeStore()
    private val firestore = FakeRawFirestore(store)
    private val room = FakeLegacyRoomSource(LegacyRoomRead.Tables(tables, 1_768_507_200_000, FINGERPRINT))
    private val prefs = FakeLegacyPreferencesSource(preferences)
    private val flag = FakeMigrationState()
    private val work = FakeLegacyWork()
    private val copies = FakeCopyFile()
    private val ledger = FakeMigrationLedger()
    private val pause = LegacyMigrationPause()
    private val auth = FakeAuthRepository(AuthUser(uid, email = "anna@example.com"))
    private val scope = TestUserScope(uid)
    private val clock = FixedClock()
    private val dispatcher = StandardTestDispatcher()
    private val useCase = LegacyMigrationUseCase(room, prefs, firestore, firestore, flag, ledger, work, copies, pause, AppVersion(), auth, scope, clock, dispatcher, dispatcher)

    /** Samma schemaläggare i testet som i use caset (`withContext(dispatcher)`). */
    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }
    private val copyUri: Uri = Uri.parse("content://test/kopia.json")

    private val before = mapOf(
        "settings" to 1, "options" to 11, "prescriptions" to 3, "prnMedicines" to 2, "doses" to 4,
        "screenings" to 3, "activities" to 2, "events" to 2, "illnessEpisodes" to 2, "checkins" to 2,
    )

    init {
        store.set(Paths.USERS, uid, mapOf("schemaVersion" to Schema.CURRENT_VERSION), merge = false)
    }

    private companion object {
        const val FINGERPRINT = "sha256-av-db-och-wal"
    }

    private suspend fun readyPlan(): LegacyMigrationPlan = assertIs<LegacyRead.Ready>(useCase.read()).plan

    private suspend fun savedPlan(): LegacyMigrationPlan = assertIs<CopyResult.Verified>(useCase.saveCopy(readyPlan(), copyUri)).plan

    /** Alla dokument under användaren som de ligger i fejken, på sökväg. */
    private fun storedDocuments(): Map<String, Map<String, Any?>> =
        store.documents.value.filterKeys { it != Paths.USERS }.flatMap { (path, docs) -> docs.map { (id, doc) -> "$path/$id" to doc } }.toMap()

    // ── Startkontrollen (NAV-6) ───────────────────────────────────────────

    @Test
    fun `isPending - Room-fil utan flagga och utan markör på servern ger sant och avbokar 3x backupjobb tills det lyckats, utan att pausa påminnelserna`() = test {
        assertFalse(pause.paused.value, "en väntande migrering pausar ingenting")
        // Ett fel i WorkManager fäller aldrig startkontrollen – men flaggan sätts inte, så nästa start försöker igen.
        work.fails = true
        assertEquals(true, useCase.isPending().getOrThrow())
        assertEquals(1, work.cancelled)
        assertFalse(flag.backupJobCancelled)
        assertEquals(true, useCase.isPending().getOrThrow())
        assertEquals(2, work.cancelled, "försöker igen vid nästa start")
        work.fails = false
        assertEquals(true, useCase.isPending().getOrThrow())
        assertEquals(3, work.cancelled)
        assertTrue(flag.backupJobCancelled, "flaggan sätts först när avbokningen lyckats")
        assertEquals(true, useCase.isPending().getOrThrow())
        assertEquals(3, work.cancelled, "avbokat: WorkManager rörs inte mer")
        assertFalse(pause.paused.value)
    }

    @Test
    fun `3x-sessionen fångas vid första starten före inloggningsgrinden, en gång, och ger kontokontrollen`() = test {
        val capture = LegacySessionCapture(room, { flag }, { auth }, this)
        capture.capture()
        runCurrent()
        assertEquals(LegacySession(uid), flag.session)
        assertEquals(AccountCheck.SAME, readyPlan().accountCheck)
        // Ett annat konto loggar in: varning. Fångas inte om igen.
        scope.uid.value = "annan"
        auth.authState.value = AuthUser("annan", email = "b@example.com")
        store.set(Paths.USERS, "annan", mapOf("schemaVersion" to Schema.CURRENT_VERSION), merge = false)
        capture.capture()
        runCurrent()
        assertEquals(LegacySession(uid), flag.session)
        val other = readyPlan()
        assertEquals(AccountCheck.DIFFERENT, other.accountCheck)
        assertTrue(other.accountMismatch)
        assertEquals(uid, other.previousUid)
        // 3.x kördes utan konto: okänt – e-posten måste bekräftas uttryckligen.
        flag.session = LegacySession(null)
        assertEquals(AccountCheck.UNKNOWN, readyPlan().accountCheck)
        // Utan Room-fil fångas inget.
        flag.session = null
        room.read = LegacyRoomRead.Missing
        capture.capture()
        runCurrent()
        assertNull(flag.session)
    }

    @Test
    fun `isPending - utan Room-fil falskt, med flagga falskt utan serverfråga, med markör på servern falskt och flaggan sätts`() = test {
        room.read = LegacyRoomRead.Missing
        assertEquals(false, useCase.isPending().getOrThrow())
        assertEquals(0, work.cancelled)

        room.read = LegacyRoomRead.Tables(tables, 0, FINGERPRINT)
        flag.done += uid
        firestore.offline = true
        assertEquals(false, useCase.isPending().getOrThrow(), "flaggan räcker – servern frågas inte")
        assertEquals(1, work.cancelled, "backupjobbet avbokas oberoende av kontots flagga")
        assertTrue(flag.backupJobCancelled)

        flag.done.clear()
        firestore.offline = false
        store.set(Paths.USERS, uid, mapOf(LegacyMigrationCodec.FIELD to mapOf("source" to "room")), merge = true)
        assertEquals(false, useCase.isPending().getOrThrow(), "markören på servern gäller oavsett enhetens flagga")
        assertTrue(uid in flag.done)
        assertEquals(1, work.cancelled, "redan avbokat")
        assertFalse(pause.paused.value, "ingen migrering väntar – påminnelserna får gå")
    }

    @Test
    fun `isPending - utan nät och utan flagga blir det Offline, utloggad NotSignedIn`() = test {
        firestore.offline = true
        assertEquals(DataError.Offline, useCase.isPending().exceptionOrNull())
        scope.uid.value = null
        assertEquals(DataError.NotSignedIn, useCase.isPending().exceptionOrNull())
    }

    // ── Läsa ──────────────────────────────────────────────────────────────

    @Test
    fun `read - Room-raderna ger konverterarens dokument utan users, antal före = rapportens, e-post och varningar utan innehåll`() = test {
        flag.session = LegacySession("uid-3x")
        val plan = readyPlan()
        assertEquals(expected.map { it.path }, plan.documents.map { it.path })
        for ((doc, got) in expected.zip(plan.documents)) assertEquals(ExportFormat.toJson(doc.data), ExportFormat.toJson(got.data), doc.path)
        assertEquals(before, plan.counts)
        assertEquals(before.keys.toList(), plan.counts.keys.toList(), "entiteterna i CollectionNames-ordning")
        assertEquals(32, plan.total)
        assertEquals("anna@example.com", plan.accountEmail)
        assertTrue(plan.accountMismatch, "3.x-sessionen var ett annat konto")
        assertEquals(AccountCheck.DIFFERENT, plan.accountCheck)
        assertEquals(CopyStatus.Missing, plan.copy)
        assertEquals(FINGERPRINT, plan.fingerprint)
        assertEquals(tables.mapValues { it.value.size }, plan.tableCounts)
        assertEquals(2, plan.backup.version)
        assertEquals("2026-01-15T21:00:00", plan.backup.createdAt, "Room-filens senaste ändring i Europe/Stockholm")
        assertTrue(plan.warnings.any { it.path == "notes" && "utan sin post" in it.message })
        val rendered = plan.warnings.joinToString()
        for (secret in listOf("Regn", "Svamplockning", "Levaxin", "Anteckning utan post")) assertFalse(secret in rendered, secret)
    }

    @Test
    fun `read - fel databasversion, saknad fil, trasig fil och utloggad är egna utfall och inget skrivs`() = test {
        room.read = LegacyRoomRead.WrongVersion(10)
        assertEquals(LegacyRead.WrongVersion(10), useCase.read())
        room.read = LegacyRoomRead.Missing
        assertEquals(LegacyRead.NoDatabase, useCase.read())
        room.read = LegacyRoomRead.Tables(tables, 0, FINGERPRINT)
        room.fails = true
        assertEquals(LegacyRead.Unreadable, useCase.read())
        room.fails = false
        prefs.fails = true
        assertEquals(LegacyRead.Unreadable, useCase.read())
        prefs.fails = false
        scope.uid.value = null
        assertEquals(LegacyRead.Failed(DataError.NotSignedIn), useCase.read())
        assertEquals(emptyMap(), storedDocuments())
    }

    @Test
    fun `read - ett stopp i konverteraren ger rapporten med antal och fel, aldrig innehåll, och inget skrivs`() = test {
        val orphan = mapOf("id" to "c-x", "episod_id" to "finns-inte", "datum" to "2026-01-11", "tid" to "09:00", "svarighetsgrad" to 3L, "symptom" to "", "somatiska" to 0L, "timestamp" to 0L)
        room.read = LegacyRoomRead.Tables(tables + (LegacyRoomSchema.SJUKDOMS_INCHECKNINGAR to tables.getValue(LegacyRoomSchema.SJUKDOMS_INCHECKNINGAR) + orphan), 0, FINGERPRINT)
        val stopped = assertIs<LegacyRead.Stopped>(useCase.read())
        assertTrue(stopped.report.stopped)
        assertEquals(1, stopped.report.problems.size)
        assertEquals("episodId", stopped.report.problems.single().field)
        assertEquals(before + ("users" to 1), stopped.report.counts, "incheckningen utan episod räknas inte – den har ingen sökväg")
        assertFalse("Levaxin" in stopped.report.render())
        assertEquals(emptyMap(), storedDocuments())
        assertFalse(uid in flag.done)
    }

    // ── Kopian (OMB-8) ────────────────────────────────────────────────────

    @Test
    fun `saveCopy - skriver en 3x-backupfil, läser tillbaka och verifierar den, och utan verifierad kopia skrivs inget`() = test {
        val plan = readyPlan()
        assertEquals(LegacyMigrationUseCase.COPY_NOT_VERIFIED, assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan)).code)
        assertEquals(emptyMap(), storedDocuments())
        val saved = assertIs<CopyResult.Verified>(useCase.saveCopy(plan, copyUri)).plan
        val record = assertIs<CopyStatus.Verified>(saved.copy).record
        assertEquals(CopyRecord(FINGERPRINT, clock.now(), "dagboken-3x.json", 32), record)
        assertEquals(record, flag.copyRecord, "kopian sparas på enheten")
        assertEquals(plan.backup, BackupJson.parse(copies.files.getValue(copyUri)), "3.x:s parser läser filen tillbaka till samma data")
        // Nästa läsning (t.ex. efter processdöd) ser kopian som verifierad för samma Room-fil …
        assertEquals(CopyStatus.Verified(record), readyPlan().copy)
        // … men inte efter att filen ändrats: då krävs en ny kopia.
        room.read = LegacyRoomRead.Tables(tables, 0, "annan-fil")
        assertEquals(CopyStatus.Stale(record), readyPlan().copy)
        assertEquals(LegacyMigrationUseCase.COPY_NOT_VERIFIED, assertIs<LegacyWriteOutcome.Failed>(useCase.write(readyPlan())).code)
    }

    @Test
    fun `saveCopy - varje steg i verifieringen har sitt eget skäl, och en kopia som inte blev klar sparas inte`() = test {
        val plan = readyPlan()
        copies.writeFails = true
        assertEquals(CopyResult.Failed(CopyFailure.WRITE_FAILED), useCase.saveCopy(plan, copyUri))
        copies.writeFails = false
        copies.readBack = { "HEMLIGT trasigt" }
        assertEquals(CopyResult.Failed(CopyFailure.UNREADABLE), useCase.saveCopy(plan, copyUri))
        // En anteckning försvann på vägen: antalet per entitet stämmer inte med Room.
        copies.readBack = { it.replace(""",{"target":"ACTIVITY","entityId":"finns-inte-i-backupen","text":"Anteckning utan post"}""", "") }
        assertEquals(CopyResult.Failed(CopyFailure.COUNT_MISMATCH), useCase.saveCopy(plan, copyUri))
        // Samma antal men en incheckning som pekar fel: konverteraren stoppar, och rapporten följer med.
        copies.readBack = { it.replace(""""episodId":"9e0f1a2b-3c4d-4e5f-8a6b-7c8d9e0f1a2b"""", """"episodId":"finns-inte"""") }
        val stopped = assertIs<CopyResult.Failed>(useCase.saveCopy(plan, copyUri))
        assertEquals(CopyFailure.CONVERTER_STOPPED, stopped.reason)
        assertTrue(stopped.report?.stopped == true)
        // Samma antal, konverteraren går igenom, men ett värde skiljer sig.
        copies.readBack = { it.replace(""""namn":"Alvedon"""", """"namn":"Alvedom"""") }
        assertEquals(CopyResult.Failed(CopyFailure.DOCUMENTS_DIFFER), useCase.saveCopy(plan, copyUri))
        assertNull(flag.copyRecord)
        assertEquals(CopyStatus.Missing, readyPlan().copy)
    }

    @Test
    fun `saveCopy - en verifierad kopia glöms innan ett nytt försök skriver, så ett misslyckat försök lämnar inget gammalt verifierat kvar`() = test {
        val verified = savedPlan()
        assertNotNull(flag.copyRecord)
        copies.writeFails = true
        assertEquals(CopyResult.Failed(CopyFailure.WRITE_FAILED), useCase.saveCopy(verified, copyUri))
        assertNull(flag.copyRecord, "den gamla kopian gäller inte längre")
        assertEquals(CopyStatus.Missing, readyPlan().copy)
        assertEquals(LegacyMigrationUseCase.COPY_NOT_VERIFIED, assertIs<LegacyWriteOutcome.Failed>(useCase.write(readyPlan())).code)
    }

    @Test
    fun `write - ändras Room-filen efter kopian stannar flytten före första batchen`() = test {
        val plan = savedPlan()
        room.fingerprint = "3x-skrev-igen"
        val failed = assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertEquals(LegacyMigrationUseCase.SOURCE_CHANGED, failed.code)
        assertEquals(emptyList(), firestore.batches)
    }

    // ── Skriva och verifiera ──────────────────────────────────────────────

    @Test
    fun `write - batcharna håller gränserna, framstegen räknas per entitet och varje dokument verifieras på servern`() = test {
        val plan = savedPlan()
        val progress = mutableListOf<Map<String, Int>>()
        var pausedDuringWrite = true
        val outcome = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan) { progress += it; pausedDuringWrite = pausedDuringWrite && pause.paused.value })
        assertEquals(before, outcome.before)
        assertEquals(before, outcome.after)
        assertEquals(MigrationBatches.plan(plan.documents), firestore.batches)
        assertEquals(1, firestore.batches.size, "32 dokument ryms i en batch")
        assertEquals(listOf(before.mapValues { 0 }, before), progress, "läget på servern först (inget klart), sedan batchen")
        assertEquals(plan.documents.map { it.path.removePrefix("users/$uid/") }.toSet(), ledger.verified.getValue(uid).keys, "varje skrivet dokument verifierat och antecknat med hash")
        assertEquals(emptyMap(), ledger.read(uid).pending)
        assertTrue(pausedDuringWrite, "påminnelserna pausas medan skrivningen pågår")
        assertFalse(pause.paused.value, "och släpps så fort den är klar – bekräftelsen behövs inte för det")
        assertEquals(expected.associate { it.path to ExportFormat.toJson(it.data) }, storedDocuments().mapValues { ExportFormat.toJson(it.value) })
        assertNull(store.read(Paths.USERS, uid)?.get(LegacyMigrationCodec.FIELD), "markören sätts först vid bekräftelsen")
        assertFalse(uid in flag.done)
    }

    @Test
    fun `write - många episoder delas upp på högst 20 per batch, och framstegen växer per batch`() = test {
        val episodes = (0 until 25).map { mapOf("id" to "ep%02d".format(it), "typ" to "Förkylning", "start_datum" to "2026-01-01", "slut_datum" to "", "timestamp" to 0L) }
        val checkins = episodes.map { mapOf("id" to "c-${it["id"]}", "episod_id" to it["id"], "datum" to "2026-01-02", "tid" to "09:00", "svarighetsgrad" to 2L, "symptom" to "", "somatiska" to 0L, "timestamp" to 0L) }
        room.read = LegacyRoomRead.Tables(mapOf(LegacyRoomSchema.SJUKDOMSEPISODER to episodes, LegacyRoomSchema.SJUKDOMS_INCHECKNINGAR to checkins), 0, FINGERPRINT)
        prefs.values = emptyMap()
        val plan = savedPlan()
        val progress = mutableListOf<Map<String, Int>>()
        val outcome = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan) { progress += it })
        assertEquals(mapOf("settings" to 1, "options" to 22, "illnessEpisodes" to 25, "checkins" to 25), outcome.after)
        assertEquals(listOf(20, 5), firestore.batches.map { batch -> batch.count { CollectionNames.collectionOf(it.path) == CollectionNames.ILLNESS_EPISODES } })
        assertEquals(3, progress.size)
        assertEquals(0, progress[0]["illnessEpisodes"])
        assertEquals(20, progress[1]["illnessEpisodes"])
        assertEquals(25, progress[2]["illnessEpisodes"])
    }

    @Test
    fun `write - en nekad batch stannar med felkod och batchnummer, och omkörningen ger samma dokument utan dubbletter`() = test {
        val plan = savedPlan()
        firestore.failAt = 0
        val failed = assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertEquals(DataError.PermissionDenied, failed.error)
        assertEquals("PERMISSION_DENIED", failed.code)
        assertEquals(0, failed.batch)
        assertEquals(before.mapValues { 0 }, failed.after)
        assertEquals(emptyMap(), storedDocuments())
        assertEquals(Ledger.EMPTY, ledger.read(uid), "avvisad definitivt: P-raderna struks – inget är flyttens, flytten har inte börjat")
        assertFalse(useCase.started())
        firestore.failAt = null
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(emptyMap(), ledger.read(uid).planned, "planerat och saknas → skrivet och verifierat")
        assertEquals(emptyMap(), ledger.read(uid).pending)
        val first = storedDocuments()
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan), "en avbruten körning görs om")
        assertEquals(first, storedDocuments(), "samma id:n med merge – inga dubbletter")
        assertEquals(32, storedDocuments().size)
    }

    @Test
    fun `write - slut på kvot är ett eget fel med koden RESOURCE_EXHAUSTED, och saknat nät vid kvittot UNAVAILABLE`() = test {
        val plan = savedPlan()
        firestore.failAt = 0
        firestore.failWith = DataError.QuotaExceeded
        val quota = assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertEquals(DataError.QuotaExceeded, quota.error)
        assertEquals("RESOURCE_EXHAUSTED", quota.code)
        firestore.failAt = null
        firestore.offline = true
        val offline = assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertEquals("UNAVAILABLE", offline.code, "läsningen av läget på servern kräver nät")
        assertNull(offline.batch)
        assertEquals(emptyList(), firestore.batches, "inget skrivs utan att läget är känt")
    }

    @Test
    fun `write - ett dokument som ändrats på servern före verifieringen är en avvikelse, inte en evig slinga, och Försök igen skriver om det`() = test {
        val plan = savedPlan()
        val dose = expected.first { CollectionNames.collectionOf(it.path) == "doses" }
        val rel = dose.path.removePrefix("users/$uid/")
        firestore.tamper = { store.set(Paths.doses(uid), dose.path.substringAfterLast('/'), mapOf("name" to "Ändrad"), merge = true) }
        val mismatch = assertIs<LegacyWriteOutcome.Mismatch>(useCase.write(plan))
        assertEquals(mapOf("doses" to 1), mismatch.mismatched)
        assertEquals(before + ("doses" to 3), mismatch.after)
        val wrong = store.read(Paths.doses(uid), dose.path.substringAfterLast('/'))!!
        assertEquals(mapOf(rel to MigrationLedger.hashOf(wrong, DocumentRules.fieldTree("doses"))), ledger.read(uid).mismatched, "M-raden bär hashen över serverns felaktiga läge")
        assertEquals(emptyMap(), ledger.read(uid).pending)
        // "Försök igen": servern skiljer sig från vårt – dokumentet skrivs om helt inom fältmängden och verifieras (OMB-7).
        firestore.batches.clear()
        val retry = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(listOf(dose), firestore.batches.flatten(), "samma nycklar på servern: inget att ta bort")
        assertEquals(before, retry.after)
        assertEquals(dose.data["name"], store.read(Paths.doses(uid), dose.path.substringAfterLast('/'))?.get("name"))
        assertEquals(emptyMap(), ledger.read(uid).mismatched)
        assertTrue(rel in ledger.verified.getValue(uid))
    }

    @Test
    fun `write - misslyckas committen vid en 3x-nyare-uppdatering tränger P-raden inte undan V-raden, och nästa körning uppdaterar`() = test {
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(savedPlan()))
        val doses = tables.getValue(LegacyRoomSchema.MEDICINER)
        val noted = doses[0]["id"] as String
        val notes = tables.getValue(LegacyRoomSchema.NOTES).map { note ->
            if (note["target"] == "MEDICATION" && note["entityId"] == noted) note + ("text" to "Nyare i 3.x") else note
        }
        room.read = LegacyRoomRead.Tables(tables + (LegacyRoomSchema.NOTES to notes), 0, "ny-fil")
        room.fingerprint = "ny-fil"
        firestore.failAt = firestore.batches.size
        val plan = savedPlan()
        assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        val ledgerNow = ledger.read(uid)
        val rel = "doses/$noted"
        assertTrue(rel !in ledgerNow.planned && rel in ledgerNow.verified, "avvisad batch: P struken, V-raden (dokumentet är flyttens) står kvar")
        assertFalse("Nyare i 3.x" == store.read(Paths.doses(uid), noted)?.get("note"))
        firestore.failAt = null
        firestore.batches.clear()
        val retry = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(listOf(noted), firestore.batches.flatten().map { it.path.substringAfterLast('/') }, "servern har V-hashen: vårt och orört – 3.x nyare tillämpas")
        assertEquals("Nyare i 3.x", store.read(Paths.doses(uid), noted)?.get("note"))
        assertEquals(before, retry.after)
        assertEquals(emptyMap(), ledger.read(uid).planned)
    }

    @Test
    fun `write - en avvikelse där servern sedan har exakt vårt dokument verifieras utan skrivning och räknas i after`() = test {
        val plan = savedPlan()
        val dose = expected.first { CollectionNames.collectionOf(it.path) == "doses" }
        val rel = dose.path.removePrefix("users/$uid/")
        firestore.tamper = { store.set(Paths.doses(uid), dose.path.substringAfterLast('/'), mapOf("name" to "Fel"), merge = true) }
        assertIs<LegacyWriteOutcome.Mismatch>(useCase.write(plan))
        // Användaren rättar namnet i 4.0 till det 3.x hade.
        store.set(Paths.doses(uid), dose.path.substringAfterLast('/'), mapOf("name" to dose.data["name"]), merge = true)
        firestore.batches.clear()
        val retry = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(emptyList(), firestore.batches)
        assertEquals(before, retry.after)
        assertEquals(emptyMap(), ledger.read(uid).mismatched)
        assertEquals(MigrationLedger.hashOf(dose.data, DocumentRules.fieldTree("doses")), ledger.read(uid).verified[rel], "V-rad")
    }

    @Test
    fun `write - nyckelordningen på servern, också i mappar inne i listor, ger aldrig en avvikelse`() = test {
        val plan = savedPlan()
        // Som Firestore kan lämna tillbaka det: mapparna i omvänd nyckelordning – inställningarna (medSlots är en lista av mappar) och ett recept med doshöjningar.
        fun shuffled(value: Any?): Any? = when (value) {
            is Map<*, *> -> value.entries.reversed().associate { it.key.toString() to shuffled(it.value) }
            is List<*> -> value.map(::shuffled)
            else -> value
        }
        firestore.tamper = {
            for ((path, docs) in store.documents.value) {
                if (path == Paths.USERS) continue
                for ((id, doc) in docs) store.set(path, id, asDoc(shuffled(doc)), merge = false)
            }
        }
        val outcome = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(before, outcome.after)
        assertEquals(emptyMap(), ledger.read(uid).mismatched)
        firestore.batches.clear()
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(emptyList(), firestore.batches, "ingen evig omskrivning")
    }

    @Test
    fun `write - extra fält på servern är tillåtna, men ett saknat dokument är en avvikelse`() = test {
        val plan = savedPlan()
        val dose = expected.first { CollectionNames.collectionOf(it.path) == "doses" }
        firestore.tamper = {
            store.set(Paths.doses(uid), dose.path.substringAfterLast('/'), mapOf("framtida" to true), merge = true)
            store.delete(Paths.activities(uid), expected.first { CollectionNames.collectionOf(it.path) == "activities" }.path.substringAfterLast('/'))
        }
        val mismatch = assertIs<LegacyWriteOutcome.Mismatch>(useCase.write(plan))
        assertEquals(mapOf("activities" to 1), mismatch.mismatched)
        assertEquals(4, mismatch.after["doses"])
        val activityRel = expected.first { CollectionNames.collectionOf(it.path) == "activities" }.path.removePrefix("users/$uid/")
        assertEquals(mapOf(activityRel to MigrationLedger.MISSING), ledger.read(uid).mismatched, "saknades vid verifieringen: egen markör")
        // Omkörningen: aktiviteten saknades då och saknas nu – den skrivs igen.
        val retry = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(before, retry.after)
        assertNotNull(store.read(Paths.activities(uid), expected.first { CollectionNames.collectionOf(it.path) == "activities" }.path.substringAfterLast('/')))
    }

    // ── Återupptagning (OMB-7) ────────────────────────────────────────────

    @Test
    fun `write - inställningar som 4x0 skapat delvis kompletteras bara med de fält som saknas`() = test {
        val plan = savedPlan()
        // Användaren använde 4.0 före migreringen: temat valt, födelseår satt – ingenting annat.
        store.set(Paths.settings(uid), "app", mapOf("theme" to mapOf("mode" to "light"), "profile" to mapOf("birthYear" to 1979)), merge = true)
        val outcome = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(mapOf("settings" to 1), outcome.existing, "fanns före migreringen: temat är 4.0:s och lämnas, bara luckorna fylls")
        assertEquals(before - "settings" + ("settings" to 0), outcome.after)
        assertNull(ledger.verified[uid]?.get("settings/app"), "ett delvis fyllt dokument antecknas inte som vårt")
        val written = firestore.batches.flatten().single { CollectionNames.collectionOf(it.path) == "settings" }
        assertEquals(setOf("theme", "reminders", "profile", "legacy"), written.data.keys)
        assertEquals(setOf("lightStartHour", "darkStartHour", "isDarkTheme"), asDoc(written.data["theme"]).keys, "mode skrivs inte")
        assertEquals(setOf("sex"), asDoc(written.data["profile"]).keys, "birthYear skrivs inte")
        val stored = store.read(Paths.settings(uid), "app")!!
        assertEquals("light", asDoc(stored["theme"])["mode"])
        assertEquals(1979L, asDoc(stored["profile"])["birthYear"])
        assertEquals("male", asDoc(stored["profile"])["sex"])
        assertEquals(6L, asDoc(stored["theme"])["lightStartHour"])
    }

    @Test
    fun `write - jämförelsen är djup - extra nycklar i nästlade objekt på servern fäller inte verifieringen`() = test {
        val plan = savedPlan()
        // 4.0 lägger till nycklar som konverteraren inte skrev – på toppnivå och i en nästlad grupp – mellan skrivningen och verifieringen.
        firestore.tamper = { store.set(Paths.settings(uid), "app", mapOf("profile" to mapOf("framtida" to true), "nyGrupp" to mapOf("a" to 1)), merge = true) }
        val outcome = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(before, outcome.after, "inställningarna är lika på allt konverteraren skrev")
        assertEquals(1985L, asDoc(store.read(Paths.settings(uid), "app")?.get("profile"))["birthYear"], "konverterarens värde står kvar")
    }

    @Test
    fun `write - en avbruten körning fortsätter där den slutade utan att skriva om det som redan finns`() = test {
        val episodes = (0 until 25).map { mapOf("id" to "ep%02d".format(it), "typ" to "Förkylning", "start_datum" to "2026-01-01", "slut_datum" to "", "timestamp" to 0L) }
        room.read = LegacyRoomRead.Tables(mapOf(LegacyRoomSchema.SJUKDOMSEPISODER to episodes), 0, FINGERPRINT)
        prefs.values = emptyMap()
        val plan = savedPlan()
        firestore.failAt = 1
        val failed = assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertEquals(1, failed.batch)
        assertEquals(20, failed.after["illnessEpisodes"], "första batchen hann in")
        firestore.failAt = null
        firestore.batches.clear()
        val retry = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        // De 20 första skrevs med kvitto men hann aldrig verifieras – servern har exakt det W-raderna säger, så de verifieras utan att skrivas om (OMB-7).
        assertEquals(listOf(5 + 22 + 1), firestore.batches.map { it.size })
        assertEquals(25, retry.after["illnessEpisodes"])
        assertEquals(emptyMap(), ledger.read(uid).pending)
    }

    @Test
    fun `write - en batch som fick timeout men landade räknas som verifierad nästa gång utan att skrivas om`() = test {
        val plan = savedPlan()
        // Som en timeout: kvittot kom aldrig (ingen rad i liggaren), men dokumenten landade på servern.
        for (document in plan.documents) store.set(document.path.substringBeforeLast('/'), document.path.substringAfterLast('/'), document.data, merge = true)
        val outcome = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(emptyList(), firestore.batches, "ingenting skrivs om")
        assertEquals(before, outcome.after)
        assertEquals(Ledger.EMPTY, ledger.read(uid), "lika redan före första skrivningen (ingen P-rad): inte flyttens – antecknas inte")
        assertFalse(useCase.started())
    }

    @Test
    fun `write - ett fält som tömts i 3x räknas som nytt 3x-värde och tas bort på servern när 4x0 inte rört dokumentet`() = test {
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(savedPlan()))
        val doses = tables.getValue(LegacyRoomSchema.MEDICINER)
        val noted = doses[0]["id"] as String
        assertNotNull(store.read(Paths.doses(uid), noted)?.get("note"), "dosen hade en anteckning i 3.x")
        // 3.x använd igen: anteckningen togs bort. Ett fält utanför samlingens fasta fältmängd på servern ändrar inget.
        store.set(Paths.doses(uid), noted, mapOf("framtida" to true), merge = true)
        val notes = tables.getValue(LegacyRoomSchema.NOTES).filterNot { it["target"] == "MEDICATION" && it["entityId"] == noted }
        room.read = LegacyRoomRead.Tables(tables + (LegacyRoomSchema.NOTES to notes), 0, "ny-fil")
        room.fingerprint = "ny-fil"
        firestore.batches.clear()
        val retry = assertIs<LegacyWriteOutcome.Verified>(useCase.write(savedPlan()))
        val written = firestore.batches.flatten().single()
        assertEquals(noted, written.path.substringAfterLast('/'))
        assertTrue("note" in written.data && written.data["note"] == null, "konverteraren skriver det tömda fältet som null – inte utelämnat")
        val stored = store.read(Paths.doses(uid), noted)!!
        assertNull(stored["note"])
        assertEquals(true, stored["framtida"], "fält utanför fältmängden rörs inte")
        assertEquals(before, retry.after, "verifierat lika igen – hashen räknar det tömda fältet")
    }

    @Test
    fun `withDeletions - ett fält i fältmängden som servern har men 3x inte längre skriver tas bort, även nästlat, resten lämnas`() {
        val tree = DocumentRules.fieldTree("settings")
        val ours = mapOf("name" to "x", "theme" to mapOf("lightStartHour" to 6), "reminders" to mapOf("medsEnabled" to true))
        val stored = mapOf("name" to "y", "note" to "borttagen i 3.x", "theme" to mapOf("lightStartHour" to 7, "mode" to "light"), "framtida" to 1)
        val written = LegacyMigrationUseCase.withDeletions(ours, stored, tree)
        assertSame(RawDocumentWriter.DELETE, written["note"])
        assertSame(RawDocumentWriter.DELETE, asDoc(written["theme"])["mode"], "nästlat fält tas bort inne i gruppen")
        assertEquals(6, asDoc(written["theme"])["lightStartHour"])
        assertEquals(ours["reminders"], written["reminders"])
        assertFalse("framtida" in written, "fält utanför fältmängden rörs inte")
        assertEquals(setOf("name", "theme", "reminders", "note"), written.keys)
    }

    @Test
    fun `write - ett IO-fel i liggaren stannar med egen kod, utan krasch och utan att servern är fel`() = test {
        val plan = savedPlan()
        ledger.fails = true
        val failed = assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertEquals(LegacyMigrationUseCase.LEDGER_FAILED, failed.code)
        assertNull(failed.batch)
        assertEquals(emptyList(), firestore.batches, "inget skrivs när liggaren inte går att läsa")
        assertFalse(pause.paused.value)
        ledger.fails = false
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(32, storedDocuments().size)
    }

    @Test
    fun `write - en omkörning skriver om det migreringen skrivit som 4x0 ändrat eller raderat, men fyller bara i det som fanns före flytten`() = test {
        // Fanns före flytten: inställningar från 4.0 med egna värden.
        store.set(Paths.settings(uid), "app", mapOf("theme" to mapOf("mode" to "light"), "profile" to mapOf("birthYear" to 1979)), merge = true)
        val plan = savedPlan()
        assertEquals(mapOf("settings" to 1), assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan)).existing)
        val dose = expected.first { CollectionNames.collectionOf(it.path) == "doses" }
        val activity = expected.first { CollectionNames.collectionOf(it.path) == "activities" }
        // Lämnas utan bekräftelse; i 4.0 (även från en annan enhet) ändras en dos, raderas en aktivitet, läggs ett eget alternativ till och ändras inställningarna.
        store.set(Paths.doses(uid), dose.path.substringAfterLast('/'), mapOf("note" to "Egen anteckning i 4.0"), merge = true)
        store.delete(Paths.activities(uid), activity.path.substringAfterLast('/'))
        store.set(Paths.options(uid), "eget-alternativ", mapOf("kind" to "activity", "name" to "Yoga"), merge = true)
        store.set(Paths.settings(uid), "app", mapOf("theme" to mapOf("mode" to "dark")), merge = true)
        firestore.batches.clear()

        val retry = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(setOf(dose.path, activity.path), firestore.batches.flatten().map { it.path }.toSet(), "bara det som skiljer sig eller saknas skrivs")
        assertEquals(before - "settings" + ("settings" to 0), retry.after)
        assertEquals(mapOf("settings" to 1), retry.existing)
        assertEquals(dose.data["note"], store.read(Paths.doses(uid), dose.path.substringAfterLast('/'))?.get("note"), "3.x-värdet gäller igen – ändringar under flytten skyddas inte")
        assertNotNull(store.read(Paths.activities(uid), activity.path.substringAfterLast('/')), "raderad under flytten: skrivs igen")
        assertNotNull(store.read(Paths.options(uid), "eget-alternativ"), "4.0:s eget dokument rörs inte")
        assertEquals("dark", asDoc(store.read(Paths.settings(uid), "app")?.get("theme"))["mode"], "fanns före flytten: 4.0:s värden lämnas")
        assertEquals(1979L, asDoc(store.read(Paths.settings(uid), "app")?.get("profile"))["birthYear"])
    }

    @Test
    fun `write - en planerad skrivning som landade sent verifieras utan omskrivning, och en som fick annat innehåll skrivs om`() = test {
        val plan = savedPlan()
        firestore.failAt = 0
        firestore.failWith = DataError.Offline
        assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertEquals(32, ledger.read(uid).planned.size)
        val option = expected.first { CollectionNames.collectionOf(it.path) == "options" }
        for (document in plan.documents) store.set(document.path.substringBeforeLast('/'), document.path.substringAfterLast('/'), document.data, merge = true)
        store.set(Paths.options(uid), option.path.substringAfterLast('/'), mapOf("name" to "Eget i 4.0", "framtida" to 1), merge = true)
        firestore.failAt = null
        firestore.batches.clear()
        val retry = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(listOf(option.path), firestore.batches.flatten().map { it.path }, "bara det som skiljer sig skrivs om")
        assertEquals(before, retry.after)
        assertEquals(option.data["name"], store.read(Paths.options(uid), option.path.substringAfterLast('/'))?.get("name"))
        assertEquals(1L, store.read(Paths.options(uid), option.path.substringAfterLast('/'))?.get("framtida"), "fält utanför fältmängden rörs inte")
        assertEquals(32, ledger.read(uid).verified.size)
        assertEquals(emptyMap(), ledger.read(uid).planned)
    }

    @Test
    fun `write - en incheckning vars episod saknas på servern skrivs i samma batch som episoden, så rules fäller inget`() = test {
        val plan = savedPlan()
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        val episode = expected.first { CollectionNames.collectionOf(it.path) == "illnessEpisodes" }
        val checkin = expected.first { CollectionNames.collectionOf(it.path) == "checkins" && it.path.startsWith(episode.path) }
        store.delete(Paths.illnessEpisodes(uid), episode.path.substringAfterLast('/'))
        store.set(checkin.path.substringBeforeLast('/'), checkin.path.substringAfterLast('/'), mapOf("note" to "Ändrad"), merge = true)
        firestore.batches.clear()
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        val batch = firestore.batches.single()
        assertEquals(listOf(episode.path, checkin.path), batch.map { it.path }, "episoden före sin incheckning, i en batch (existsAfter)")
    }

    @Test
    fun `write - ett liggarfel efter verifieringen räknar inget två gånger`() = test {
        val plan = savedPlan()
        ledger.failsAfter = 3 // läsningen, P och W går, V inte
        val failed = assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertEquals(LegacyMigrationUseCase.LEDGER_FAILED, failed.code)
        assertEquals(before, failed.after, "allt skrivet med kvitto – exakt före, inte dubbelt")
    }

    @Test
    fun `write - en definitivt avvisad batch stryker sina P-rader, men en timeout lämnar dem`() = test {
        val plan = savedPlan()
        firestore.failAt = 0
        firestore.failWith = DataError.QuotaExceeded
        assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertEquals(Ledger.EMPTY, ledger.read(uid))
        firestore.failWith = DataError.Offline
        assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertEquals(32, ledger.read(uid).planned.size, "kan ha landat: står kvar")
    }

    @Test
    fun `write - när inget behöver skrivas läses inget tillbaka, och ett DataStore-fel i kopian blir ett eget skäl utan krasch`() = test {
        val plan = savedPlan()
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        firestore.reads = 0
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(1, firestore.reads, "bara läget – ingen verifieringsläsning för en tom skrivning")
        flag.fails = true
        assertEquals(CopyResult.Failed(CopyFailure.STATE_FAILED), useCase.saveCopy(plan, copyUri))
        assertEquals(CopyStatus.Missing, readyPlan().copy, "läget kan inte läsas: ingen kopia")
        assertEquals(AccountCheck.UNKNOWN, readyPlan().accountCheck, "sessionen kan inte läsas: okänt konto")
    }

    @Test
    fun `isPending - utan Room-fil är svaret falskt före allt annat, även utloggad`() = test {
        room.read = LegacyRoomRead.Missing
        scope.uid.value = null
        assertEquals(false, useCase.isPending().getOrThrow())
    }

    // ── Avbryt flytten (OMB-7) ───────────────────────────────────────────

    @Test
    fun `abort - dokument som fanns före flytten, lika eller olika, är inte flyttens och raderas aldrig`() = test {
        val plan = savedPlan()
        // Som efter en OMB-4-import: en dos finns redan identisk, en aktivitet med andra värden.
        val dose = expected.first { CollectionNames.collectionOf(it.path) == "doses" }
        val activity = expected.first { CollectionNames.collectionOf(it.path) == "activities" }
        store.set(Paths.doses(uid), dose.path.substringAfterLast('/'), dose.data, merge = true)
        store.set(Paths.activities(uid), activity.path.substringAfterLast('/'), mapOf("name" to "Egen"), merge = true)
        val outcome = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertEquals(mapOf("activities" to 1), outcome.existing)
        assertEquals(before - "activities" + ("activities" to 1), outcome.after)
        val recorded = ledger.read(uid)
        assertFalse(dose.path.removePrefix("users/$uid/") in recorded, "lika före första skrivningen: inte flyttens")
        assertFalse(activity.path.removePrefix("users/$uid/") in recorded, "bara ifylld: inte flyttens")
        assertIs<LegacyAbortOutcome.Done>(useCase.abort())
        assertEquals(dose.data["name"], store.read(Paths.doses(uid), dose.path.substringAfterLast('/'))?.get("name"))
        assertEquals("Egen", store.read(Paths.activities(uid), activity.path.substringAfterLast('/'))?.get("name"))
        assertEquals(2, storedDocuments().size, "allt annat flyttat är borta")
        // Bara lika dokument: flytten har inte börjat alls.
        for (document in plan.documents) store.set(document.path.substringBeforeLast('/'), document.path.substringAfterLast('/'), document.data, merge = true)
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertFalse(useCase.started())
        assertEquals(LegacyAbortOutcome.Done(emptyMap()), useCase.abort())
        assertEquals(32, storedDocuments().size)
    }

    @Test
    fun `started - falskt före första skrivningen, sant så fort något antecknats, och isPending tvingar då fram skärmen även utan nät`() = test {
        assertFalse(useCase.started())
        val plan = savedPlan()
        assertFalse(useCase.started(), "kopian är ingen skrivning")
        firestore.failAt = 0
        firestore.failWith = DataError.Offline
        assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertTrue(useCase.started(), "timeout: batchen kan ha landat – P-raderna står kvar, flytten har börjat")
        firestore.offline = true
        assertEquals(true, useCase.isPending().getOrThrow(), "utan nät, utan serverfråga: skärmen visas")
        firestore.offline = false
        firestore.failAt = null
        val verified = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertTrue(useCase.started())
        useCase.confirm(plan, verified).getOrThrow()
        assertFalse(useCase.started(), "bekräftad: liggaren är borta")
    }

    @Test
    fun `abort - tar bort exakt det migreringen skrev, lämnar det som fanns före och 4x0s egna, och rensar liggaren`() = test {
        store.set(Paths.settings(uid), "app", mapOf("theme" to mapOf("mode" to "light")), merge = true)
        store.set(Paths.options(uid), "eget-alternativ", mapOf("kind" to "activity", "name" to "Yoga"), merge = true)
        val plan = savedPlan()
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        val before = storedDocuments()
        assertEquals(33, before.size)
        val done = assertIs<LegacyAbortOutcome.Done>(useCase.abort())
        assertEquals(this@LegacyMigrationUseCaseTest.before - "settings", done.deleted)
        val left = storedDocuments()
        assertEquals(setOf("${Paths.settings(uid)}/app", "${Paths.options(uid)}/eget-alternativ"), left.keys, "bara det som fanns före flytten och 4.0:s eget")
        assertEquals("light", asDoc(left.getValue("${Paths.settings(uid)}/app")["theme"])["mode"])
        assertEquals(6L, asDoc(left.getValue("${Paths.settings(uid)}/app")["theme"])["lightStartHour"], "det ifyllda ligger kvar – aldrig raderat")
        assertTrue(firestore.deletes.flatten().indexOfFirst { "/checkins/" in it } < firestore.deletes.flatten().indexOfFirst { CollectionNames.collectionOf(it) == "illnessEpisodes" }, "incheckningar före episoder")
        assertEquals(Ledger.EMPTY, ledger.read(uid))
        assertFalse(useCase.started())
        assertFalse(pause.paused.value)
        assertEquals(CopyStatus.Verified::class, readyPlan().copy::class, "kopian gäller fortfarande")
        assertEquals(LegacyAbortOutcome.Done(emptyMap()), useCase.abort(), "inget antecknat: klart direkt")
    }

    @Test
    fun `abort - efter ett batchfel tas även det planerade bort, och ett fel eller något som finns kvar lämnar liggaren och ger Försök igen`() = test {
        val plan = savedPlan()
        firestore.failAt = 0
        firestore.failWith = DataError.Offline
        assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        // Batchen landade ändå (timeout) – allt planerat finns på servern.
        for (document in plan.documents) store.set(document.path.substringBeforeLast('/'), document.path.substringAfterLast('/'), document.data, merge = true)
        firestore.deleteFails = DataError.Offline
        val failed = assertIs<LegacyAbortOutcome.Failed>(useCase.abort())
        assertEquals("UNAVAILABLE", failed.code)
        assertTrue(useCase.started(), "liggaren står kvar")
        assertFalse(pause.paused.value)
        // Raderingen går, men ett dokument dyker upp igen (annan enhet) före kontrollen.
        firestore.deleteFails = null
        val dose = expected.first { CollectionNames.collectionOf(it.path) == "doses" }
        firestore.batches += listOf(emptyList()) // tamper slår till vid nästa läsning
        firestore.tamper = { store.set(Paths.doses(uid), dose.path.substringAfterLast('/'), dose.data, merge = true) }
        val incomplete = assertIs<LegacyAbortOutcome.Failed>(useCase.abort())
        assertEquals(LegacyMigrationUseCase.ABORT_INCOMPLETE, incomplete.code)
        assertEquals(mapOf("doses" to 1), incomplete.remaining)
        assertTrue(useCase.started())
        assertIs<LegacyAbortOutcome.Done>(useCase.abort())
        assertEquals(emptyMap(), storedDocuments())
        assertFalse(useCase.started())
    }

    // ── Påminnelserna (OMB-2) ─────────────────────────────────────────────

    @Test
    fun `påminnelserna pausas bara medan write körs - inget utfall och ingen processdöd lämnar dem av`() = test {
        room.read = LegacyRoomRead.WrongVersion(10)
        useCase.read()
        assertFalse(pause.paused.value, "fel version pausar inget")
        room.read = LegacyRoomRead.Tables(tables, 0, FINGERPRINT)
        readyPlan()
        assertFalse(pause.paused.value, "att granska pausar inget")
        val plan = savedPlan()
        assertFalse(pause.paused.value, "kopian pausar inget")
        // Ett fel mitt i skrivningen.
        firestore.failAt = 0
        assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan))
        assertFalse(pause.paused.value, "ett fel släpper pausen")
        firestore.failAt = null
        // En avvikelse.
        val dose = expected.first { CollectionNames.collectionOf(it.path) == "doses" }
        firestore.tamper = { store.delete(Paths.doses(uid), dose.path.substringAfterLast('/')) }
        assertIs<LegacyWriteOutcome.Mismatch>(useCase.write(plan))
        assertFalse(pause.paused.value, "en avvikelse släpper pausen")
        // Skrivet, appen lämnas utan bekräftelse.
        assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        assertFalse(pause.paused.value, "Skrivet utan bekräftelse: inget är pausat")
        // Processdöd: pausen lever bara i minnet och startar som av.
        assertFalse(LegacyMigrationPause().paused.value)
    }

    @Test
    fun `write - skrivspärren och fel konto stoppar före första batchen`() = test {
        val plan = savedPlan()
        scope.setVersion(Schema.CURRENT_VERSION + 1)
        assertEquals(DataError.UpdateRequired, assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan)).error)
        scope.setVersion(Schema.CURRENT_VERSION)
        scope.uid.value = "annan"
        assertEquals(DataError.NotSignedIn, assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan)).error)
        assertEquals(emptyList(), firestore.batches)
    }

    // ── Bekräfta ──────────────────────────────────────────────────────────

    @Test
    fun `confirm - markören skrivs en gång med källa, tid, version och antal, flaggan sätts för kontot och larmen läggs om`() = test {
        val plan = savedPlan()
        val verified = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        useCase.confirm(plan, verified).getOrThrow()
        val marker = assertNotNull(LegacyMigrationCodec.decode(store.read(Paths.USERS, uid)?.get(LegacyMigrationCodec.FIELD)))
        assertEquals(LegacySource.ROOM, marker.source)
        assertEquals(before, marker.counts, "rapportens före")
        assertEquals(AppVersion().name, marker.appVersion)
        assertEquals(kotlin.time.Instant.parse("2026-01-15T20:00:00Z"), marker.sourceCreatedAt)
        assertNotNull(marker.completedAt)
        assertEquals(Schema.CURRENT_VERSION.toLong(), store.read(Paths.USERS, uid)?.get("schemaVersion"), "användardokumentet i övrigt orört")
        assertTrue(uid in flag.done)
        assertFalse(pause.paused.value, "påminnelserna får gå igen – ReminderSync lägger larmen")
        assertEquals(1, ledger.deleted, "liggaren raderas")
        assertEquals(Ledger.EMPTY, ledger.read(uid))
        assertEquals(false, useCase.isPending().getOrThrow())
        // Idempotent: en andra bekräftelse (markören finns) räknas som lyckad.
        assertTrue(useCase.confirm(plan, verified).isSuccess)
        assertEquals(2, firestore.markCalls)
    }

    @Test
    fun `confirm - misslyckas markören och saknas den på servern sätts ingen flagga`() = test {
        val plan = savedPlan()
        val verified = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        firestore.markerFails = DataError.Offline
        assertEquals(DataError.Offline, useCase.confirm(plan, verified).exceptionOrNull())
        assertFalse(uid in flag.done)
        assertFalse(pause.paused.value, "en misslyckad bekräftelse lämnar inte larmen av")
        assertEquals(true, useCase.isPending().getOrThrow(), "nästa start erbjuder migreringen igen – omkörningen är idempotent")
    }

    @Test
    fun `confirm - nekas eller tar markören timeout fast den hann in på servern räknas det som lyckat`() = test {
        val plan = savedPlan()
        val verified = assertIs<LegacyWriteOutcome.Verified>(useCase.write(plan))
        // Som efter en timeout: servern har markören, klienten fick inget kvitto.
        store.set(Paths.USERS, uid, mapOf(LegacyMigrationCodec.FIELD to mapOf("source" to "room", "completedAt" to clock.now())), merge = true)
        firestore.markerFails = DataError.PermissionDenied
        assertTrue(useCase.confirm(plan, verified).isSuccess)
        assertTrue(uid in flag.done)
        assertFalse(pause.paused.value)
    }

    @Test
    fun `write - ett läsfel på Room-filens kontrollsumma stannar som SOURCE_CHANGED utan krasch`() = test {
        val plan = savedPlan()
        room.fingerprintFails = true
        assertEquals(LegacyMigrationUseCase.SOURCE_CHANGED, assertIs<LegacyWriteOutcome.Failed>(useCase.write(plan)).code)
        assertEquals(emptyList(), firestore.batches)
    }
}
