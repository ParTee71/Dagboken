package se.partee71.dagboken.ui.importing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import se.partee71.dagboken.R
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.importing.ImportSamples.before
import se.partee71.dagboken.ui.importing.ImportSamples.importReview
import se.partee71.dagboken.ui.importing.ImportSamples.importStopped
import se.partee71.dagboken.ui.settings.ExportImportEvent
import se.partee71.dagboken.ui.settings.ExportImportScreen
import se.partee71.dagboken.ui.settings.ExportImportUiState
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Importens vy i Export och import (BCK-6): filväljaren (SAF, JSON), filen som inte är någon export, läsfelet,
 * granskningen med `ConfirmDialog` "Importera N poster?", stoppet, skrivningen och "Klar" när importen är klar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1700dp-xxhdpi")
class ImportScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val events = mutableListOf<ExportImportEvent>()

    private var stage by mutableStateOf<ImportStage>(ImportStage.Choose)

    @Composable
    private fun Screen(stage: ImportStage) = ExportImportScreen(ExportImportUiState(import = stage), { events += it }, { "dagboken-export.json" }, onBack = {})

    private fun show(stage: ImportStage) {
        this.stage = stage
        rule.setContent { DagbokenTheme { Screen(this.stage) } }
    }

    private fun import(event: ImportEvent) = ExportImportEvent.Import(event)

    /** Filväljaren öppnas med JSON som filtyp; den valda filen skickas vidare som [ImportEvent.FileChosen]. */
    private fun assertPicksFile() {
        val shadow = shadowOf(rule.activity)
        val request = shadow.nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
        assertEquals(listOf("application/json"), request.intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList())
        val uri = Uri.parse("content://test/backup.json")
        shadow.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
        rule.waitForIdle()
        assertEquals(import(ImportEvent.FileChosen(uri)), events.last())
    }

    @Test
    fun `filen är ingen export - notisen om 3x och Välj en annan fil`() {
        show(ImportStage.NotABackup)
        rule.onNodeWithText(text(R.string.import_not_a_backup)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_choose_other)).performClick()
        assertPicksFile()
    }

    @Test
    fun `läsfelet - Försök igen läser filen igen och Avbryt går tillbaka`() {
        show(ImportStage.ReadFailed(null))
        rule.onNodeWithText(text(R.string.import_read_failed_file)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry)).performClick()
        rule.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(listOf<ExportImportEvent>(import(ImportEvent.Retry), import(ImportEvent.Reset)), events)
    }

    @Test
    fun `granskningen - tabellen, notisen om samma id och Importera till mitt konto`() {
        show(importReview)
        rule.onNodeWithText(text(R.string.import_review_title)).assertIsDisplayed()
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.import_replaces_count, 12, countText(12))).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_source_export, 1)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_action)).performClick()
        rule.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(listOf<ExportImportEvent>(import(ImportEvent.Import), import(ImportEvent.Reset)), events)
    }

    @Test
    fun `inget som ersätts - den allmänna notisen om samma id`() {
        show(importReview.copy(replaced = 0))
        rule.onNodeWithText(text(R.string.import_replaces)).assertIsDisplayed()
    }

    @Test
    fun `en tom fil går inte att importera`() {
        show(importReview.copy(counts = before.mapValues { 0 }))
        rule.onNodeWithText(text(R.string.import_action)).assertIsNotEnabled()
    }

    @Test
    fun `bekräftelsen - Importera N poster, Importera och Avbryt`() {
        show(importReview.copy(confirming = true))
        val title = context.resources.getQuantityString(R.plurals.import_confirm_title, ImportSamples.total, countText(ImportSamples.total))
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_confirm)).performClick()
        assertEquals(listOf<ExportImportEvent>(import(ImportEvent.Confirm)), events)
    }

    @Test
    fun `stoppet - rapporten och Välj en annan fil`() {
        show(importStopped)
        val line = importStopped.report.single()
        rule.onNodeWithText(text(R.string.import_report_line, text(R.string.import_entity_prescriptions), 1, line.field, line.reason)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_nothing_written)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_choose_other)).performClick()
        assertPicksFile()
    }

    @Test
    fun `under skrivningen går Klar inte att trycka, och när importen är klar går den tillbaka till valen`() {
        show(ImportStage.Writing(before, null))
        rule.onNodeWithText(text(R.string.import_done)).assertIsNotEnabled()
        stage = ImportStage.Done(before, before)
        rule.onNodeWithText(text(R.string.import_done_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_done)).performClick()
        assertEquals(listOf<ExportImportEvent>(import(ImportEvent.Reset)), events)
    }

    @Test
    fun `fel vid skrivningen - Försök igen och Avbryt`() {
        show(ImportStage.WriteFailed(DataError.Offline, null, "UNAVAILABLE"))
        rule.onNodeWithText(text(R.string.import_failed_code_only, "UNAVAILABLE")).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry)).performClick()
        rule.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(listOf<ExportImportEvent>(import(ImportEvent.Retry), import(ImportEvent.Reset)), events)
    }
}
