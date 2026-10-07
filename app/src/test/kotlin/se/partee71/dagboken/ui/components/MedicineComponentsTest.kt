package se.partee71.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Det som Mediciner lade till i katalogen (#255): favoritstjärnan (andra användningen efter Listor),
 * postkortets reglage och rad under texten, stegarens värdetext och listramens grupp av postkort.
 */
@RunWith(RobolectricTestRunner::class)
class MedicineComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { DagbokenTheme(content = content) }

    private fun SemanticsNodeInteraction.top() = fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `FavoriteStar säger vad ett tryck gör och växlar`() {
        var favorite by mutableStateOf(false)
        show { FavoriteStar("Alvedon", favorite, { favorite = !favorite }) }
        rule.onNodeWithContentDescription("Markera Alvedon som favorit").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Inte favorit")).performClick()
        assertTrue(favorite)
        rule.onNodeWithContentDescription("Ta bort Alvedon som favorit").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Favorit"))
    }

    @Test
    fun `postkortets reglage är en egen växel med etikett, och tryck på kortet öppnar fortfarande`() {
        var active by mutableStateOf(true)
        var opened = 0
        show {
            DagbokenEntryCard(
                "Sertralin 50 mg",
                onClick = { opened++ },
                inactive = !active,
                toggle = EntryToggle(active, { active = it }, "Sertralin aktivt"),
                below = { InfoPill("Idag 75 mg (+25)") },
            )
        }
        rule.onNodeWithContentDescription("Sertralin aktivt")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .performClick()
        assertEquals(false, active)
        assertEquals(0, opened, "reglaget öppnar inte posten")
        rule.onNodeWithText("Sertralin 50 mg").performClick()
        assertEquals(1, opened)
        assertTrue(rule.onNodeWithText("Idag 75 mg (+25)", useUnmergedTree = true).top() > rule.onNodeWithText("Sertralin 50 mg", useUnmergedTree = true).top(), "pills under titeln")
    }

    @Test
    fun `med reglage får titeln hela bredden ovanför undertexten och kontrollerna`() {
        show {
            DagbokenEntryCard(
                "Sertralin 50 mg",
                onClick = {},
                subtitle = "Morgon · dagligen · tills vidare",
                note = "Med frukost",
                expandedContent = {},
                onEdit = {},
                toggle = EntryToggle(true, {}, "Sertralin aktivt"),
            )
        }
        val title = rule.onNodeWithText("Sertralin 50 mg", useUnmergedTree = true).fetchSemanticsNode()
        val subtitle = rule.onNodeWithText("Morgon · dagligen · tills vidare", useUnmergedTree = true).fetchSemanticsNode()
        val toggle = rule.onNodeWithContentDescription("Sertralin aktivt").fetchSemanticsNode()
        assertTrue(title.boundsInRoot.bottom <= subtitle.boundsInRoot.top, "undertexten under titeln")
        assertTrue(title.boundsInRoot.bottom <= toggle.boundsInRoot.top, "reglaget under titeln, inte bredvid")
    }

    @Test
    fun `QuantityStepper visar och läser värdetexten`() {
        var hours by mutableStateOf(1)
        show { QuantityStepper(hours, { hours = it }, "minsta tid mellan doser", valueText = { if (it == 0) "Ingen spärr" else "$it h" }) }
        rule.onNodeWithText("1 h").assertIsDisplayed()
        rule.onNodeWithContentDescription("minsta tid mellan doser 1 h").assertIsDisplayed()
        rule.onNodeWithContentDescription("Minska minsta tid mellan doser").performClick()
        rule.onNodeWithText("Ingen spärr").assertIsDisplayed()
        rule.onNodeWithContentDescription("minsta tid mellan doser Ingen spärr").assertIsDisplayed()
    }

    @Test
    fun `NoticeBanner är en knapp som öppnar det meddelandet gäller`() {
        var opened = 0
        show { NoticeBanner("Kåvepenin slutar i morgon.", se.partee71.dagboken.R.drawable.ic_bell, { opened++ }) }
        rule.onNodeWithText("Kåvepenin slutar i morgon.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `NoticeBanner utan länk är bara ett meddelande med sin förklaring – ingen knapp`() {
        show { NoticeBanner("Health Connect saknas", se.partee71.dagboken.R.drawable.ic_clock, onClick = null, detail = "Klockans data visas när Health Connect är kopplat.") }
        rule.onNodeWithText("Health Connect saknas").assertIsDisplayed().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        rule.onNodeWithText("Klockans data visas när Health Connect är kopplat.").assertIsDisplayed()
        rule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `NoticeBanner med åtgärd har knappen under texten – kortet självt är ingen knapp (HLS-4)`() {
        var opened = 0
        show {
            NoticeBanner(
                "Health Connect saknas",
                se.partee71.dagboken.R.drawable.ic_watch,
                { opened++ },
                detail = "Installera Health Connect från Play Butik för att se klockans data.",
                action = "Installera",
            )
        }
        rule.onNodeWithText("Health Connect saknas").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
        rule.onNodeWithText("Installera").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `en grupp av postkort har rubriken ovanför och korten var för sig`() {
        show {
            EntityListScreen(
                title = "Mediciner",
                state = ListUiState.Content(listOf("Levaxin", "Sertralin", "Alvedon")),
                empty = EmptyContent(0, "", ""),
                add = AddAction("Nytt recept", {}),
                key = { it },
                group = { if (it == "Alvedon") ListGroup("Vid behov") else ListGroup("Recept och scheman", count = "2 aktiva", cards = true) },
            ) { DagbokenEntryCard(it, onClick = {}) }
        }
        rule.onNodeWithText("Recept och scheman").assertIsDisplayed()
        rule.onNodeWithText("2 aktiva").assertIsDisplayed()
        val levaxin = rule.onNodeWithText("Levaxin").top()
        val sertralin = rule.onNodeWithText("Sertralin").top()
        assertTrue(rule.onNodeWithText("Recept och scheman").top() < levaxin && levaxin < sertralin)
        rule.onNodeWithText("Vid behov").assertIsDisplayed()
        rule.onNodeWithText("Alvedon").assertIsDisplayed()
    }
}
