package se.partee71.dagboken.ui.migration

import android.net.Uri
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.legacy.LegacyRoomSchema
import se.partee71.dagboken.core.legacy.Row
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.FakeStore
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.firestore.RawDocumentWriter
import se.partee71.dagboken.data.firestore.RawDocuments
import se.partee71.dagboken.data.legacy.AccountCheck
import se.partee71.dagboken.data.legacy.AppVersion
import se.partee71.dagboken.data.legacy.CopyFailure
import se.partee71.dagboken.data.legacy.CopyRecord
import se.partee71.dagboken.data.legacy.FakeCopyFile
import se.partee71.dagboken.data.legacy.FakeDriveBackups
import se.partee71.dagboken.data.legacy.LegacyImportUseCase
import se.partee71.dagboken.data.legacy.FakeLegacyPreferencesSource
import se.partee71.dagboken.data.legacy.FakeLegacyRoomSource
import se.partee71.dagboken.data.legacy.FakeLegacyWork
import se.partee71.dagboken.data.legacy.FakeMigrationLedger
import se.partee71.dagboken.data.legacy.FakeMigrationState
import se.partee71.dagboken.data.legacy.FakeRawFirestore
import se.partee71.dagboken.data.legacy.LegacyMigrationPause
import se.partee71.dagboken.data.legacy.LegacySession
import se.partee71.dagboken.data.legacy.LegacyMigrationUseCase
import se.partee71.dagboken.data.legacy.LegacyRoomRead
import se.partee71.dagboken.testing.FakeAuthRepository
import se.partee71.dagboken.testing.MainDispatcherRule

