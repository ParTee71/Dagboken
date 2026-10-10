package se.partee71.dagboken.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.testing.pixels
import se.partee71.dagboken.ui.theme.AppColors

/**
 * Skärmbytenas rörelse i `navigation/Transitions` (NAV-5, NAV-11): framåt glider den nya skärmen in från
 * höger och den gamla ut åt vänster, bakåt och predictive back tvärtom – och båda tonas under tiden.
 * Specen körs i en `AnimatedContent` med testklockan, så att läget mitt i rörelsen går att mäta.
 */
@RunWith(RobolectricTestRunner::class)
class TransitionsTest {

    @get:Rule
    val rule = createComposeRule()

    private val oldColor = AppColors.light.secondary
    private val newColor = AppColors.light.primary

    private class Midway(val oldLeft: Dp, val newLeft: Dp, val oldPixels: Int, val newPixels: Int)

    /** Byter från skärm 0 till 1 med [spec] och mäter en bit in i rörelsen; därefter får rörelsen landa. */
    private fun switchScreens(spec: AnimatedContentTransitionScope<Scene<AppKey>>.() -> ContentTransform): Midway {
        var target by mutableIntStateOf(0)
        rule.setContent {
            AnimatedContent(
                targetState = target,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    // Specen läser inte sitt scope; nyckeltypen spelar ingen roll vid körning (via Any: okontrollerad, inte omöjlig).
                    @Suppress("UNCHECKED_CAST")
                    ((this as Any) as AnimatedContentTransitionScope<Scene<AppKey>>).spec()
                },
                label = "skärmbyte",
            ) { screen ->
                Box(Modifier.fillMaxSize().background(if (screen == 0) oldColor else newColor).testTag("skärm $screen"))
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        target = 1
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(MIDWAY_MILLIS)
        val midway = Midway(
            oldLeft = rule.onNodeWithTag("skärm 0").getUnclippedBoundsInRoot().left,
            newLeft = rule.onNodeWithTag("skärm 1").getUnclippedBoundsInRoot().left,
            oldPixels = rule.onRoot().pixels(oldColor),
            newPixels = rule.onRoot().pixels(newColor),
        )
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
        rule.onNodeWithTag("skärm 0").assertDoesNotExist()
        assertEquals(0.dp, rule.onNodeWithTag("skärm 1").getUnclippedBoundsInRoot().left, "rörelsen landar på plats")
        assertTrue(rule.onRoot().pixels(newColor) > 0, "den nya skärmen syns helt när rörelsen landat")
        return midway
    }

    /** Båda skärmarna är halvgenomskinliga mitt i rörelsen: ingen bildpunkt har någon av dem i full färg. */
    private fun Midway.assertFades() {
        assertEquals(0, oldPixels, "den gamla skärmen tonas ut")
        assertEquals(0, newPixels, "den nya skärmen tonas fram")
    }

    @Test
    fun `framåt glider den nya skärmen in från höger och tonas fram (NAV-5)`() {
        val midway = switchScreens(Transitions.forward)
        assertTrue(midway.newLeft > 0.dp, "den nya skärmen kommer från höger: ${midway.newLeft}")
        assertTrue(midway.oldLeft < 0.dp, "den gamla skärmen glider åt vänster: ${midway.oldLeft}")
        midway.assertFades()
    }

    @Test
    fun `bakåt är samma rörelse baklänges (NAV-5)`() {
        val midway = switchScreens(Transitions.back)
        assertTrue(midway.newLeft < 0.dp, "skärmen under kommer från vänster: ${midway.newLeft}")
        assertTrue(midway.oldLeft > 0.dp, "den stängda skärmen glider åt höger: ${midway.oldLeft}")
        midway.assertFades()
    }

    @Test
    fun `predictive back följer bakåtrörelsen (NAV-5, NFR-4)`() {
        val midway = switchScreens { Transitions.predictiveBack(this, 0) }
        assertTrue(midway.newLeft < 0.dp, "skärmen under kommer från vänster: ${midway.newLeft}")
        assertTrue(midway.oldLeft > 0.dp, "den stängda skärmen glider åt höger: ${midway.oldLeft}")
        midway.assertFades()
    }

    private companion object {
        /** Tre bildrutor in i fjädringen – långt från både start och mål. */
        const val MIDWAY_MILLIS = 48L
        const val SETTLE_MILLIS = 2_000L
    }
}
