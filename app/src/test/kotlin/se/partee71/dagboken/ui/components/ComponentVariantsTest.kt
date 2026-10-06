package se.partee71.dagboken.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.testing.clickWithoutRipple
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Parametrarna som Idag (etapp 5.3b) lade till på delade komponenter – standardvärdena bevarar det gamla
 * utseendet (oförändrade skärmdumpar): `CheckRow(menu, note, below)`, `AppFilterChip(onLongClick)`,
 * `AppMenuItem(section)` och `ProgressBar(celebrate)`.
 */
@RunWith(RobolectricTestRunner::class)
class ComponentVariantsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { DagbokenTheme(content = content) }

    @Test
    fun `CheckRow med meny - tryck växlar, långtryck och ⋮ öppnar menyn (NFR-17, MED-3)`() {
        val changes = mutableListOf<Boolean>()
        var skipped = 0
        show {
            CheckRow(
                "Levaxin 100 µg", false, { changes += it },
                note = "Tas på fastande mage.",
                menu = listOf(AppMenuItem("Hoppa över", { skipped++ }, R.drawable.ic_close)),
                below = { InfoPill("Försenat") },
            )
        }
        rule.onNodeWithText("Levaxin 100 µg").assertIsOff().performClick()
        assertEquals(listOf(true), changes)
        rule.onNodeWithText("Försenat").assertIsDisplayed()

        rule.onNodeWithText("Levaxin 100 µg").performTouchInput { longClick() }
        assertEquals(listOf(true), changes, "långtrycket växlar inte")
        rule.onNodeWithText("Hoppa över").performClick()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Hoppa över").performClick()
        assertEquals(2, skipped)

        rule.onNodeWithContentDescription("Visa anteckning").performClick()
        rule.onNodeWithText("Tas på fastande mage.").assertIsDisplayed()
    }

    @Test
    fun `inaktiv CheckRow med meny - växlar inte, men långtryck och ⋮ öppnar menyn (NFR-17, MED-3)`() {
        val changes = mutableListOf<Boolean>()
        var deleted = 0
        show {
            CheckRow(
                "Ipren 400 mg", true, { changes += it },
                menu = listOf(AppMenuItem("Radera", { deleted++ }, R.drawable.ic_delete, destructive = true)),
                enabled = false,
            )
        }
        rule.onNodeWithText("Ipren 400 mg").assertIsOn().assertIsNotEnabled().performClick()
        assertEquals(emptyList(), changes, "en inaktiv rad växlar inte")
        rule.onNodeWithText("Ipren 400 mg").performTouchInput { longClick() }
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(2, deleted)
        assertEquals(emptyList(), changes)
    }

    @Test
    fun `AppFilterChip - långtryck kör onLongClick men inte onClick`() {
        var clicks = 0
        var longClicks = 0
        show { AppFilterChip("Alvedon 500 mg", false, { clicks++ }, onLongClick = { longClicks++ }, onLongClickLabel = "Fler val") }
        rule.onNodeWithText("Alvedon 500 mg").performTouchInput { longClick() }
        assertEquals(0, clicks)
        assertEquals(1, longClicks)
        rule.onNodeWithText("Alvedon 500 mg").performClick()
        assertEquals(1, clicks)
        assertEquals(1, longClicks)
    }

    @Test
    fun `AppFilterChip - en långsam dragning som börjar på chipet öppnar inte menyn och klickar inte`() {
        var clicks = 0
        var longClicks = 0
        val scroll = ScrollState(0)
        show {
            Column(Modifier.height(SCROLL_VIEWPORT).verticalScroll(scroll)) {
                AppFilterChip("Alvedon 500 mg", false, { clicks++ }, onLongClick = { longClicks++ })
                Spacer(Modifier.height(SCROLL_VIEWPORT * 3))
            }
        }
        rule.onNodeWithText("Alvedon 500 mg").performTouchInput {
            down(center)
            moveBy(Offset(0f, -10f), delayMillis = 100)
            moveBy(Offset(0f, -200f), delayMillis = 100)
            advanceEventTime(1_000)
            up()
        }
        assertEquals(0, longClicks)
        assertEquals(0, clicks)
        assertTrue(scroll.value > 0, "rullningen gick vidare till listan")
    }

    @Test
    fun `AppFilterChip - inaktivt chip nås fortfarande med långtryck`() {
        var longClicks = 0
        show { AppFilterChip("Alvedon 500 mg", false, {}, enabled = false, onLongClick = { longClicks++ }) }
        rule.onNodeWithText("Alvedon 500 mg").performTouchInput { longClick() }
        assertEquals(1, longClicks)
    }

    @Test
    fun `EntityDetailScreen i flikläge - stor rubrik med undertext och actions, ingen tillbakapil`() {
        var avatar = 0
        show {
            EntityDetailScreen(
                DetailUiState.Content("Levaxin"), header = null, onBack = null, title = "God morgon", subtitle = "Söndag 4 oktober · vecka 40",
                actions = { AppIconButton(R.drawable.ic_person, "Konto och inställningar", { avatar++ }) },
            ) { AppCard { ItemRow(it) } }
        }
        rule.onNodeWithText("God morgon").assertIsDisplayed()
        rule.onNodeWithText("Söndag 4 oktober · vecka 40").assertIsDisplayed()
        rule.onNodeWithContentDescription("Tillbaka").assertDoesNotExist()
        rule.onNodeWithContentDescription("Konto och inställningar").performClick()
        assertEquals(1, avatar)
    }

    @Test
    fun `skärmdumpar - varianterna`() {
        captureLightAndDark("CheckRow_meny") {
            Sheet {
                AppCard {
                    CheckRow(
                        "D-vitamin 20 µg", false, {}, subtitle = "Förmiddag · 10:00", note = "Tas med mat.",
                        menu = listOf(AppMenuItem("Hoppa över", {}, R.drawable.ic_close)), below = { InfoPill("Försenat", tone = Tone.Warning) },
                    )
                }
            }
        }
        captureLightAndDark("ProgressBar_utan_firande") { Sheet { ProgressBar(9, 9, celebrate = false) } }
        captureLightAndDark("EntityDetailScreen_flik") {
            EntityDetailScreen(DetailUiState.Content("Levaxin 100 µg"), header = null, onBack = null, title = "God morgon", subtitle = "Söndag 4 oktober · vecka 40") {
                AppCard { ItemRow(it, subtitle = "Morgon · 07:00") }
            }
        }
    }

    @Test
    fun `skärmdump - meny med avdelning`() = rule.captureScreenLightAndDark("AppMenu_avdelning", open = { onNodeWithContentDescription("Fler val").clickWithoutRipple() }) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            AppMenu(listOf(AppMenuItem("Loratadin 10 mg", {}), AppMenuItem("Betapred 0,5 mg", {}, section = "Recept"), AppMenuItem("Levaxin 100 µg", {}, section = "Recept")))
        }
    }

    private companion object {
        val SCROLL_VIEWPORT = 200.dp
    }

    @Composable
    private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(Spacing.l), content = content)
    }

    @Test
    fun `AppMenuItem section ger en rubrik där avdelningen börjar (FAV-11)`() {
        var picked = ""
        show {
            AppMenuPopup(
                listOf(
                    AppMenuItem("Loratadin 10 mg", { picked = "l" }),
                    AppMenuItem("Betapred 0,5 mg", { picked = "b" }, section = "Recept"),
                    AppMenuItem("Levaxin 100 µg", { picked = "v" }, section = "Recept"),
                ),
                expanded = true,
                onDismiss = {},
            )
        }
        rule.onNodeWithText("RECEPT").assertIsDisplayed()
        rule.onNodeWithText("Levaxin 100 µg").performClick()
        assertEquals("v", picked)
    }

    @Test
    fun `ProgressBar utan firande är teal med vanlig text också när allt är klart (HEM-19)`() {
        show { ProgressBar(9, 9, celebrate = false) }
        rule.onNodeWithContentDescription("9 av 9 klara").assertIsDisplayed()
    }
}
