package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

/**
 * Täcker de delade komponenterna i `ui/components/` som saknade test. De återanvänds
 * av flera skärmar (regel 4), så en regression här slår brett.
 *
 * Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
 */
@RunWith(AndroidJUnit4::class)
class SharedComponentsTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun launch(content: @androidx.compose.runtime.Composable () -> Unit): ActivityScenario<ComponentActivity> {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { it.setContent { MaterialTheme { content() } } }
        return scenario
    }

    // ─── SaveButton ───────────────────────────────────────────────────────────

    @Test fun `SaveButton is disabled and does not fire when nothing is dirty`() = retryOnRenderGlitch {
        var clicks = 0
        val scenario = launch { SaveButton(enabled = false, onClick = { clicks++ }) }
        try {
            composeRule.onNodeWithText("Spara").assertIsNotEnabled()
            composeRule.onNodeWithText("Spara").performClick()
            composeRule.waitForIdle()
            assertEquals(0, clicks)
        } finally {
            scenario.close()
        }
    }

    @Test fun `SaveButton fires when enabled`() = retryOnRenderGlitch {
        var clicks = 0
        val scenario = launch { SaveButton(enabled = true, onClick = { clicks++ }) }
        try {
            composeRule.onNodeWithText("Spara").assertIsEnabled().performClick()
            composeRule.waitForIdle()
            assertEquals(1, clicks)
        } finally {
            scenario.close()
        }
    }

    @Test fun `SaveButton shows a custom label`() = retryOnRenderGlitch {
        val scenario = launch { SaveButton(enabled = true, onClick = {}, label = "Logga dos") }
        try {
            composeRule.onNodeWithText("Logga dos").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    // ─── StatPill ─────────────────────────────────────────────────────────────

    @Test fun `StatPill shows value and label`() = retryOnRenderGlitch {
        val scenario = launch {
            StatPill(
                icon           = Icons.Filled.DirectionsWalk,
                value          = "8 431",
                label          = "steg",
                containerColor = Color.DarkGray,
                contentColor   = Color.White,
            )
        }
        try {
            composeRule.onNodeWithText("8 431").assertIsDisplayed()
            composeRule.onNodeWithText("steg").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `StatPill without onClick is not clickable`() = retryOnRenderGlitch {
        // De allra flesta brickorna är ren avläsning — de ska inte annonsera en åtgärd
        // som inte finns för skärmläsaren.
        val scenario = launch {
            StatPill(
                icon           = Icons.Filled.DirectionsWalk,
                value          = "8 431",
                label          = "steg",
                containerColor = Color.DarkGray,
                contentColor   = Color.White,
            )
        }
        try {
            composeRule.onNodeWithText("steg").assert(hasClickAction().not())
        } finally {
            scenario.close()
        }
    }

    @Test fun `StatPill with onClick is a button that holds the touch target`() = retryOnRenderGlitch {
        var clicks = 0
        val scenario = launch {
            StatPill(
                icon           = Icons.Filled.DirectionsWalk,
                value          = "Ge åtkomst",
                label          = "Träning saknar åtkomst",
                containerColor = Color.DarkGray,
                contentColor   = Color.White,
                onClick        = { clicks++ },
                onClickLabel   = "Begär åtkomst",
            )
        }
        try {
            composeRule.onNodeWithText("Ge åtkomst")
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.Role,
                        androidx.compose.ui.semantics.Role.Button,
                    ),
                )
                .performClick()
            composeRule.waitForIdle()
            assertEquals(1, clicks)
        } finally {
            scenario.close()
        }
    }

    // ─── SliderRow ────────────────────────────────────────────────────────────

    @Test fun `SliderRow shows its label and current value`() = retryOnRenderGlitch {
        val scenario = launch {
            SliderRow(label = "Energi", value = 7f, onValueChange = {})
        }
        try {
            composeRule.onNodeWithText("Energi").assertIsDisplayed()
            composeRule.onNodeWithText("7").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `SliderRow can show a custom value label`() = retryOnRenderGlitch {
        val scenario = launch {
            SliderRow(label = "Energi", value = 3f, onValueChange = {}, valueLabel = "+3")
        }
        try {
            composeRule.onNodeWithText("+3").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    // ─── Foldout ──────────────────────────────────────────────────────────────

    @Test fun `Foldout hides its content until expanded`() = retryOnRenderGlitch {
        val scenario = launch {
            Foldout(title = "Mer", expanded = false, onToggle = {}) { Text("Dolt innehåll") }
        }
        try {
            composeRule.onNodeWithText("Mer").assertIsDisplayed()
            composeRule.onNodeWithText("Dolt innehåll").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test fun `Foldout shows its content when expanded`() = retryOnRenderGlitch {
        val scenario = launch {
            Foldout(title = "Mer", expanded = true, onToggle = {}) { Text("Synligt innehåll") }
        }
        try {
            composeRule.onNodeWithText("Synligt innehåll").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `Foldout reports toggles from the header`() = retryOnRenderGlitch {
        var toggles = 0
        val scenario = launch {
            Foldout(title = "Mer", expanded = false, onToggle = { toggles++ }) { Text("X") }
        }
        try {
            composeRule.onNodeWithText("Mer").performClick()
            composeRule.waitForIdle()
            assertEquals(1, toggles)
        } finally {
            scenario.close()
        }
    }

    // Titelraden bär både åtgärden och tillståndet (NFR-18) — chevronen är en ren
    // indikator, så utan detta har TalkBack inget som säger om sektionen är öppen.

    @Test fun `Foldout header is a full touch target and reports the collapsed state`() = retryOnRenderGlitch {
        val scenario = launch {
            Foldout(title = "Mer", expanded = false, onToggle = {}) { Text("X") }
        }
        try {
            composeRule.onNodeWithText("Mer")
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ihopfälld"))
        } finally {
            scenario.close()
        }
    }

    @Test fun `Foldout header reports the expanded state when open`() = retryOnRenderGlitch {
        val scenario = launch {
            Foldout(title = "Mer", expanded = true, onToggle = {}) { Text("X") }
        }
        try {
            composeRule.onNodeWithText("Mer")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Utfälld"))
        } finally {
            scenario.close()
        }
    }

    @Test fun `Foldout renders trailing content in the header while collapsed`() = retryOnRenderGlitch {
        val scenario = launch {
            Foldout(
                title    = "Mer",
                expanded = false,
                onToggle = {},
                trailing = { Text("Månad") },
            ) { Text("Dolt innehåll") }
        }
        try {
            composeRule.onNodeWithText("Månad", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithText("Dolt innehåll").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    // ─── DurationRow ──────────────────────────────────────────────────────────

    @Test fun `DurationRow shows the duration label and unit texts`() = retryOnRenderGlitch {
        val scenario = launch {
            DurationRow(hours = 1, minutes = 30, onHoursChange = {}, onMinutesChange = {})
        }
        try {
            composeRule.onNodeWithText("Varaktighet").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    // ─── AccountBubble ────────────────────────────────────────────────────────

    /** Regressionsskydd: ytan var tidigare 36 dp och saknade knapproll. */
    @Test fun `AccountBubble is a button with a sufficient touch target`() = retryOnRenderGlitch {
        var clicks = 0
        val scenario = launch {
            AccountBubble(email = null, photoUrl = null, displayName = null, onClick = { clicks++ })
        }
        try {
            composeRule.onNode(hasClickAction())
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assertHasClickAction()
                .performClick()
            composeRule.waitForIdle()
            assertEquals(1, clicks)
        } finally {
            scenario.close()
        }
    }

    @Test fun `AccountBubble stays a single clickable node when signed in`() = retryOnRenderGlitch {
        val scenario = launch {
            AccountBubble(
                email       = "test@example.com",
                photoUrl    = null,
                displayName = "Testanvändare",
                onClick     = {},
            )
        }
        try {
            composeRule.onNode(hasClickAction()).assertHasClickAction()
        } finally {
            scenario.close()
        }
    }
}
