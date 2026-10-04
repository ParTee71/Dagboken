package se.partee71.dagboken.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.navigation.DiaryKey
import se.partee71.dagboken.navigation.MedicinesKey
import se.partee71.dagboken.navigation.RootSheet
import se.partee71.dagboken.navigation.RootSheets
import se.partee71.dagboken.navigation.TodayKey
import se.partee71.dagboken.navigation.TOP_LEVEL
import se.partee71.dagboken.navigation.TrendsKey
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.ui.theme.DagbokenTheme

/** Flikarnas platshållare, inställningsarket bakom avataren (NAV-9) och loggmenyn bakom plusknappen (NAV-10). */
@RunWith(RobolectricTestRunner::class)
class AppSheetsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(sheet: RootSheet?, onSignOut: () -> Unit = {}, onOpenGallery: (() -> Unit)? = null, onDismiss: () -> Unit = {}) =
        rule.setContent { DagbokenTheme { RootSheets(sheet, onDismiss, onSignOut, onOpenGallery) } }

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
    fun `inställningsarket loggar ut och stänger sig (NAV-9, AUTH-3)`() {
        var signedOut = 0
        var dismissed = 0
        show(RootSheet.Account, onSignOut = { signedOut++ }, onDismiss = { dismissed++ })
        rule.onNodeWithText("Komponentgalleri").assertDoesNotExist()
        rule.onNodeWithText("Logga ut").performClick()
        assertEquals(1 to 1, signedOut to dismissed)
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
    fun `varje flik visar Kommer i etapp 5 och avataren öppnar arket`() {
        assertEquals(TOP_LEVEL, TabInfo.entries.map { it.key })
        var opened = 0
        rule.setContent { DagbokenTheme { TabPlaceholderScreen(MedicinesKey, onAccount = { opened++ }) } }
        rule.onNodeWithText("Mediciner").assertIsDisplayed()
        rule.onNodeWithText("Kommer i etapp 5").assertIsDisplayed()
        rule.onNodeWithContentDescription("Konto och inställningar").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `AccountSheet - debug`() = rule.captureScreenLightAndDark("AccountSheet_debug") {
        AccountSheet(onDismiss = {}, onSignOut = {}, onOpenGallery = {})
    }

    @Test
    fun `LogMenuSheet - fem val`() = rule.captureScreenLightAndDark("LogMenuSheet_val") {
        LogMenuSheet(onDismiss = {}, onPick = {})
    }

    @Test
    fun `TabPlaceholderScreen - alla flikar`() {
        listOf(TodayKey to "Idag", DiaryKey to "Dagbok", TrendsKey to "Trender", MedicinesKey to "Mediciner").forEach { (key, name) ->
            captureLightAndDark("TabPlaceholderScreen_$name") { TabPlaceholderScreen(key, onAccount = {}) }
        }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.activityString(id: Int): String =
    androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