/**
 * Migreringens ViewModel (OMB-2, OMB-7, OMB-8, NAV-6) mot det riktiga use caset över migreringens fejkar: ett läge
 * per tavla och varje övergång – startkontrollen (utan Room-fil, offline, "Försök igen", "Inte nu"), granskningen
 * med spärren utan kontrollerad kopia, kopian (kontrollerar, klar, fel, inaktuell), skrivningen med framsteg per
 * batch, klar och bekräftelsen (också misslyckad), avvikelsen, batchfelet, ändrad Room-fil, stopp, äldre version och
 * oläsbar fil. Fast klocka: onsdag 7 oktober 2026 kl. 21:14 i Europe/Stockholm. Syntetisk data.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val uid = "uid-flytt"
    private val zone = TimeZone.of("Europe/Stockholm")
    private val now = LocalDateTime(LocalDate(2026, 10, 7), LocalTime(21, 14))
    private val clock = FixedClock(now.toInstant(zone))
    private val io = StandardTestDispatcher(main.dispatcher.scheduler)

    private val store = FakeStore().apply { set(Paths.USERS, uid, mapOf("schemaVersion" to Schema.CURRENT_VERSION), merge = false) }
    private val firestore = FakeRawFirestore(store)
    private val server = HeldServer(firestore)
    private val room = FakeLegacyRoomSource(tables(episodes = 2))
    // 3.x-sessionen var samma konto, om inte testet säger annat (AccountCheck.SAME).
    private val flag = FakeMigrationState().apply { session = LegacySession(uid) }
    private val copies = FakeCopyFile()
    private val pause = LegacyMigrationPause()
    private val scope = TestUserScope(uid)
    private val useCase = LegacyMigrationUseCase(
        room, FakeLegacyPreferencesSource(), server, server, flag, FakeMigrationLedger(), FakeLegacyWork(), copies, pause, AppVersion(),
        FakeAuthRepository(AuthUser(uid, email = "kim.berg@example.com")), scope, clock, io, io,
    )
    private val copyUri: Uri = Uri.parse("content://test/dagboken-3x-kopia-2026-10-07.json")

    private val drive = FakeDriveBackups()
    private val importer = LegacyImportUseCase(drive, copies, server, server, pause, scope, io)
    private val backupUri: Uri = Uri.parse("content://test/backup.json")

    private fun viewModel() = MigrationViewModel(useCase, importer, clock) { zone }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(main.dispatcher) { block() }

    /** [episodes] episoder med en incheckning var – fler än 20 delas på flera batchar. */
    private fun tables(episodes: Int, fingerprint: String = FINGERPRINT, orphan: Boolean = false): LegacyRoomRead.Tables {
        val ids = (1..episodes).map { "episod-$it" }
        val checkins = ids.map { checkin(it, "in-$it") } + listOfNotNull(checkin("saknas", "in-utan-episod").takeIf { orphan })
        return LegacyRoomRead.Tables(
            mapOf(LegacyRoomSchema.SJUKDOMSEPISODER to ids.map(::episode), LegacyRoomSchema.SJUKDOMS_INCHECKNINGAR to checkins),
            lastModifiedMillis = 1_790_000_000_000,
            fingerprint = fingerprint,
        )
    }

    private fun episode(id: String): Row = mapOf("start_datum" to "2026-09-20", "slut_datum" to "2026-09-27", "typ" to "Halsfluss", "id" to id, "timestamp" to 1L)

    private fun checkin(episode: String, id: String): Row =
        mapOf("episod_id" to episode, "id" to id, "tid" to "07:30", "datum" to "2026-09-21", "symptom" to "", "svarighetsgrad" to 3L, "somatiska" to 0L, "timestamp" to 1L)

    /** Väntar ut lägen som inte är [T] (t.ex. det första "Läser") och ger nästa av typen. */
    private suspend inline fun <reified T : MigrationStage> ReceiveTurbine<MigrationUiState>.next(): T {
        while (true) {
            val stage = awaitItem().stage
            if (stage is T) return stage
        }
    }

    /** Läser in granskningen och sparar en kontrollerad kopia. */
    private suspend fun ReceiveTurbine<MigrationUiState>.savedCopy(vm: MigrationViewModel): MigrationStage.Review {
        next<MigrationStage.Review>()
        vm.onEvent(MigrationEvent.CopyChosen(copyUri))
        while (true) {
            val review = next<MigrationStage.Review>()
            if (review.copy is CopyStep.Saved) return review
        }
    }

    private fun repoRoot() = java.io.File("").absoluteFile.let { if (it.name == "app") it.parentFile else it }

    private companion object {
        const val FINGERPRINT = "sha256-flytt"
    }

    // ── Startkontrollen (NAV-6) ───────────────────────────────────────────

    @Test
    fun `utan Room-fil med flaggan satt släpps användaren in direkt`() = test {
        room.read = LegacyRoomRead.Missing
        flag.done += uid
        viewModel().state.test { assertEquals(MigrationStage.Closed(), next<MigrationStage.Closed>()) }
    }

    // ── Fallbacken utan Room-fil (OMB-5) ──────────────────────────────────

    @Test
    fun `utan Room-fil, flagga och markör frågar första starten, och Börja tomt sätter flaggan och släpper in`() = test {
        room.read = LegacyRoomRead.Missing
        val vm = viewModel()
        vm.state.test {
            assertEquals(MigrationStage.Fallback(ImportStage.Choose), next<MigrationStage.Fallback>())
            vm.onEvent(MigrationEvent.StartEmpty)
            assertEquals(MigrationStage.Closed(), next<MigrationStage.Closed>())
        }
        assertTrue(uid in flag.done)
        assertEquals(emptyList(), firestore.batches)
        viewModel().state.test { assertEquals(MigrationStage.Closed(), next<MigrationStage.Closed>()) }
    }

    @Test
    fun `utan Room-fil och utan nät släpps användaren in, och nästa start frågar igen`() = test {
        room.read = LegacyRoomRead.Missing
        firestore.offline = true
        viewModel().state.test { assertEquals(MigrationStage.Closed(), next<MigrationStage.Closed>()) }
        assertFalse(uid in flag.done)
        firestore.offline = false
        viewModel().state.test { next<MigrationStage.Fallback>() }
    }

    @Test
    fun `utan Room-fil men med data i kontot (ny telefon) frågas ingenting`() = test {
        room.read = LegacyRoomRead.Missing
        store.set("${Paths.user(uid)}/doses", "d1", mapOf("date" to "2026-10-07"), merge = false)
        viewModel().state.test { assertEquals(MigrationStage.Closed(), next<MigrationStage.Closed>()) }
        assertTrue(uid in flag.done)
    }

    @Test
    fun `utan Room-fil men med markören på servern frågas ingenting`() = test {
        room.read = LegacyRoomRead.Missing
        store.set(Paths.USERS, uid, mapOf("legacyMigration" to mapOf("source" to "room")), merge = true)
        viewModel().state.test { assertEquals(MigrationStage.Closed(), next<MigrationStage.Closed>()) }
        assertTrue(uid in flag.done)
    }

    @Test
    fun `fallbacken - ingen backup på Drive visar notisen, filen granskas, bekräftas, skrivs och flaggan sätts när den är klar`() = test {
        room.read = LegacyRoomRead.Missing
        copies.files[backupUri] = java.io.File(repoRoot(), "tools/db/test/fixtures/legacy/backup-v2.json").readText()
        val vm = viewModel()
        vm.state.test {
            next<MigrationStage.Fallback>()
            vm.onEvent(MigrationEvent.Import(ImportEvent.FromDrive))
            while (next<MigrationStage.Fallback>().import != ImportStage.NoDriveBackup) Unit
            vm.onEvent(MigrationEvent.Import(ImportEvent.FileChosen(backupUri)))
            var stage = next<MigrationStage.Fallback>().import
            while (stage !is ImportStage.Review) stage = next<MigrationStage.Fallback>().import
            assertEquals(3, stage.counts["prescriptions"])
            vm.onEvent(MigrationEvent.Import(ImportEvent.Import))
            assertTrue((next<MigrationStage.Fallback>().import as ImportStage.Review).confirming)
            vm.onEvent(MigrationEvent.Import(ImportEvent.Confirm))
            while (next<MigrationStage.Fallback>().import !is ImportStage.Done) Unit
            assertTrue(uid in flag.done, "en klar import räknas som svaret – frågan kommer inte igen")
            vm.onEvent(MigrationEvent.StartEmpty)
            assertEquals(MigrationStage.Closed(), next<MigrationStage.Closed>())
        }
        assertTrue(firestore.batches.isNotEmpty())
    }

    @Test
    fun `offline vid startkontrollen - Ingen anslutning, Försök igen kontrollerar igen och läser in granskningen`() = test {
        firestore.offline = true
        val vm = viewModel()
        vm.state.test {
            assertEquals(MigrationStage.CheckFailed(DataError.Offline), next<MigrationStage.CheckFailed>())
            firestore.offline = false
            vm.onEvent(MigrationEvent.Retry)
            assertEquals(MigrationStage.Reading, next<MigrationStage.Reading>())
            next<MigrationStage.Review>()
        }
    }

    @Test
    fun `Inte nu släpper in utan att något skrivs, och nästa start erbjuder migreringen igen`() = test {
        firestore.offline = true
        val offline = viewModel()
        offline.state.test {
            next<MigrationStage.CheckFailed>()
            offline.onEvent(MigrationEvent.NotNow)
            assertEquals(MigrationStage.Closed(openImport = false), next<MigrationStage.Closed>())
        }
        firestore.offline = false
        val review = viewModel()
        review.state.test {
            next<MigrationStage.Review>()
            review.onEvent(MigrationEvent.NotNow)
            assertEquals(MigrationStage.Closed(), next<MigrationStage.Closed>())
        }
        assertEquals(emptyList(), firestore.batches)
        assertFalse(uid in flag.done)
        viewModel().state.test { next<MigrationStage.Review>() }
    }

    // ── Granskningen och kopian (OMB-8) ───────────────────────────────────

    @Test
    fun `granskningen visar antal per samling, kontot med varning, påminnelserna och att kopian saknas`() = test {
        flag.session = LegacySession("uid-3x")
        viewModel().state.test {
            assertEquals(MigrationStage.Reading, next<MigrationStage.Reading>())
            val review = next<MigrationStage.Review>()
            assertEquals(2, review.counts["illnessEpisodes"])
            assertEquals(2, review.counts["checkins"])
            assertEquals(1, review.counts["settings"])
            assertEquals("kim.berg@example.com", review.accountEmail)
            assertEquals(AccountCheck.DIFFERENT, review.accountCheck, "3.x-sessionen var ett annat konto")
            assertEquals(CopyStep.Missing, review.copy)
            assertEquals("dagboken-3x-kopia-2026-10-07.json", review.copyFileName)
            assertFalse(review.canMove)
        }
    }

    @Test
    fun `spärren - Flytta utan kontrollerad kopia gör ingenting och skriver inget`() = test {
        val vm = viewModel()
        vm.state.test {
            val review = next<MigrationStage.Review>()
            vm.onEvent(MigrationEvent.Move)
            expectNoEvents()
            assertEquals(review, vm.state.value.stage)
        }
        assertEquals(emptyList(), firestore.batches)
    }

    @Test
    fun `kopian - Kontrollerar och sedan Kontrollerad med filnamn, tid och antal, och Flytta blir aktiv`() = test {
        val vm = viewModel()
        vm.state.test {
            next<MigrationStage.Review>()
            vm.onEvent(MigrationEvent.CopyChosen(copyUri))
            assertEquals(CopyStep.Checking, next<MigrationStage.Review>().copy)
            val saved = next<MigrationStage.Review>()
            assertEquals(CopyStep.Saved("dagboken-3x.json", now, 27), saved.copy)
            assertTrue(saved.canMove)
        }
    }

    @Test
    fun `kopian som inte gick visar skälet och kan sparas igen`() = test {
        copies.writeFails = true
        val vm = viewModel()
        vm.state.test {
            next<MigrationStage.Review>()
            vm.onEvent(MigrationEvent.CopyChosen(copyUri))
            assertEquals(CopyStep.Checking, next<MigrationStage.Review>().copy)
            val failed = next<MigrationStage.Review>()
            assertEquals(CopyStep.Failed(CopyFailure.WRITE_FAILED), failed.copy)
            assertFalse(failed.canMove)
            copies.writeFails = false
            vm.onEvent(MigrationEvent.CopyChosen(copyUri))
            assertEquals(CopyStep.Checking, next<MigrationStage.Review>().copy)
            assertIs<CopyStep.Saved>(next<MigrationStage.Review>().copy)
        }
    }

    @Test
    fun `en kopia av en äldre Room-fil är inaktuell och spärrar flytten`() = test {
        flag.copyRecord = CopyRecord("sha256-tidigare", Instant.parse("2026-10-04T22:30:00Z"), "kopia.json", 27)
        viewModel().state.test {
            val review = next<MigrationStage.Review>()
            assertEquals(CopyStep.Stale(LocalDate(2026, 10, 5)), review.copy, "datumet i Europe/Stockholm")
            assertFalse(review.canMove)
        }
    }

    // ── Skriva, kontrollera och bekräfta ──────────────────────────────────

    @Test
    fun `flytten - framsteg per batch, sedan Klar, och Bekräfta skriver markören och släpper in`() = test {
        room.read = tables(episodes = 25)
        server.held = true
        val vm = viewModel()
        vm.state.test {
            savedCopy(vm)
            vm.onEvent(MigrationEvent.Move)
            val started = next<MigrationStage.Writing>()
            assertEquals(25, started.before["illnessEpisodes"])
            assertNull(started.done, "läget på servern läses först")
            server.permits.send(Unit)
            assertEquals(started.before.mapValues { 0 }, next<MigrationStage.Writing>().done, "servern läst: första framsteget med nollor")
            server.permits.send(Unit)
            assertEquals(20, next<MigrationStage.Writing>().done?.get("illnessEpisodes"), "första batchen: högst 20 episoder")
            server.permits.send(Unit)
            server.permits.send(Unit) // verifieringen läser tillbaka
            val written = next<MigrationStage.Written>()
            assertEquals(written.before, written.after)
            assertFalse(written.confirming)
            vm.onEvent(MigrationEvent.Confirm)
            assertTrue(next<MigrationStage.Written>().confirming)
            vm.onEvent(MigrationEvent.Confirm)
            server.permits.send(Unit)
            assertEquals(MigrationStage.Closed(), next<MigrationStage.Closed>())
        }
        assertEquals(1, firestore.markCalls, "ett andra tryck under bekräftelsen gör ingenting")
        assertTrue(uid in flag.done)
        assertFalse(pause.paused.value, "påminnelserna på igen efter bekräftelsen")
    }

    @Test
    fun `en bekräftelse som misslyckas visar felet, och knappen kan tryckas igen`() = test {
        firestore.markerFails = DataError.Offline
        val vm = viewModel()
        vm.state.test {
            savedCopy(vm)
            vm.onEvent(MigrationEvent.Move)
            next<MigrationStage.Written>()
            vm.onEvent(MigrationEvent.Confirm)
            var shown = awaitItem()
            while (shown.failure == null) shown = awaitItem()
            assertEquals(DataError.Offline, shown.failure?.error)
            assertEquals(MigrationStage.Written::class, shown.stage::class)
            assertFalse((shown.stage as MigrationStage.Written).confirming)
            vm.onEvent(MigrationEvent.ErrorShown)
            assertNull(awaitItem().failure)
            firestore.markerFails = null
            vm.onEvent(MigrationEvent.Confirm)
            next<MigrationStage.Closed>()
        }
        assertTrue(uid in flag.done)
    }

    @Test
    fun `avvikelse mot servern - Försök igen skriver bara om det overifierade och blir Klar`() = test {
        val vm = viewModel()
        vm.state.test {
            savedCopy(vm)
            firestore.tamper = { store.delete(Paths.illnessEpisodes(uid), "episod-1") }
            vm.onEvent(MigrationEvent.Move)
            val mismatch = next<MigrationStage.Mismatch>()
            assertEquals(mapOf("illnessEpisodes" to 1), mismatch.mismatched)
            assertEquals(1, mismatch.accounted["illnessEpisodes"])
            firestore.batches.clear()
            vm.onEvent(MigrationEvent.Retry)
            val written = next<MigrationStage.Written>()
            assertEquals(written.before, accounted(written.after, written.existing))
        }
        assertEquals(listOf(listOf("users/$uid/illnessEpisodes/episod-1")), firestore.batches.map { batch -> batch.map { it.path } }, "bara den overifierade episoden")
    }

    @Test
    fun `fanns redan - ett dokument från 40 med andra värden fylls bara i och räknas som fanns redan`() = test {
        store.set(Paths.settings(uid), "app", mapOf("theme" to mapOf("mode" to "light")), merge = false)
        val vm = viewModel()
        vm.state.test {
            savedCopy(vm)
            vm.onEvent(MigrationEvent.Move)
            val written = next<MigrationStage.Written>()
            assertEquals(mapOf("settings" to 1), written.existing)
            assertEquals(0, written.after["settings"])
            assertEquals(written.before, accounted(written.after, written.existing), "före = efter + existing")
        }
    }

    // ── Pausen gäller bara medan write() körs (OMB-2) ────────────────────

    @Test
    fun `pausen släpps när flytten stannat - en avvikelse eller ett skrivet resultat lämnar aldrig påminnelserna av`() = test {
        val vm = viewModel()
        vm.state.test {
            savedCopy(vm)
            firestore.tamper = { store.delete(Paths.illnessEpisodes(uid), "episod-1") }
            vm.onEvent(MigrationEvent.Move)
            next<MigrationStage.Mismatch>()
            assertFalse(pause.paused.value, "flytten har stannat: inget är pausat medan användaren väljer")
            vm.onEvent(MigrationEvent.Retry)
            next<MigrationStage.Written>()
            assertFalse(pause.paused.value, "skrivet men inte bekräftat: appen får lämnas (bakgrund, processdöd) utan att larmen saknas")
        }
        assertFalse(pause.paused.value)
    }

    @Test
    fun `ett batchfel visar felkoden och batchen, och Försök igen kör om utan dubbletter`() = test {
        firestore.failAt = 0
        val vm = viewModel()
        vm.state.test {
            savedCopy(vm)
            vm.onEvent(MigrationEvent.Move)
            assertEquals(MigrationStage.WriteFailed(DataError.PermissionDenied, 0, "PERMISSION_DENIED", started = false), next<MigrationStage.WriteFailed>(), "avvisad första batch: inget skrivet, Inte nu finns kvar")
            firestore.failAt = null
            vm.onEvent(MigrationEvent.Retry)
            next<MigrationStage.Written>()
        }
        assertEquals(1, firestore.batches.size)
    }

    @Test
    fun `ändras Room-filen efter kopian leder flytten tillbaka till granskningen med en inaktuell kopia`() = test {
        val vm = viewModel()
        vm.state.test {
            savedCopy(vm)
            room.read = tables(episodes = 2, fingerprint = "sha256-3x-igen")
            room.fingerprint = "sha256-3x-igen"
            vm.onEvent(MigrationEvent.Move)
            val review = next<MigrationStage.Review>()
            assertEquals(CopyStep.Stale(LocalDate(2026, 10, 7)), review.copy)
            assertFalse(review.canMove)
        }
        assertEquals(emptyList(), firestore.batches)
    }

    @Test
    fun `okänt 3x-konto - Flytta är spärrad tills kontot kryssats i, även med kontrollerad kopia`() = test {
        flag.session = null
        val vm = viewModel()
        vm.state.test {
            val first = next<MigrationStage.Review>()
            assertEquals(AccountCheck.UNKNOWN, first.accountCheck)
            vm.onEvent(MigrationEvent.ConfirmAccount(true))
            assertTrue(next<MigrationStage.Review>().accountConfirmed)
            vm.onEvent(MigrationEvent.CopyChosen(copyUri))
            var review = next<MigrationStage.Review>()
            while (review.copy !is CopyStep.Saved) review = next()
            assertTrue(review.accountConfirmed, "krysset står kvar när granskningen byggs om efter kopian")
            assertTrue(review.canMove)
            vm.onEvent(MigrationEvent.ConfirmAccount(false))
            val unchecked = next<MigrationStage.Review>()
            assertFalse(unchecked.canMove, "kopian är kontrollerad men kontot inte bekräftat")
            vm.onEvent(MigrationEvent.Move)
            expectNoEvents()
            vm.onEvent(MigrationEvent.ConfirmAccount(true))
            assertTrue(next<MigrationStage.Review>().canMove)
            vm.onEvent(MigrationEvent.Move)
            next<MigrationStage.Written>()
        }
    }

    @Test
    fun `med känt 3x-konto ändrar krysset ingenting`() = test {
        val vm = viewModel()
        vm.state.test {
            val review = next<MigrationStage.Review>()
            assertEquals(AccountCheck.SAME, review.accountCheck)
            vm.onEvent(MigrationEvent.ConfirmAccount(true))
            expectNoEvents()
        }
    }

    @Test
    fun `påbörjad flytt - Inte nu gör ingenting, nästa start tvingar fram skärmen, och Avbryt flytten tar bort det skrivna och visar granskningen igen`() = test {
        val first = viewModel()
        first.state.test {
            savedCopy(first)
            first.onEvent(MigrationEvent.Move)
            val written = next<MigrationStage.Written>()
            first.onEvent(MigrationEvent.NotNow)
            expectNoEvents()
            assertEquals(written, first.state.value.stage, "Inte nu finns inte när flytten börjat")
        }
        // Nästa start – även utan nät – visar granskningen med "börjat"; sedan avbryts flytten.
        firestore.offline = true
        val again = viewModel()
        again.state.test {
            val review = next<MigrationStage.Review>()
            assertTrue(review.started)
            again.onEvent(MigrationEvent.NotNow)
            expectNoEvents()
            firestore.offline = false
            again.onEvent(MigrationEvent.Abort)
            assertEquals(MigrationStage.Aborting, next<MigrationStage.Aborting>())
            val back = next<MigrationStage.Review>()
            assertFalse(back.started, "liggaren rensad: Inte nu finns igen")
            assertIs<CopyStep.Saved>(back.copy)
            again.onEvent(MigrationEvent.NotNow)
            next<MigrationStage.Closed>()
        }
        assertEquals(emptyMap(), store.documents.value.filterKeys { it != Paths.USERS }.filterValues { it.isNotEmpty() }, "allt flyttat är borta")
        assertFalse(uid in flag.done)
    }

    @Test
    fun `omkörning - det migreringen skrivit skrivs om när 40 ändrat eller raderat det, och klar räknar allt`() = test {
        val first = viewModel()
        first.state.test {
            savedCopy(first)
            first.onEvent(MigrationEvent.Move)
            next<MigrationStage.Written>()
        }
        // Mellan starterna: raderar en episod och ändrar den andra (skyddas inte under flytten).
        store.delete(Paths.illnessEpisodes(uid), "episod-1")
        val changed = store.read(Paths.illnessEpisodes(uid), "episod-2")!!.entries.first { it.value is String }.key
        store.set(Paths.illnessEpisodes(uid), "episod-2", mapOf(changed to "Ändrad i 4.0"), merge = true)
        val again = viewModel()
        again.state.test {
            assertTrue(next<MigrationStage.Review>().started)
            again.onEvent(MigrationEvent.Move)
            val written = next<MigrationStage.Written>()
            assertEquals(written.before, written.after, "allt lika igen")
        }
        assertNotNull(store.read(Paths.illnessEpisodes(uid), "episod-1"))
        assertFalse(store.read(Paths.illnessEpisodes(uid), "episod-2")?.get(changed) == "Ändrad i 4.0")
    }

    @Test
    fun `ett misslyckat avbrytande visar felet och behåller läget`() = test {
        val vm = viewModel()
        vm.state.test {
            savedCopy(vm)
            vm.onEvent(MigrationEvent.Move)
            val written = next<MigrationStage.Written>()
            firestore.deleteFails = DataError.Offline
            vm.onEvent(MigrationEvent.Abort)
            next<MigrationStage.Aborting>()
            var shown = awaitItem()
            while (shown.failure == null) shown = awaitItem()
            assertEquals(DataError.Offline, shown.failure?.error)
            assertEquals(written, shown.stage)
        }
    }

    // ── Stopp, äldre version, oläsbart ────────────────────────────────────

    @Test
    fun `stopp - rapporten per samling och fält utan innehåll, och Försök igen läser om`() = test {
        room.read = tables(episodes = 2, orphan = true)
        val vm = viewModel()
        vm.state.test {
            val stopped = next<MigrationStage.Stopped>()
            assertEquals(listOf(ReportLine("checkins", "episodId", "episoden finns inte i backupen", 1)), stopped.report)
            room.read = tables(episodes = 2)
            vm.onEvent(MigrationEvent.Retry)
            next<MigrationStage.Review>()
        }
        assertEquals(emptyList(), firestore.batches)
    }

    @Test
    fun `äldre databasversion - Importera backup släpper in till Export och import`() = test {
        room.read = LegacyRoomRead.WrongVersion(10)
        val vm = viewModel()
        vm.state.test {
            assertEquals(MigrationStage.WrongVersion(10), next<MigrationStage.WrongVersion>())
            vm.onEvent(MigrationEvent.ImportBackup)
            assertEquals(MigrationStage.Closed(openImport = true), next<MigrationStage.Closed>())
        }
    }

    @Test
    fun `oläsbar fil - eget läge, och Försök igen läser om`() = test {
        room.fails = true
        val vm = viewModel()
        vm.state.test {
            assertEquals(MigrationStage.ReadFailed(null), next<MigrationStage.ReadFailed>())
            room.fails = false
            vm.onEvent(MigrationEvent.Retry)
            next<MigrationStage.Review>()
        }
    }

    @Test
    fun `rapportens sökväg ger samlingen utan id`() {
        assertEquals("prescriptions", reportCollection("prescriptions/6f1c2a9e"))
        assertEquals("checkins", reportCollection("illnessEpisodes/flu/checkins/c1"))
        assertEquals("options", reportCollection("options/activity#3"))
        assertEquals("notes", reportCollection("notes"))
    }
}

/**
 * Servern över [inner] som med [held] väntar på en [permits] före varje läsning av målens läge, varje batch och
 * markören – för lägena mellan stegen.
 */
private class HeldServer(private val inner: FakeRawFirestore) : RawDocumentWriter, RawDocuments by inner {
    val permits = Channel<Unit>(Channel.UNLIMITED)
    var held = false

    private suspend fun permit() {
        if (held) permits.receive()
    }

    override suspend fun documents(paths: Collection<String>): Map<String, Doc> = permit().let { inner.documents(paths) }

    override suspend fun writeBatch(documents: List<ExportFormat.Document>): Result<Unit> = permit().let { inner.writeBatch(documents) }

    override suspend fun deleteBatch(paths: List<String>): Result<Unit> = permit().let { inner.deleteBatch(paths) }

    override suspend fun markLegacyMigration(uid: String, marker: Doc): Result<Unit> = permit().let { inner.markLegacyMigration(uid, marker) }
}
