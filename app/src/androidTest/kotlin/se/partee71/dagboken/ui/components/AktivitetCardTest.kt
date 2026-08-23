package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.util.retryOnRenderGlitch

/**
 * Kortet migrerades till kortstandarden i #200: tryck redigerar (expanderade
 * tidigare), och expanderingen ligger på chevron-knappen.
 */
@RunWith(AndroidJUnit4::class)
class AktivitetCardTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun aktivitet(symptom: String = "") = Aktivitet(
        id        = "a1",
        timestamp = "x",
        datum     = "2026-01-01",
        tid       = "08:00",
        aktivitet = "Promenad",
        energy    = 3,
        stress    = 2,
        somatiska = 0,
        symptom   = symptom,
    )

    private fun launch(content: @Composable () -> Unit): ActivityScenario<ComponentActivity> {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { it.setContent { MaterialTheme { content() } } }
        return scenario
    }

    /** Långsamt svep förbi den positionella tröskeln — se DagbokenEntryCardTest. */
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

    @Test fun `tap edits the entry`() = retryOnRenderGlitch {
        var edited = 0
        val scenario = launch {
            AktivitetCard(
                aktivitet = aktivitet(symptom = "Huvudvärk:3"),
                onEdit    = { edited++ },
                onDelete  = {},
                modifier  = Modifier.testTag("card"),
            )
        }
        try {
            composeRule.onNodeWithTag("card").performClick()
            assertEquals(1, edited)
        } finally {
            scenario.close()
        }
    }

    @Test fun `tap edits an entry without symptoms too`() = retryOnRenderGlitch {
        // Tidigare var ett kort utan symptom inte klickbart alls (onClick = null).
        var edited = 0
        val scenario = launch {
            AktivitetCard(
                aktivitet = aktivitet(),
                onEdit    = { edited++ },
                onDelete  = {},
                modifier  = Modifier.testTag("card"),
            )
        }
        try {
            composeRule.onNodeWithTag("card").performClick()
            assertEquals(1, edited)
        } finally {
            scenario.close()
        }
    }

    @Test fun `chevron expands the symptoms without editing`() = retryOnRenderGlitch {
        var edited = 0
        val scenario = launch {
            AktivitetCard(
                aktivitet = aktivitet(symptom = "Huvudvärk:3"),
                onEdit    = { edited++ },
                onDelete  = {},
            )
        }
        try {
            composeRule.onNodeWithText("Huvudvärk").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Visa mer").performClick()
            composeRule.onNodeWithText("Huvudvärk").assertIsDisplayed()
            assertEquals(0, edited)
        } finally {
            scenario.close()
        }
    }

    @Test fun `no chevron when the entry has no symptoms`() = retryOnRenderGlitch {
        val scenario = launch {
            AktivitetCard(aktivitet = aktivitet(), onEdit = {}, onDelete = {})
        }
        try {
            composeRule.onNodeWithContentDescription("Visa mer").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test fun `menu offers edit and delete`() = retryOnRenderGlitch {
        var edited = 0
        val scenario = launch {
            AktivitetCard(aktivitet = aktivitet(), onEdit = { edited++ }, onDelete = {})
        }
        try {
            composeRule.onNodeWithContentDescription("Alternativ").performClick()
            composeRule.onNodeWithText("Ta bort").assertIsDisplayed()
            composeRule.onNodeWithText("Redigera").performClick()
            assertEquals(1, edited)
        } finally {
            scenario.close()
        }
    }

    @Test fun `swipe left requests delete and leaves the card visible`() = retryOnRenderGlitch {
        var deletes = 0
        val scenario = launch {
            AktivitetCard(
                aktivitet = aktivitet(),
                onEdit    = {},
                onDelete  = { deletes++ },
                modifier  = Modifier.testTag("card"),
            )
        }
        try {
            composeRule.onNodeWithTag("card").performTouchInput { slowSwipeLeft() }
            composeRule.waitForIdle()
            assertEquals(1, deletes)
            composeRule.onNodeWithText("Promenad").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `note icon is shown when the entry has a note`() = retryOnRenderGlitch {
        val scenario = launch {
            AktivitetCard(
                aktivitet = aktivitet(),
                onEdit    = {},
                onDelete  = {},
                noteText  = "Kändes tungt",
            )
        }
        try {
            composeRule.onNodeWithContentDescription("Visa anteckning").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }
}
