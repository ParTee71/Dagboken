package se.partee71.dagboken.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.navigation.RootSheet
import se.partee71.dagboken.navigation.RootSheets
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.testing.clickWithoutRipple
import se.partee71.dagboken.ui.theme.DagbokenTheme

/** Appens fasta ark: inställningsarket bakom avataren (NAV-9) och loggmenyn bakom plusknappen (NAV-10). */
@RunWith(RobolectricTestRunner::class)
class AppSheetsTest {

    @get:Rule
    val rule = createComposeRule()

    private val anna = AuthUser("uid-anna", "Anna Berg", "anna.berg@exempel.se")

    private fun show(
        sheet: RootSheet?,
        onSignOut: () -> Unit = {},
        onOpenGallery: (() -> Unit)? = null,
        onDismiss: () -> Unit = {},
        account: AuthUser? = anna,
        onOpen: (SettingsPage) -> Unit = {},
    ) = rule.setContent { DagbokenTheme { RootSheets(sheet, onDismiss, onSignOut, onOpenGallery, account, onOpen) } }

    @Test
    fun `loggmenyn har exakt fem val i ordning och varje val stänger arket (NAV-10)`() {
        assertEquals(listOf("Mående", "Aktivitet", "Dos", "Händelse", "Sjukdom"), LogChoice.entries.map { rule.activityString(it.label) })
        var dismissed = 0
        show(RootSheet.Log, onDismiss = { dismissed++ })
        LogChoice.entries.forEach { rule.onNodeWithText(rule.activityString(it.label)).assertIsDisplayed() }
        rule.onNodeWithText("Aktivitet").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `inställningsarket loggar ut och stänger sig (NAV-9, AUTH-2)`() {
        var signedOut = 0
        var dismissed = 0
        show(RootSheet.Account, onSignOut = { signedOut++ }, onDismiss = { dismissed++ })
        rule.onNodeWithText("Komponentgalleri").assertDoesNotExist()
        rule.onNodeWithText("Logga ut").performClick()
        assertEquals(0 to 0, signedOut to dismissed, "först en fråga")
        rule.onNodeWithText("Logga ut?").assertIsDisplayed()
        rule.onNodeWithText("Avbryt").performClick()
        assertEquals(0, signedOut)
        rule.onNodeWithText("Logga ut").performClick()
        rule.onAllNodesWithText("Logga ut").onLast().performClick()
        assertEquals(1 to 1, signedOut to dismissed)
    }

    @Test
    fun `arket visar kontot och öppnar varje underskärm i ordning (NAV-9, AUTH-3)`() {
        val labels = listOf("Profil", "Påminnelser", "Tema", "Listor", "Export och import", "Om Dagboken")
        assertEquals(labels, SettingsPage.entries.map { rule.activityString(it.label) })
        val opened = mutableListOf<SettingsPage>()
        var dismissed = 0
        show(RootSheet.Account, onDismiss = { dismissed++ }, onOpen = { opened += it })
        rule.onNodeWithText("Anna Berg").assertIsDisplayed()
        rule.onNodeWithText("anna.berg@exempel.se\nInloggad med Google").assertIsDisplayed()
        labels.forEach { rule.onNodeWithText(it).performClick() }
        assertEquals(SettingsPage.entries.toList(), opened)
        assertEquals(labels.size, dismissed, "varje val stänger arket först")
    }

    @Test
    fun `utan namn står e-posten som rubrik`() {
        show(RootSheet.Account, account = AuthUser("uid-anna", email = "anna.berg@exempel.se"))
        rule.onNodeWithText("anna.berg@exempel.se").assertIsDisplayed()
        rule.onNodeWithText("Inloggad med Google").assertIsDisplayed()
    }

    @Test
    fun `komponentgalleriet finns i arket bara när det får öppnas (debug)`() {
        var opened = 0
        show(RootSheet.Account, onOpenGallery = { opened++ })
        rule.onNodeWithText("Komponentgalleri").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `inget ark – ingenting visas`() {
        show(null)
        rule.onNodeWithText("Logga ut").assertDoesNotExist()
        rule.onNodeWithText("Mående").assertDoesNotExist()
    }

    @Test
    fun `AccountSheet - debug`() = rule.captureScreenLightAndDark("AccountSheet_debug") {
        AccountSheet(onDismiss = {}, onSignOut = {}, onOpenGallery = {}, account = anna, onOpen = {})
    }

    @Test
    fun `AccountSheet - logga ut frågar först`() = rule.captureScreenLightAndDark("AccountSheet_loggaut", open = { onNodeWithText("Logga ut").clickWithoutRipple() }) {
        AccountSheet(onDismiss = {}, onSignOut = {}, onOpenGallery = null, account = anna, onOpen = {})
    }

    @Test
    fun `LogMenuSheet - fem val`() = rule.captureScreenLightAndDark("LogMenuSheet_val") {
        LogMenuSheet(onDismiss = {}, onPick = {})
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.activityString(id: Int): String =
    androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
