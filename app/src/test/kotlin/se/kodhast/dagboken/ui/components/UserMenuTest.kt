package se.kodhast.dagboken.ui.components

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.kodhast.dagboken.data.auth.AuthUser
import se.kodhast.dagboken.testing.captureScreenLightAndDark
import se.kodhast.dagboken.testing.clickWithoutRipple
import se.kodhast.dagboken.ui.settings.SettingsScreen
import se.kodhast.dagboken.ui.theme.DagbokenTheme

/** Avatarmenyn i sidhuvudet och Inställningar som den öppnar (NAV-9, AUTH-2, AUTH-3). */
@RunWith(RobolectricTestRunner::class)
class UserMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private val anna = AuthUser("uid-anna", "Anna Berg", "anna.berg@exempel.se")

    private fun showMenu(
        account: AuthUser? = anna,
        onOpenSettings: () -> Unit = {},
        onOpenAbout: () -> Unit = {},
        onSignOut: () -> Unit = {},
        onOpenGallery: (() -> Unit)? = null
    ) {
        rule.setContent { DagbokenTheme { UserMenu(account, onOpenSettings, onOpenAbout, onSignOut, onOpenGallery = onOpenGallery) } }
        rule.onNodeWithContentDescription(ACCOUNT).performClick()
    }

    @Test
    fun `avataren fäller ut menyn med kontot, Inställningar och Om Dagboken (NAV-9, AUTH-3)`() {
        var settings = 0
        var about = 0
        showMenu(onOpenSettings = { settings++ }, onOpenAbout = { about++ })
        rule.onNodeWithText("Anna Berg").assertIsDisplayed()
        rule.onNodeWithText("anna.berg@exempel.se").assertIsDisplayed()
        rule.onNodeWithText("Komponentgalleri").assertDoesNotExist()
        rule.onNodeWithText("Logga in med Google").assertDoesNotExist()
        rule.onNodeWithText("Inställningar").performClick()
        assertEquals(1, settings)
        rule.onNodeWithContentDescription(ACCOUNT).performClick()
        rule.onNodeWithText("Om Dagboken").performClick()
        assertEquals(1, about)
    }

    @Test
    fun `Logga ut frågar först (AUTH-2)`() {
        var signedOut = 0
        showMenu(onSignOut = { signedOut++ })
        rule.onNodeWithText("Logga ut").performClick()
        assertEquals(0, signedOut, "först en fråga")
        rule.onNodeWithText("Logga ut?").assertIsDisplayed()
        rule.onNodeWithText("Avbryt").performClick()
        assertEquals(0, signedOut)
        rule.onNodeWithContentDescription(ACCOUNT).performClick()
        rule.onNodeWithText("Logga ut").performClick()
        rule.onAllNodesWithText("Logga ut").onLast().performClick()
        assertEquals(1, signedOut)
    }

    @Test
    fun `komponentgalleriet finns i menyn bara när det får öppnas (debug)`() {
        var opened = 0
        showMenu(onOpenGallery = { opened++ })
        rule.onNodeWithText("Komponentgalleri").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `Inställningar öppnar varje underskärm i ordning (NAV-9)`() {
        val labels = listOf("Profil", "Påminnelser", "Tema", "Listor", "Export och import")
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(labels, SettingsPage.entries.map { context.getString(it.label) })
        val opened = mutableListOf<SettingsPage>()
        var back = 0
        rule.setContent { DagbokenTheme { SettingsScreen(onBack = { back++ }, onOpen = { opened += it }) } }
        rule.onNodeWithText("Inställningar").assertIsDisplayed()
        rule.onNodeWithText("Om Dagboken").assertDoesNotExist()
        labels.forEach { rule.onNodeWithText(it).performClick() }
        assertEquals(SettingsPage.entries.toList(), opened)
        rule.onNodeWithContentDescription("Tillbaka").performClick()
        assertEquals(1, back)
    }

    @Test
    fun `UserMenu - menyn i debug`() = rule.captureScreenLightAndDark("UserMenu_meny", open = { onNodeWithContentDescription(ACCOUNT).clickWithoutRipple() }) {
        UserMenu(anna, {}, {}, {}, online = true, onOpenGallery = {})
    }

    @Test
    fun `Inställningar`() = rule.captureScreenLightAndDark("Settings_Installningar") {
        SettingsScreen(onBack = {}, onOpen = {})
    }

    private companion object {
        const val ACCOUNT = "Konto och inställningar"
    }
}
