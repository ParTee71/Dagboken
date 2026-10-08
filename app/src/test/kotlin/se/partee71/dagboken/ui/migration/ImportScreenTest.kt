package se.partee71.dagboken.ui.migration

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
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
import se.partee71.dagboken.ui.migration.MigrationSamples.before
import se.partee71.dagboken.ui.migration.MigrationSamples.importReview
import se.partee71.dagboken.ui.migration.MigrationSamples.importStopped
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Första starten utan Room-fil (OMB-5) och importens vy (BCK-6, BCK-14): valen, filväljaren (SAF, JSON), Drive-samtycket,
 * "Börja tomt", granskningen med `ConfirmDialog` "Importera N poster?", stoppet och "Öppna Dagboken" när importen är klar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1700dp-xxhdpi")
class ImportScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val events = mutableListOf<MigrationEvent>()

    private var stage by mutableStateOf<ImportStage>(ImportStage.Choose)

    private fun show(stage: ImportStage) {
        this.stage = stage
        rule.setContent { DagbokenTheme { MigrationScreen(MigrationUiState(MigrationStage.Fallback(this.stage)), { events += it }) } }
    }

    private fun import(event: ImportEvent) = MigrationEvent.Import(event)

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
    fun `valen - välkomstrubriken, notisen och tre rader med undertext`() {
        show(ImportStage.Choose)
        rule.onNodeWithText(text(R.string.import_fallback_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_fallback_intro)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_from_drive_note)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_start_empty_note)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_from_drive)).performClick()
        rule.onNodeWithText(text(R.string.migration_start_empty)).performClick()
        assertEquals(listOf<MigrationEvent>(import(ImportEvent.FromDrive), MigrationEvent.StartEmpty), events)
        rule.onNodeWithText(text(R.string.import_from_file)).performClick()
        assertPicksFile()
    }

    @Test
    fun `Drive-samtycket startas av systemet och svaret skickas`() {
        val launched = mutableListOf<Any?>()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                launched += input
                dispatchResult(requestCode, Activity.RESULT_CANCELED, Intent())
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        val consent = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                DagbokenTheme { MigrationScreen(MigrationUiState(MigrationStage.Fallback(ImportStage.DriveConsent(consent))), { events += it }) }
            }
        }
        rule.waitForIdle()
        assertEquals(consent.intentSender, (launched.single() as IntentSenderRequest).intentSender)
        assertEquals(listOf<MigrationEvent>(import(ImportEvent.DriveConsent(false))), events)
    }

    @Test
    fun `Drive-samtycket startas inte igen när aktiviteten återskapas`() {
        val launched = mutableListOf<Any?>()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                launched += input
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        val consent = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                DagbokenTheme { MigrationScreen(MigrationUiState(MigrationStage.Fallback(ImportStage.DriveConsent(consent))), { events += it }) }
            }
        }
        rule.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals(1, launched.size)
    }

    @Test
    fun `ingen backup på Drive - notisen, Importera från fil och Börja tomt`() {
        show(ImportStage.NoDriveBackup)
        rule.onNodeWithText(text(R.string.import_no_drive)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.migration_start_empty)).performClick()
        assertEquals(listOf<MigrationEvent>(MigrationEvent.StartEmpty), events)
        rule.onNodeWithText(text(R.string.import_from_file)).performClick()
        assertPicksFile()
    }

    @Test
    fun `granskningen - tabellen, notisen om samma id och Importera till mitt konto`() {
        show(importReview)
        rule.onNodeWithText(text(R.string.import_review_title)).assertIsDisplayed()
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.import_replaces_count, 12, countText(12))).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_source_legacy, 2)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_from_source_drive)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_action)).performClick()
        rule.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(listOf<MigrationEvent>(import(ImportEvent.Import), import(ImportEvent.Reset)), events)
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
        val title = context.resources.getQuantityString(R.plurals.import_confirm_title, MigrationSamples.total, countText(MigrationSamples.total))
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_confirm)).performClick()
        assertEquals(listOf<MigrationEvent>(import(ImportEvent.Confirm)), events)
    }

    @Test
    fun `stoppet - rapporten och Välj en annan fil`() {
        show(importStopped)
        val line = importStopped.report.single()
        rule.onNodeWithText(text(R.string.migration_report_line, text(R.string.migration_entity_prescriptions), 1, line.field, line.reason)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.migration_nothing_written)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.import_choose_other)).performClick()
        assertPicksFile()
    }

    @Test
    fun `under skrivningen går Öppna Dagboken inte att trycka, och när importen är klar öppnar den appen`() {
        show(ImportStage.Writing(before, null))
        rule.onNodeWithText(text(R.string.migration_open)).assertIsNotEnabled()
        stage = ImportStage.Done(before, before)
        rule.onNodeWithText(text(R.string.import_done_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.migration_open)).performClick()
        assertEquals(listOf<MigrationEvent>(MigrationEvent.StartEmpty), events)
    }

    @Test
    fun `fel vid skrivningen - Försök igen och Avbryt`() {
        show(ImportStage.WriteFailed(DataError.Offline, null, "UNAVAILABLE"))
        rule.onNodeWithText(text(R.string.migration_failed_code_only, "UNAVAILABLE")).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry)).performClick()
        rule.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(listOf<MigrationEvent>(import(ImportEvent.Retry), import(ImportEvent.Reset)), events)
    }
}
