package se.partee71.dagboken.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.migration.ImportEvent
import se.partee71.dagboken.ui.migration.ImportStage
import se.partee71.dagboken.ui.migration.MigrationSamples
import se.partee71.dagboken.ui.migration.countText
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Export och import (BCK-6, BCK-13, BCK-14, SET-8): "Spara som JSON" öppnar filväljaren med filnamnet och snackbaren
 * "Sparade N poster" följer, "Importera backup" ger valen Drive och fil i ett ark, inget går att starta medan något pågår,
 * och importens lägen är samma vy som i första starten.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1700dp-xxhdpi")
class ExportImportScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val events = mutableListOf<ExportImportEvent>()

    private var state by mutableStateOf(ExportImportUiState())

    private fun show(initial: ExportImportUiState = ExportImportUiState()) {
        state = initial
        rule.setContent { DagbokenTheme { ExportImportScreen(state, { events += it }, { FILE_NAME }, onBack = {}) } }
    }

    private fun import(event: ImportEvent) = ExportImportEvent.Import(event)

    @Test
    fun `Spara som JSON öppnar filväljaren med filnamnet, och platsen skickas vidare`() {
        show()
        rule.onNodeWithText(text(R.string.export_note)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.export_title)).performClick()
        val shadow = shadowOf(rule.activity)
        val request = shadow.nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
        assertEquals(FILE_NAME, request.intent.getStringExtra(Intent.EXTRA_TITLE))
        val uri = Uri.parse("content://test/export.json")
        shadow.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
        rule.waitForIdle()
        assertEquals(listOf<ExportImportEvent>(ExportImportEvent.ExportChosen(uri)), events)
    }

    @Test
    fun `efter exporten visas Sparade N poster, och meddelandet släpps`() {
        show()
        state = state.copy(exported = 6353)
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(context.resources.getQuantityString(R.plurals.export_saved, 6353, countText(6353)))).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { ExportImportEvent.MessageShown in events }
    }

    @Test
    fun `ett fel vid exporten visas som meddelande`() {
        show(ExportImportUiState(failure = Failure(DataError.Offline)))
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(text(R.string.error_offline))).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `Importera backup ger valen Drive och fil i ett ark`() {
        show()
        rule.onNodeWithText(text(R.string.import_note)).performClick()
        rule.onNodeWithText(text(R.string.import_from_drive)).performClick()
        assertEquals(listOf<ExportImportEvent>(import(ImportEvent.FromDrive)), events)
        rule.onNodeWithText(text(R.string.import_title)).performClick()
        rule.onNodeWithText(text(R.string.import_from_file)).performClick()
        val request = shadowOf(rule.activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
    }

    @Test
    fun `medan exporten sparas går inget annat att starta`() {
        show(ExportImportUiState(exporting = true))
        rule.onNodeWithText(text(R.string.export_saving)).assertIsDisplayed()
        rule.onNode(hasText(text(R.string.export_title)) and hasClickAction()).assertDoesNotExist()
        rule.onNode(hasText(text(R.string.import_title)) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `importens lägen visas i stället för raderna, och Klar går tillbaka till valen`() {
        show(ExportImportUiState(import = MigrationSamples.importReview))
        rule.onNodeWithText(text(R.string.export_title)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.import_review_title)).assertIsDisplayed()
        state = ExportImportUiState(import = ImportStage.Done(MigrationSamples.before, MigrationSamples.before))
        rule.onNodeWithText(text(R.string.import_done)).performClick()
        assertEquals(listOf<ExportImportEvent>(import(ImportEvent.Reset)), events)
    }

    @Test
    fun `ingen backup på Drive i inställningarna - Importera från fil och Avbryt, inget Börja tomt`() {
        show(ExportImportUiState(import = ImportStage.NoDriveBackup))
        rule.onNodeWithText(text(R.string.import_no_drive_settings)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.migration_start_empty)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(listOf<ExportImportEvent>(import(ImportEvent.Reset)), events)
    }

    private companion object {
        const val FILE_NAME = "dagboken-export-2026-10-08.json"
    }
}
