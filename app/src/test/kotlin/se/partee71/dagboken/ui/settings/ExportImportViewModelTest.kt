package se.partee71.dagboken.ui.settings

import android.net.Uri
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
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
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.FakeStore
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.export.ExportUseCase
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.importing.FakeRawFirestore
import se.partee71.dagboken.data.importing.FakeUserFile
import se.partee71.dagboken.data.importing.ImportFixtures
import se.partee71.dagboken.data.importing.ImportUseCase
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.dataError
import se.partee71.dagboken.ui.importing.ImportEvent
import se.partee71.dagboken.ui.importing.ImportStage

/**
 * Export och import (BCK-6, BCK-13, SET-8) mot de riktiga use casen över fejkarna (SAF som fejk): exporten med
 * snackbarens antal och fel, importens lägen från valen via granskningen och bekräftelsen till klar, stoppet med
 * "Välj en annan fil", fel fil, läsfel med "Försök igen" och "Försök igen" efter ett batchfel. Fast klocka; syntetisk data.
 */
@RunWith(RobolectricTestRunner::class)
class ExportImportViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val uid = "uid-export-import"
    private val io = StandardTestDispatcher(main.dispatcher.scheduler)
    private val exportFile = ImportFixtures.exportText

    private val store = FakeStore().apply { set(Paths.USERS, uid, mapOf("schemaVersion" to Schema.CURRENT_VERSION), merge = false) }
    private val firestore = FakeRawFirestore(store)
    private val files = FakeUserFile()
    private val scope = TestUserScope(uid)
    private val exporter = ExportUseCase(firestore, files, scope, FixedClock(Instant.parse("2026-10-07T19:14:00Z")), io)
    private val importer = ImportUseCase(files, firestore, firestore, scope, io)
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
            assertEquals(DataError.Offline, state.failure?.dataError)
            assertNull(state.exported)
        }
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun `import från fil - granskning, bekräftelse med antal, skrivning och klar, sedan tillbaka till valen`() = test {
        files.files[fileUri] = exportFile
        val vm = viewModel()
        vm.state.test {
            vm.onEvent(ExportImportEvent.Import(ImportEvent.FileChosen(fileUri)))
            assertEquals(ImportStage.Reading, next<ImportStage.Reading>())
            val review = next<ImportStage.Review>()
            assertEquals(Schema.CURRENT_VERSION, review.schemaVersion)
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
    fun `en fil som inte går att öppna ger läsfelet, och Försök igen läser samma fil igen`() = test {
        val vm = viewModel()
        vm.state.test {
            vm.onEvent(ExportImportEvent.Import(ImportEvent.FileChosen(fileUri)))
            assertEquals(ImportStage.ReadFailed(null), next<ImportStage.ReadFailed>())
            files.files[fileUri] = exportFile
            vm.onEvent(ExportImportEvent.Import(ImportEvent.Retry))
            next<ImportStage.Review>()
        }
        assertTrue(firestore.batches.isEmpty())
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
        files.files[fileUri] = exportFile
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
