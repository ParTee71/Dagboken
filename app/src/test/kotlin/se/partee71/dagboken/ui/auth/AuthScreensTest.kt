package se.partee71.dagboken.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.AppRootContent
import se.partee71.dagboken.ui.theme.DagbokenTheme

/** Skärmarna som inloggningen styr: beteende och skärmdumpar (ljust + mörkt). */
@RunWith(RobolectricTestRunner::class)
class AuthScreensTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `inloggningen visar knappen och skickar SignIn`() {
        val events = mutableListOf<AuthEvent>()
        rule.setContent { DagbokenTheme { AppRootContent(AuthUiState(AuthGate.SignedOut), { events += it }) } }

        rule.onNodeWithText("Din hälsodagbok – mediciner, mående och aktiviteter på ett ställe").assertIsDisplayed()
        rule.onNodeWithText("Logga in med Google").performClick()

        assertIs<AuthEvent.SignIn>(events.single())
    }

    @Test
    fun `ett fel visas som snackbar och markeras som visat`() {
        val events = mutableListOf<AuthEvent>()
        rule.setContent {
            DagbokenTheme { AppRootContent(AuthUiState(AuthGate.SignedOut, error = DataError.Offline), { events += it }) }
        }

        rule.onNodeWithText("Ingen anslutning just nu", substring = true).assertIsDisplayed()
        rule.mainClock.advanceTimeBy(SNACKBAR_SHORT_MS)
        rule.waitForIdle()
        assertEquals(listOf<AuthEvent>(AuthEvent.ErrorShown), events)
    }

    @Test
    fun `nyare dataformat visar Uppdatera appen`() {
        rule.setContent { DagbokenTheme { AppRootContent(AuthUiState(AuthGate.UpdateRequired), {}) } }
        rule.onNodeWithText("Uppdatera appen").assertIsDisplayed()
        rule.onNodeWithText("Hämta senaste versionen").assertIsDisplayed()
    }

    @Test
    fun `inloggad men utan användardokument visar inloggningen igen`() {
        rule.setContent { DagbokenTheme { AppRootContent(AuthUiState(AuthGate.NeedsUser), {}) } }
        rule.onNodeWithText("Logga in med Google").assertIsDisplayed()
    }

    @Test
    fun `SignInScreen - start`() = captureLightAndDark("SignInScreen_start") {
        AppRootContent(AuthUiState(AuthGate.SignedOut), {})
    }

    @Test
    fun `SignInScreen - loggar in`() = captureLightAndDark("SignInScreen_loggarIn") {
        AppRootContent(AuthUiState(AuthGate.SignedOut, busy = true), {})
    }

    @Test
    fun `SignInScreen - fel`() {
        rule.mainClock.autoAdvance = false
        rule.captureLightAndDark("SignInScreen_fel", settle = { waitForIdle(); mainClock.advanceTimeBy(SNACKBAR_VISIBLE_MS) }) {
            AppRootContent(AuthUiState(AuthGate.SignedOut, error = DataError.Unknown), {})
        }
    }

    @Test
    fun `UpdateRequiredScreen - start`() = captureLightAndDark("UpdateRequiredScreen_start") {
        AppRootContent(AuthUiState(AuthGate.UpdateRequired), {})
    }

    private companion object {
        /** `SnackbarDuration.Short` plus marginal. */
        const val SNACKBAR_SHORT_MS = 5_000L

        /** Snackbaren har glidit in; två bilder hinns med innan `Short` (4 s) går ut. */
        const val SNACKBAR_VISIBLE_MS = 1_000L
    }
}
