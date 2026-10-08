package se.partee71.dagboken.ui.migration

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import se.partee71.dagboken.R
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.legacy.AccountCheck
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.migration.MigrationSamples.before
import se.partee71.dagboken.ui.migration.MigrationSamples.halfway
import se.partee71.dagboken.ui.migration.MigrationSamples.review
import se.partee71.dagboken.ui.migration.MigrationSamples.saved
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Migreringsskärmen och startdestinationen (NAV-6, OMB-2, OMB-7, OMB-8): laddning under startkontrollen, skärmen
 * före flikarna och flikarna efteråt; spärren utan kontrollerad kopia, filväljaren med filnamnet, raderna som en
 * enhet för TalkBack, knappar på minst 48 dp, "Försök igen"/"Inte nu"/"Importera backup" och felet vid bekräftelsen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1700dp-xxhdpi")
class MigrationScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val events = mutableListOf<MigrationEvent>()

    private fun show(stage: MigrationStage, failure: Failure? = null) {
        rule.setContent { DagbokenTheme { MigrationScreen(MigrationUiState(stage, failure), { events += it }) } }
    }

    // ── Startdestinationen (NAV-6) ────────────────────────────────────────

    @Test
    fun `startdestinationen - laddning under kontrollen, migreringen före flikarna och sedan flikarna`() {
        var state by mutableStateOf(MigrationUiState())
        val opened = mutableListOf<Boolean>()
        rule.setContent {
            DagbokenTheme {
                MigrationGateContent(state, { events += it }) { openImport ->
                    opened += openImport
                    Text("Flikarna")
                }
            }
        }
        rule.onNodeWithContentDescription(text(R.string.loading)).assertExists()
        rule.onNodeWithText("Flikarna").assertDoesNotExist()

        state = MigrationUiState(review())
        rule.onNodeWithText(text(R.string.migration_title)).assertIsDisplayed()
        rule.onNodeWithText("Flikarna").assertDoesNotExist()

        state = MigrationUiState(MigrationStage.Closed(openImport = true))
        rule.onNodeWithText("Flikarna").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.migration_title)).assertDoesNotExist()
        assertEquals(true, opened.last(), "Importera backup öppnar Export och import ovanpå flikarna")
    }

    // ── Granskningen och kopian (OMB-8) ───────────────────────────────────

    @Test
    fun `spärren - utan kontrollerad kopia går det inte att flytta, men Inte nu går alltid`() {
        show(review())
        rule.onNodeWithText(text(R.string.migration_move)).performScrollTo().assertIsNotEnabled().performClick()
        rule.onNodeWithText(text(R.string.not_now)).performScrollTo().performClick()
        assertEquals(listOf<MigrationEvent>(MigrationEvent.NotNow), events)
    }

    @Test
    fun `Spara en kopia öppnar filväljaren med filnamnet, och den valda filen skickas vidare`() {
        show(review())
        rule.onNodeWithText(text(R.string.migration_copy_save)).performClick()
        val shadow = shadowOf(rule.activity)
        val request = shadow.nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
        assertEquals("dagboken-3x-kopia-2026-10-07.json", request.intent.getStringExtra(Intent.EXTRA_TITLE))
        val uri = Uri.parse("content://test/kopia.json")
        shadow.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
        rule.waitForIdle()
        assertEquals(listOf<MigrationEvent>(MigrationEvent.CopyChosen(uri)), events)
    }

    @Test
    fun `med kontrollerad kopia flyttar knappen, och raderna läses som en enhet`() {
        show(review(saved))
        rule.onNodeWithText(text(R.string.migration_copy_verified)).assertIsDisplayed()
        rule.onNodeWithText(saved.fileName!!).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.migration_row_before, text(R.string.migration_entity_doses), countText(3904))).assertExists()
        rule.onNodeWithContentDescription(text(R.string.migration_row_before, text(R.string.migration_entity_settings), countText(25)))
            .assertExists()
        rule.onNodeWithText(text(R.string.migration_move)).performScrollTo().assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(text(R.string.not_now)).assertHeightIsAtLeast(48.dp)
        assertEquals(listOf<MigrationEvent>(MigrationEvent.Move), events)
    }

    @Test
    fun `ett annat konto än i 3x visas som varning`() {
        show(review(accountCheck = AccountCheck.DIFFERENT))
        rule.onNodeWithText(text(R.string.migration_account_mismatch, "anna.lind@example.com")).assertIsDisplayed()
    }

    @Test
    fun `okänt 3x-konto - kryssrutan med e-posten krävs innan Flytta går att trycka`() {
        var stage by mutableStateOf(review(saved, AccountCheck.UNKNOWN))
        rule.setContent { DagbokenTheme { MigrationScreen(MigrationUiState(stage), { events += it }) } }
        val confirm = text(R.string.migration_account_confirm, "anna.lind@example.com")
        rule.onNodeWithText(text(R.string.migration_move)).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText(confirm).performScrollTo().assertIsOff().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf<MigrationEvent>(MigrationEvent.ConfirmAccount(true)), events)
        stage = review(saved, AccountCheck.UNKNOWN, accountConfirmed = true)
        rule.onNodeWithText(confirm).assertIsOn()
        rule.onNodeWithText(text(R.string.migration_move)).performScrollTo().assertIsEnabled()
    }

    @Test
    fun `känt 3x-konto har ingen kryssruta`() {
        show(review(saved))
        rule.onNodeWithText(text(R.string.migration_account_confirm, "anna.lind@example.com")).assertDoesNotExist()
    }

    // ── Skriva, klar och bekräfta ─────────────────────────────────────────

    @Test
    fun `under skrivningen före och efter per rad, och Öppna Dagboken går inte att trycka`() {
        show(MigrationStage.Writing(before, halfway))
        val doses = text(R.string.migration_entity_doses)
        rule.onNodeWithContentDescription(text(R.string.migration_row_after, doses, countText(3904), countText(2140))).assertExists()
        rule.onNodeWithContentDescription(text(R.string.migration_row_pending, text(R.string.migration_entity_checkins), countText(143))).assertExists()
        rule.onNodeWithContentDescription(text(R.string.migration_row_done, text(R.string.migration_entity_activities), countText(612), countText(612))).assertExists()
        rule.onNodeWithText(text(R.string.migration_open)).assertIsNotEnabled()
    }

    @Test
    fun `medan läget på servern läses visas det, och raderna väntar`() {
        show(MigrationStage.Writing(before, null))
        rule.onNodeWithText(text(R.string.migration_reading_server)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.migration_row_pending, text(R.string.migration_entity_doses), countText(3904))).assertExists()
    }

    @Test
    fun `påbörjad flytt - granskningen har Avbryt flytten bakom en dialog i stället för Inte nu`() {
        show(review(MigrationSamples.saved).copy(started = true))
        rule.onNodeWithText(text(R.string.not_now)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.migration_started_note)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.migration_abort)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(text(R.string.migration_abort_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(emptyList<MigrationEvent>(), events, "avbrutet i dialogen: inget händer")
        rule.onNodeWithText(text(R.string.migration_abort)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.migration_abort_confirm)).performClick()
        assertEquals(listOf<MigrationEvent>(MigrationEvent.Abort), events)
    }

    @Test
    fun `klar, avvikelse och avbruten skrivning efter första batchen har Avbryt flytten och inget Inte nu`() {
        var stage by mutableStateOf<MigrationStage>(MigrationStage.Written(before, before))
        rule.setContent { DagbokenTheme { MigrationScreen(MigrationUiState(stage), { events += it }) } }
        fun abortOnly() {
            rule.onNodeWithText(text(R.string.not_now)).assertDoesNotExist()
            rule.onNodeWithText(text(R.string.migration_abort)).performScrollTo().assertIsDisplayed()
        }
        abortOnly()
        stage = MigrationStage.Mismatch(before, before + ("doses" to 3902), mapOf("doses" to 2))
        abortOnly()
        stage = MigrationStage.WriteFailed(DataError.Offline, 1, "UNAVAILABLE")
        abortOnly()
        // Före första skrivningen (t.ex. utan nät när läget skulle läsas) finns Inte nu kvar.
        stage = MigrationStage.WriteFailed(DataError.Offline, null, "UNAVAILABLE", started = false)
        rule.onNodeWithText(text(R.string.migration_abort)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.not_now)).performScrollTo().assertIsDisplayed()
        stage = MigrationStage.Aborting
        rule.onNodeWithText(text(R.string.migration_aborting_title)).assertIsDisplayed()
    }

    @Test
    fun `klar med poster som fanns redan - bocken räknar dem och ett neutralt meddelande`() {
        show(MigrationStage.Written(before, before + ("settings" to 0), existing = mapOf("settings" to 1)))
        val settings = text(R.string.migration_entity_settings)
        rule.onNodeWithContentDescription(text(R.string.migration_row_done, settings, countText(25), countText(24))).assertExists()
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.migration_existing, 1, countText(1))).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `klar utan poster som fanns redan visar inget sådant meddelande`() {
        show(MigrationStage.Written(before, before))
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.migration_existing, 1, countText(1))).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.migration_done_note)).assertIsDisplayed()
    }

    @Test
    fun `klar - Bekräfta skickas, och ett fel vid bekräftelsen visas som meddelande`() {
        show(MigrationStage.Written(before, before), Failure(DataError.Offline))
        rule.onNodeWithText(text(R.string.error_offline)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.migration_confirm)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(MigrationEvent.Confirm, events.last())
    }

    @Test
    fun `klar - medan bekräftelsen skrivs går knappen inte att trycka igen`() {
        show(MigrationStage.Written(before, before, confirming = true))
        rule.onNodeWithText(text(R.string.migration_confirm)).assertIsNotEnabled()
    }

    // ── Fel, stopp och offline ────────────────────────────────────────────

    @Test
    fun `offline - Ingen anslutning med Försök igen och Inte nu`() {
        show(MigrationStage.CheckFailed(DataError.Offline))
        rule.onNodeWithText(text(R.string.migration_offline_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry)).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(text(R.string.not_now)).performClick()
        assertEquals(listOf(MigrationEvent.Retry, MigrationEvent.NotNow), events)
    }

    @Test
    fun `avbruten skrivning visar felet och felkoden med batchnummer`() {
        show(MigrationStage.WriteFailed(DataError.QuotaExceeded, 3, "RESOURCE_EXHAUSTED"))
        rule.onNodeWithText(text(R.string.error_quota_exceeded)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.migration_failed_code, "RESOURCE_EXHAUSTED", 4)).assertIsDisplayed()
    }

    @Test
    fun `stopp - rapporten med antal, Försök igen, Importera backup och Börja tomt`() {
        show(MigrationSamples.stopped)
        val line = MigrationSamples.stopped.report.single()
        rule.onNodeWithText(text(R.string.migration_report_line, text(R.string.migration_entity_prescriptions), 1, line.field, line.reason)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.migration_nothing_written)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry)).performClick()
        rule.onNodeWithText(text(R.string.migration_import_instead)).performClick()
        rule.onNodeWithText(text(R.string.migration_start_empty)).performClick()
        assertEquals(listOf(MigrationEvent.Retry, MigrationEvent.ImportBackup, MigrationEvent.NotNow), events)
    }

    @Test
    fun `äldre version - Importera backup och Börja tomt, inget Försök igen`() {
        show(MigrationStage.WrongVersion(10))
        rule.onNodeWithText(text(R.string.migration_version_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.migration_import)).performClick()
        assertEquals(listOf<MigrationEvent>(MigrationEvent.ImportBackup), events)
    }

    // ── Raderna ───────────────────────────────────────────────────────────

    @Test
    fun `varje samling som migreringen skriver har en rad, i mockupens ordning`() {
        assertEquals(
            CollectionNames.ALL - CollectionNames.USERS,
            (CollectionNames.ALL - CollectionNames.USERS).filter { MigrationEntity.of(it) != null },
        )
        assertEquals(25, MigrationEntity.SETTINGS.count(before), "inställningarna och listorna på en rad")
        assertEquals("6 353", countText(6353))
    }
}
