package se.partee71.dagboken.ui.components

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.testing.captureLightAndDarkPaused
import se.partee71.dagboken.testing.pixels
import se.partee71.dagboken.ui.common.formatDuration
import se.partee71.dagboken.ui.common.rememberReducedMotion
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.ui.components.AppCard
import se.partee71.ui.theme.Spacing

/**
 * Stundens delade delar (NOT-20): andningscirkeln (4-4-takt, reducerad rörelse), längden i text (`formatDuration`) och
 * reglaget med steg och värdetext (`ValueSlider(step, valueText)`).
 */
@RunWith(RobolectricTestRunner::class)
class MomentComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { DagbokenTheme(content = content) }
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `formatDuration skriver sekunder, hela minuter och minuter med sekunder`() {
        val texts = mutableListOf<String>()
        show { listOf(15, 45, 60, 120, 135, 300).forEach { texts += formatDuration(it) } }
        assertEquals(listOf("15 s", "45 s", "1 min", "2 min", "2 min 15 s", "5 min"), texts)
    }

    @Test
    fun `andningscirkeln andas in i fyra sekunder och ut i fyra, och växer under inandningen`() {
        var fill = Color.Unspecified
        show {
            fill = MaterialTheme.colorScheme.primaryContainer
            BreathingCircle(Modifier.testTag(CIRCLE), reducedMotion = false)
        }
        val circle = rule.onNodeWithTag(CIRCLE)
        rule.onNodeWithText("Andas in").assertExists()
        val small = circle.pixels(fill)
        rule.mainClock.advanceTimeBy(3_000)
        val large = circle.pixels(fill)
        assertTrue(large > small, "växer: $small → $large")

        rule.mainClock.advanceTimeBy(1_100)
        rule.onNodeWithText("Andas ut").assertExists()
        rule.mainClock.advanceTimeBy(4_000)
        rule.onNodeWithText("Andas in").assertExists()
    }

    @Test
    fun `vid reducerad rörelse står bara texten kvar, utan cirkel`() {
        var fill = Color.Unspecified
        show {
            fill = MaterialTheme.colorScheme.primaryContainer
            BreathingCircle(Modifier.testTag(CIRCLE), reducedMotion = true)
        }
        rule.onNodeWithText("Andas in").assertExists()
        assertEquals(0, rule.onNodeWithTag(CIRCLE).pixels(fill))
        rule.mainClock.advanceTimeBy(4_100)
        rule.onNodeWithText("Andas ut").assertExists()
    }

    @Test
    fun `reducerad rörelse följer systemets Ta bort animationer`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var reduced: Boolean? = null
        show { reduced = rememberReducedMotion() }
        assertEquals(true, reduced)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test
    fun `klar cirkel vilar utan text`() {
        show { BreathingCircle(Modifier.testTag(CIRCLE), active = false, reducedMotion = false) }
        rule.onNodeWithText("Andas in").assertDoesNotExist()
        rule.onNodeWithText("Andas ut").assertDoesNotExist()
    }

    @Test
    fun `reglaget med steg stannar på var 15e sekund och läser värdet som text`() {
        var seconds by mutableIntStateOf(120)
        rule.setContent {
            DagbokenTheme {
                var value by remember { mutableIntStateOf(seconds) }
                ValueSlider("Pausens längd", value, {
                    value = it
                    seconds = it
                }, valueRange = 15..300, step = 15, valueText = formatDuration(value))
            }
        }
        val slider = rule.onNodeWithContentDescription("Pausens längd")
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 min"))
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(52f) }
        rule.waitForIdle()
        assertEquals(45, seconds)
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "45 s"))
        rule.onNodeWithText("45 s", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Hög", useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Skärmdumpar ──

    @Test
    fun `BreathingCircle - med och utan rörelse, klar`() = rule.captureLightAndDarkPaused("BreathingCircle_lagen") {
        Row(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            BreathingCircle(size = SHOT_SIZE, reducedMotion = false)
            BreathingCircle(size = SHOT_SIZE, reducedMotion = true)
            BreathingCircle(size = SHOT_SIZE, active = false, reducedMotion = false)
        }
    }

    @Test
    fun `ValueSlider - längd med steg och värdetext`() = rule.captureLightAndDarkPaused("ValueSlider_langd") {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.l)) {
            AppCard {
                ValueSlider("Pausens längd", 135, {}, valueRange = 15..300, step = 15, valueText = "2 min 15 s")
                ValueSlider("Pausens längd", 45, {}, valueRange = 15..300, step = 15, valueText = "45 s", enabled = false)
            }
        }
    }

    private companion object {
        const val CIRCLE = "circle"
        val SHOT_SIZE = 110.dp
    }
}
