package se.partee71.dagboken.ui.settings

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import se.partee71.dagboken.core.legacy.ImportFormat
import se.partee71.dagboken.core.model.LegacySource
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.FakeStore
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.export.ExportUseCase
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.legacy.DriveBackupFile
import se.partee71.dagboken.data.legacy.DriveRead
import se.partee71.dagboken.data.legacy.FakeCopyFile
import se.partee71.dagboken.data.legacy.FakeDriveBackups
import se.partee71.dagboken.data.legacy.FakeRawFirestore
import se.partee71.dagboken.data.legacy.LegacyImportUseCase
import se.partee71.dagboken.data.legacy.LegacyMigrationPause
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.migration.ImportEvent
import se.partee71.dagboken.ui.migration.ImportStage

/**
 * Export och import (BCK-6, BCK-13, BCK-14, SET-8) mot de riktiga use casen över fejkarna (Drive och SAF som fejkar):
 * exporten med snackbarens antal och fel, importens lägen från valen via granskningen och bekräftelsen till klar,
 * Drive utan backup, med samtycke (ja och nej) och utan nät, stoppet med "Välj en annan fil", fel fil och "Försök igen"
 * efter ett batchfel. Fast klocka; syntetisk data.
 */
@RunWith(RobolectricTestRunner::class)
class ExportImportViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val uid = "uid-export-import"
    private val io = StandardTestDispatcher(main.dispatcher.scheduler)
    private val repoRoot = File("").absoluteFile.let { if (it.name == "app") it.parentFile else it }
    private val backupV2 = File(repoRoot, "tools/db/test/fixtures/legacy/backup-v2.json").readText()

    private val store = FakeStore().apply { set(Paths.USERS, uid, mapOf("schemaVersion" to Schema.CURRENT_VERSION), merge = false) }
    private val firestore = FakeRawFirestore(store)
    private val drive = FakeDriveBackups()
    private val files = FakeCopyFile()
    private val scope = TestUserScope(uid)
    private val exporter = ExportUseCase(firestore, files, scope, FixedClock(Instant.parse("2026-10-07T19:14:00Z")), io)
    private val importer = LegacyImportUseCase(drive, files, firestore, firestore, LegacyMigrationPause(), scope, io)
    private val fileUri: Uri = Uri.parse("content://test/backup.json")
    private val exportUri: Uri = Uri.parse("content://test/dagboken-export-2026-10-07.json")

    private fun viewModel() = ExportImportViewModel(exporter, importer) { TimeZone.of("Europe/Stockholm") }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(main.dispatcher) { block() }

    /** Väntar ut lägen som inte är [T] och ger nästa importläge av typen. */
    private suspend inline fun <reified T : ImportStage> ReceiveTurbine<ExportImportUiState>.next(): T {
        while (true) {
            val stage = awaitItem().import
            if (stage is T) return stage
        }
    }

    @Test
    fun `Spara som JSON - filnamnet har dagens datum, exporten ger snackbaren med antal och den nollställs`() = test {
        store.set("${Paths.user(uid)}/doses", "d1", mapOf("date" to "2026-10-07"), merge = false)
        val vm = viewModel()
        assertEquals("dagboken-export-2026-10-07.json", vm.exportFileName())
        vm.state.test {
            assertEquals(ExportImportUiState(), awaitItem())
            vm.onEvent(ExportImportEvent.ExportChosen(exportUri))
            assertTrue(awaitItem().exporting)
            val done = awaitItem()
            assertFalse(done.exporting)
            assertEquals(1, done.exported)
            vm.onEvent(ExportImportEvent.MessageShown)
            assertNull(awaitItem().exported)
        }
        assertTrue(files.files.getValue(exportUri).contains("\"documents\""))
    }

    @Test
    fun `exporten utan nät visar felet som snackbar och skriver ingen fil`() = test {
        firestore.offline = true
        val vm = viewModel()
        vm.state.test {
            skipItems(1)
            vm.onEvent(ExportImportEvent.ExportChosen(exportUri))
            // "Sparar …" kan slås ihop med felet (inget väntar när kön inte når servern) – vänta in slutläget.
            var state = awaitItem()
            while (state.exporting) state = awaitItem()
            assertEquals(DataError.Offline, state.failure?.error)
            assertNull(state.exported)
        }
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun `import från fil - granskning, bekräftelse med antal, skrivning och klar, sedan tillbaka till valen`() = test {
        files.files[fileUri] = backupV2
        val vm = viewModel()
        vm.state.test {
            vm.onEvent(ExportImportEvent.Import(ImportEvent.FileChosen(fileUri)))
            assertEquals(ImportStage.Reading(LegacySource.JSON), next<ImportStage.Reading>())
            val review = next<ImportStage.Review>()
            assertEquals(ImportFormat.Legacy(2), review.format)
            assertEquals(LegacySource.JSON, review.source)
            assertEquals(2, review.counts["illnessEpisodes"])
            assertTrue(review.canImport)
            assertFalse(review.confirming)

            vm.onEvent(ExportImportEvent.Import(ImportEvent.Import))
            assertTrue(next<ImportStage.Review>().confirming, "ConfirmDialog: Importera N poster?")
            vm.onEvent(ExportImportEvent.Import(ImportEvent.Dismiss))
            assertFalse(next<ImportStage.Review>().confirming)
            assertTrue(firestore.batches.isEmpty(), "inget skrivs före bekräftelsen")

            vm.onEvent(ExportImportEvent.Import(ImportEvent.Import))
            next<ImportStage.Review>()
            vm.onEvent(ExportImportEvent.Import(ImportEvent.Confirm))
            assertEquals(null, next<ImportStage.Writing>().done, "först läses läget på servern")
            val done = next<ImportStage.Done>()
            assertEquals(review.counts, done.before)
            assertEquals(review.counts, done.after)

            vm.onEvent(ExportImportEvent.Import(ImportEvent.Reset))
            next<ImportStage.Choose>()
        }
    }

    @Test
    fun `Drive - ingen backup ger notisen, samtycke nej tillbaka till valen, ja läser igen och utan nät Försök igen`() = test {
        val consent = PendingIntent.getActivity(RuntimeEnvironment.getApplication(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val vm = viewModel()
        vm.state.test {
            vm.onEvent(ExportImportEvent.Import(ImportEvent.FromDrive))
            next<ImportStage.NoDriveBackup>()

            drive.read = DriveRead.NeedsConsent(consent)
            vm.onEvent(ExportImportEvent.Import(ImportEvent.FromDrive))
            assertEquals(consent, next<ImportStage.DriveConsent>().consent)
            vm.onEvent(ExportImportEvent.Import(ImportEvent.DriveConsent(granted = false)))
            next<ImportStage.Choose>()

            vm.onEvent(ExportImportEvent.Import(ImportEvent.FromDrive))
            next<ImportStage.DriveConsent>()
            drive.read = DriveRead.Failed(DataError.Offline)
            vm.onEvent(ExportImportEvent.Import(ImportEvent.DriveConsent(granted = true)))
            assertEquals(ImportStage.ReadFailed(LegacySource.DRIVE, DataError.Offline), next<ImportStage.ReadFailed>())

            drive.read = DriveRead.Found(DriveBackupFile("id", "dagboken-backup-20260115-2100.json", ""), backupV2)
            vm.onEvent(ExportImportEvent.Import(ImportEvent.Retry))
            assertEquals(LegacySource.DRIVE, next<ImportStage.Review>().source)
        }
        assertEquals(5, drive.calls)
    }

    @Test
    fun `stopp och fel fil visar bara rapporten, skriver ingenting, och Välj en annan fil läser den nya`() = test {
        files.files[fileUri] = """{"documents":[{"path":"users/annan/okand/x","data":{}}]}"""
        val other = Uri.parse("content://test/annan.json")
        files.files[other] = "inte json"
        val vm = viewModel()
        vm.state.test {
            vm.onEvent(ExportImportEvent.Import(ImportEvent.FileChosen(fileUri)))
            val stopped = next<ImportStage.Stopped>()
            assertEquals(listOf("path"), stopped.report.map { it.field })
            vm.onEvent(ExportImportEvent.Import(ImportEvent.FileChosen(other)))
            next<ImportStage.NotABackup>()
        }
        assertTrue(firestore.batches.isEmpty())
    }

    @Test
    fun `ett batchfel visar felkoden och Försök igen skriver resten utan ny bekräftelse`() = test {
        files.files[fileUri] = backupV2
        firestore.failAt = 0
        val vm = viewModel()
        vm.state.test {
            vm.onEvent(ExportImportEvent.Import(ImportEvent.FileChosen(fileUri)))
            next<ImportStage.Review>()
            vm.onEvent(ExportImportEvent.Import(ImportEvent.Import))
            next<ImportStage.Review>()
            vm.onEvent(ExportImportEvent.Import(ImportEvent.Confirm))
            val failed = next<ImportStage.WriteFailed>()
            assertEquals("PERMISSION_DENIED", failed.code)
            assertEquals(DataError.PermissionDenied, failed.error)
            firestore.failAt = null
            vm.onEvent(ExportImportEvent.Import(ImportEvent.Retry))
            assertIs<ImportStage.Done>(next<ImportStage.Done>())
        }
    }
}
