package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

/**
 * Kortstandarden (NFR-15/NFR-16) verifierad på den delade komponenten, så att varje
 * yta som migrerar hit ärver beteendet i stället för att testa om det själv.
 *
 * Samma testupplägg som [DagbokenCardTest]: `createEmptyComposeRule()` + en färsk
 * `ActivityScenario` per försök (se `retryOnRenderGlitch`, #112).
 */
@RunWith(AndroidJUnit4::class)
class DagbokenEntryCardTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun launch(content: @Composable () -> Unit): ActivityScenario<ComponentActivity> {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { it.setContent { MaterialTheme { content() } } }
        return scenario
    }

    /**
     * Långsamt svep förbi den positionella tröskeln (50 % av bredden) — inte
     * `swipeLeft()`, vars höga hastighet gör utfallet beroende av fling-settling.
     */
    private fun TouchInjectionScope.slowSwipeLeft() {
        down(centerRight)
        var x = right
        repeat(10) {
            advanceEventTime(16)
            x -= width / 12f
            moveTo(Offset(x, center.y))
        }
        advanceEventTime(16)
        up()
    }

    private fun TouchInjectionScope.slowSwipeRight() {
        down(centerLeft)
        var x = left
        repeat(10) {
            advanceEventTime(16)
            x += width / 12f
            moveTo(Offset(x, center.y))
        }
        advanceEventTime(16)
        up()
    }

    // ─── Tryck och långtryck ───────────────────────────────────────────────────

    @Test fun `title and subtitle are shown`() = retryOnRenderGlitch {
        val scenario = launch {
            DagbokenEntryCard(title = "Löprunda", subtitle = "07:30  •  ⚡ 4", onClick = {})
        }
        try {
            composeRule.onNodeWithText("Löprunda").assertIsDisplayed()
            composeRule.onNodeWithText("07:30  •  ⚡ 4", substring = true).assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `tap opens the entry`() = retryOnRenderGlitch {
        var opened = 0
        val scenario = launch {
            DagbokenEntryCard(
                title    = "Löprunda",
                onClick  = { opened++ },
                modifier = Modifier.testTag("card"),
            )
        }
        try {
            composeRule.onNodeWithTag("card").performClick()
            assertEquals(1, opened)
        } finally {
            scenario.close()
        }
    }

    @Test fun `long press opens the menu without opening the entry`() = retryOnRenderGlitch {
        var opened = 0
        val scenario = launch {
            DagbokenEntryCard(
                title    = "Löprunda",
                onClick  = { opened++ },
                actions  = listOf(EntryAction("Redigera", Icons.Default.Edit) {}),
                onDelete = {},
                modifier = Modifier.testTag("card"),
            )
        }
        try {
            composeRule.onNodeWithTag("card").performTouchInput {
                down(center)
                advanceEventTime(600L)
                up()
            }
            composeRule.onNodeWithText("Redigera").assertIsDisplayed()
            assertEquals(0, opened)
        } finally {
            scenario.close()
        }
    }

    // ─── Kontextmenyn ──────────────────────────────────────────────────────────

    @Test fun `overflow button opens the same menu as long press`() = retryOnRenderGlitch {
        val scenario = launch {
            DagbokenEntryCard(
                title    = "Löprunda",
                onClick  = {},
                actions  = listOf(
                    EntryAction("Redigera", Icons.Default.Edit) {},
                    EntryAction("Markera favorit", Icons.Default.Star) {},
                ),
                onDelete = {},
            )
        }
        try {
            composeRule.onNodeWithContentDescription("Alternativ").performClick()
            composeRule.onNodeWithText("Redigera").assertIsDisplayed()
            composeRule.onNodeWithText("Markera favorit").assertIsDisplayed()
            composeRule.onNodeWithText("Ta bort").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `menu lists edit first and delete last`() = retryOnRenderGlitch {
        val scenario = launch {
            DagbokenEntryCard(
                title    = "Löprunda",
                onClick  = {},
                actions  = listOf(
                    EntryAction("Redigera", Icons.Default.Edit) {},
                    EntryAction("Markera favorit", Icons.Default.Star) {},
                ),
                onDelete = {},
            )
        }
        try {
            composeRule.onNodeWithContentDescription("Alternativ").performClick()
            val edit     = composeRule.onNodeWithText("Redigera").fetchSemanticsNode().boundsInRoot.top
            val favorite = composeRule.onNodeWithText("Markera favorit").fetchSemanticsNode().boundsInRoot.top
            val delete   = composeRule.onNodeWithText("Ta bort").fetchSemanticsNode().boundsInRoot.top
            assert(edit < favorite && favorite < delete) {
                "Förväntade ordningen Redigera → kontextval → Ta bort (fick $edit, $favorite, $delete)"
            }
        } finally {
            scenario.close()
        }
    }

    @Test fun `menu items invoke their action`() = retryOnRenderGlitch {
        var edited = 0
        val scenario = launch {
            DagbokenEntryCard(
                title   = "Löprunda",
                onClick = {},
                actions = listOf(EntryAction("Redigera", Icons.Default.Edit) { edited++ }),
            )
        }
        try {
            composeRule.onNodeWithContentDescription("Alternativ").performClick()
            composeRule.onNodeWithText("Redigera").performClick()
            assertEquals(1, edited)
        } finally {
            scenario.close()
        }
    }

    @Test fun `delete is absent from the menu when onDelete is null`() = retryOnRenderGlitch {
        val scenario = launch {
            DagbokenEntryCard(
                title   = "Löprunda",
                onClick = {},
                actions = listOf(EntryAction("Redigera", Icons.Default.Edit) {}),
            )
        }
        try {
            composeRule.onNodeWithContentDescription("Alternativ").performClick()
            composeRule.onNodeWithText("Redigera").assertIsDisplayed()
            composeRule.onNodeWithText("Ta bort").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test fun `no overflow button when the card has no actions`() = retryOnRenderGlitch {
        val scenario = launch {
            DagbokenEntryCard(title = "Löprunda", onClick = {})
        }
        try {
            composeRule.onNodeWithContentDescription("Alternativ").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    // ─── Expandering ───────────────────────────────────────────────────────────

    @Test fun `chevron expands details without opening the entry`() = retryOnRenderGlitch {
        var opened = 0
        val scenario = launch {
            DagbokenEntryCard(
                title           = "Löprunda",
                onClick         = { opened++ },
                expandedContent = { Text("Huvudvärk: 3") },
            )
        }
        try {
            composeRule.onNodeWithText("Huvudvärk: 3").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Visa mer").performClick()
            composeRule.onNodeWithText("Huvudvärk: 3").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Visa mindre").assertIsDisplayed()
            assertEquals(0, opened)
        } finally {
            scenario.close()
        }
    }

    @Test fun `no chevron when the card has no expanded content`() = retryOnRenderGlitch {
        val scenario = launch {
            DagbokenEntryCard(title = "Löprunda", onClick = {})
        }
        try {
            composeRule.onNodeWithContentDescription("Visa mer").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    // ─── Svep ──────────────────────────────────────────────────────────────────

    @Test fun `swipe left requests delete and leaves the card visible`() = retryOnRenderGlitch {
        var deletes = 0
        val scenario = launch {
            DagbokenEntryCard(
                title    = "Löprunda",
                onClick  = {},
                onDelete = { deletes++ },
                modifier = Modifier.testTag("card"),
            )
        }
        try {
            composeRule.onNodeWithTag("card").performTouchInput { slowSwipeLeft() }
            composeRule.waitForIdle()
            assertEquals(1, deletes)
            // Spökkort-regressionen: kortet får inte animeras bort innan
            // anroparens bekräftelsedialog har svarat.
            composeRule.onNodeWithText("Löprunda").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `swipe right does nothing`() = retryOnRenderGlitch {
        var deletes = 0
        val scenario = launch {
            DagbokenEntryCard(
                title    = "Löprunda",
                onClick  = {},
                onDelete = { deletes++ },
                modifier = Modifier.testTag("card"),
            )
        }
        try {
            composeRule.onNodeWithTag("card").performTouchInput { slowSwipeRight() }
            composeRule.waitForIdle()
            assertEquals(0, deletes)
            composeRule.onNodeWithText("Löprunda").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    // ─── Trailing-ordning och anteckningsikon ──────────────────────────────────

    @Test fun `no note icon when the entry has no note`() = retryOnRenderGlitch {
        val scenario = launch {
            DagbokenEntryCard(title = "Utan anteckning", onClick = {})
        }
        try {
            composeRule.onNodeWithContentDescription("Visa anteckning").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test fun `note icon is shown when the entry has a note`() = retryOnRenderGlitch {
        val scenario = launch {
            DagbokenEntryCard(title = "Med anteckning", onClick = {}, noteText = "Kändes tungt")
        }
        try {
            composeRule.onNodeWithContentDescription("Visa anteckning").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `trailing content follows chip then note then chevron then menu`() = retryOnRenderGlitch {
        val scenario = launch {
            DagbokenEntryCard(
                title           = "Löprunda",
                onClick         = {},
                trailingChip    = { Text("7/10") },
                noteText        = "Kändes tungt",
                expandedContent = { Text("Detaljer") },
                actions         = listOf(EntryAction("Redigera", Icons.Default.Edit) {}),
            )
        }
        try {
            val chip    = composeRule.onNodeWithText("7/10", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.left
            val note    = composeRule.onNodeWithContentDescription("Visa anteckning", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.left
            val chevron = composeRule.onNodeWithContentDescription("Visa mer", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.left
            val menu    = composeRule.onNodeWithContentDescription("Alternativ", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.left
            assert(chip < note && note < chevron && chevron < menu) {
                "Förväntade ordningen chip → anteckning → chevron → meny (fick $chip, $note, $chevron, $menu)"
            }
        } finally {
            scenario.close()
        }
    }

    // ─── Tillgänglighet ────────────────────────────────────────────────────────

    @Test fun `icon buttons keep the minimum touch target`() = retryOnRenderGlitch {
        val scenario = launch {
            DagbokenEntryCard(
                title           = "Löprunda",
                onClick         = {},
                expandedContent = { Text("Detaljer") },
                actions         = listOf(EntryAction("Redigera", Icons.Default.Edit) {}),
            )
        }
        try {
            composeRule.onNodeWithContentDescription("Alternativ")
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithContentDescription("Visa mer")
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        } finally {
            scenario.close()
        }
    }

    @Test fun `card exposes a click action for screen readers`() = retryOnRenderGlitch {
        val scenario = launch {
            DagbokenEntryCard(
                title    = "Löprunda",
                subtitle = "07:30",
                onClick  = {},
                modifier = Modifier.testTag("card"),
            )
        }
        try {
            composeRule.onNodeWithTag("card").assertHasClickAction()
            // Titel och undertitel läses som en enhet.
            composeRule.onNodeWithText("Löprunda").assertIsDisplayed()
            composeRule.onNodeWithText("07:30").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }
}
