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
import se.partee71.dagboken.navigation.TodayKey
import se.partee71.dagboken.navigation.TOP_LEVEL
import se.partee71.dagboken.navigation.TrendsKey
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.theme.DagbokenTheme

/** Flikarnas platshållare innan flikarna byggts, med avataren som öppnar inställningsarket (NAV-9). */
@RunWith(RobolectricTestRunner::class)
class TabPlaceholderScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `varje flik visar Snart här och vad den ska innehålla, och avataren öppnar arket`() {
        assertEquals(TOP_LEVEL, TabInfo.entries.map { it.key })
        var opened = 0
        rule.setContent { DagbokenTheme { TabPlaceholderScreen(MedicinesKey, onAccount = { opened++ }) } }
        rule.onNodeWithText("Mediciner").assertIsDisplayed()
        rule.onNodeWithText("Snart här").assertIsDisplayed()
        rule.onNodeWithText("Här hanterar du recept, scheman och vid behov-mediciner.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Konto och inställningar").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `TabPlaceholderScreen - alla flikar`() {
        listOf(TodayKey to "Idag", DiaryKey to "Dagbok", TrendsKey to "Trender", MedicinesKey to "Mediciner").forEach { (key, name) ->
            captureLightAndDark("TabPlaceholderScreen_$name") { TabPlaceholderScreen(key, onAccount = {}) }
        }
    }
}
