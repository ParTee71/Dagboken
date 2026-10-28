package se.partee71.dagboken.ui.components

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.testing.captureLightAndDarkPaused
import se.partee71.dagboken.ui.common.BREATH_MILLIS
import se.partee71.dagboken.ui.common.BREATH_SETTLE_MILLIS
import se.partee71.dagboken.ui.common.BreathPhase
import se.partee71.dagboken.ui.common.HILL_DRIFT_MILLIS
import se.partee71.dagboken.ui.common.formatDuration
import se.partee71.dagboken.ui.common.rememberBreathPhase
import se.partee71.dagboken.ui.common.rememberReducedMotion
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.ui.components.AppCard
import se.partee71.ui.theme.Spacing

/**
 * Stundens delade delar (NOT-20): solen som andas (`SunsetBreathing`, 4-4-takt, kullar, reducerad rörelse), andningsfasen, längden i text (`formatDuration`) och
 * reglaget med steg och värdetext (`ValueSlider(step, valueText)`).
 */
@RunWith(RobolectricTestRunner::class)
class MomentComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    /** Solens utbredning i bilden: antalet punkter som är ljusare än himlen och kullarna, och deras tyngdpunkt i höjdled. */
    private data class Sun(val area: Int, val centerY: Int)

    private fun SemanticsNodeInteraction.sun(): Sun {
        val map = captureToImage().toPixelMap()
        var area = 0
        var sumY = 0L
        for (x in 0 until map.width) {
            for (y in 0 until map.height) {
                if (map[x, y].luminance() > SUN_LUMINANCE) {
                    area++
                    sumY += y
                }
            }
        }
        return Sun(area, if (area == 0) 0 else (sumY / area).toInt())
    }

    /** Raderna där kullarnas kammar går (under solens dis, som andas i en annan takt – alltså nedanför `HILLS_FLOOR` och bakre kullens kam) – ändras när de glider. */
    private fun SemanticsNodeInteraction.hillRow(): List<Color> {
        val map = captureToImage().toPixelMap()
        return listOf(0.49f, 0.5f, 0.51f).flatMap { row -> List(map.width) { map[it, (map.height * row).toInt()] } }
    }

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
    fun `solen andas in i fyra sekunder och ut i fyra, stiger och växer under inandningen`() {
        show { SunsetBreathing(Modifier.size(BIG_WIDTH, BIG_HEIGHT).testTag(SKY), reducedMotion = false) }
        val sky = rule.onNodeWithTag(SKY)
        rule.onNodeWithText("Andas in").assertExists()
        val rest = sky.sun()
        rule.mainClock.advanceTimeBy(3_000)
        val large = sky.sun()
        assertTrue(large.area > rest.area, "växer: ${rest.area} → ${large.area}")
        assertTrue(large.centerY < rest.centerY, "stiger: ${rest.centerY} → ${large.centerY}")

        rule.mainClock.advanceTimeBy(1_100)
        rule.onNodeWithText("Andas ut").assertExists()
        rule.mainClock.advanceTimeBy(2_900)
        assertTrue(sky.sun().area < large.area, "krymper under utandningen")
        rule.mainClock.advanceTimeBy(1_100)
        rule.onNodeWithText("Andas in").assertExists()
    }

    @Test
    fun `vid reducerad rörelse står solen stilla och bara texten byter`() {
        show { SunsetBreathing(Modifier.size(BIG_WIDTH, BIG_HEIGHT).testTag(SKY), reducedMotion = true) }
        val sky = rule.onNodeWithTag(SKY)
        val before = sky.sun()
        val hills = sky.hillRow()
        rule.onNodeWithText("Andas in").assertExists()
        rule.mainClock.advanceTimeBy(4_100)
        rule.onNodeWithText("Andas ut").assertExists()
        // Texten byter ord (några punkter i solen), men solen och kullarna är desamma.
        val after = sky.sun()
        assertEquals(before.centerY, after.centerY)
        assertEquals(before.area.toDouble(), after.area.toDouble(), before.area * 0.05)
        assertEquals(hills, sky.hillRow(), "kullarna står stilla")
    }

    @Test
    fun `klar sol vilar utan text och stannar kvar`() {
        show { SunsetBreathing(Modifier.size(BIG_WIDTH, BIG_HEIGHT).testTag(SKY), active = false, reducedMotion = false) }
        val sky = rule.onNodeWithTag(SKY)
        rule.onNodeWithText("Andas in").assertDoesNotExist()
        rule.onNodeWithText("Andas ut").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(2_000)
        val rest = sky.sun()
        rule.mainClock.advanceTimeBy(6_000)
        assertEquals(rest, sky.sun())
    }

    @Test
    fun `solen stannar mjukt när pausen blir klar`() {
        var active by mutableStateOf(true)
        show { SunsetBreathing(Modifier.size(BIG_WIDTH, BIG_HEIGHT).testTag(SKY), active = active, reducedMotion = false) }
        val sky = rule.onNodeWithTag(SKY)
        rule.mainClock.advanceTimeBy(3_900)
        val full = sky.sun().area
        active = false
        rule.mainClock.advanceTimeBy(BREATH_SETTLE_MILLIS / 2L)
        val halfway = sky.sun().area
        rule.mainClock.advanceTimeBy(BREATH_SETTLE_MILLIS.toLong())
        rule.onNodeWithText("Andas in").assertDoesNotExist()
        val settled = sky.sun().area
        assertTrue(halfway in (settled + 1) until full, "mjuk övergång: $full → $halfway → $settled")
        rule.mainClock.advanceTimeBy(6_000)
        assertEquals(settled, sky.sun().area, "står kvar i vila")
    }

    @Test
    fun `kullarna glider sömlöst ett varv på 90 sekunder`() {
        show { SunsetBreathing(Modifier.size(BIG_WIDTH, BIG_HEIGHT).testTag(SKY), reducedMotion = false) }
        val sky = rule.onNodeWithTag(SKY)
        val start = sky.hillRow()
        rule.mainClock.advanceTimeBy(HILL_DRIFT_MILLIS / 2L)
        assertTrue(sky.hillRow() != start, "kullarna rör sig")
        rule.mainClock.advanceTimeBy(HILL_DRIFT_MILLIS / 2L)
        val lap = sky.hillRow()
        val differing = start.indices.count { i -> abs(start[i].red - lap[i].red) + abs(start[i].green - lap[i].green) + abs(start[i].blue - lap[i].blue) > COLOR_TOLERANCE }
        assertTrue(differing < start.size / 50, "ett helt varv ger samma bild: $differing av ${start.size} punkter skiljer")
        HillLayer.entries.forEach { layer ->
            for (i in 0..10) assertEquals(layer.crest(i / 10f, 0f), layer.crest(i / 10f, 1f), 1e-4f, "$layer sömlös")
            assertTrue(layer.topMost >= HILLS_TOP && HILLS_TOP > 0.25f)
        }
    }

    @Test
    fun `solens storlek och plats följer andningens grad`() {
        val size = Size(360f, 800f)
        val rest = sunGeometry(size, 0f)
        val full = sunGeometry(size, 1f)
        assertTrue(full.radius > rest.radius && full.center.y < rest.center.y)
        assertEquals(size.width / 2, rest.center.x)
        // Solen ryms ovanför kullarna också när den är störst.
        assertTrue(full.center.y + full.radius < HILLS_TOP * size.height, "solen ryms ovanför kullarna")
    }

    @Test
    fun `texten ryms alltid i solen i en liten ruta`() = textFitsSun(SKY_WIDTH, SKY_HEIGHT)

    @Test
    fun `texten ryms alltid i solen på en telefonskärm`() = textFitsSun(BIG_WIDTH, BIG_HEIGHT)

    private fun textFitsSun(width: Dp, height: Dp) {
        run {
            show { SunsetBreathing(Modifier.size(width, height).testTag(SKY), reducedMotion = true) }
            val image = rule.onNodeWithTag(SKY).captureToImage().toPixelMap()
            val bounds = rule.onNodeWithText("Andas in").getBoundsInRoot()
            val density = rule.density.density
            val y = ((bounds.top + bounds.bottom).value / 2 * density).toInt()
            listOf(bounds.left.value * density + 1, bounds.right.value * density - 1).forEach { x ->
                assertTrue(image[x.toInt(), y].luminance() > SUN_EDGE_LUMINANCE, "texten ligger på solen i ${width}x$height: $x,$y")
            }
        }
    }

    @Test
    fun `kullarna bromsar mjukt till stillastående när pausen är klar`() {
        var active by mutableStateOf(true)
        show { SunsetBreathing(Modifier.size(BIG_WIDTH, BIG_HEIGHT).testTag(SKY), active = active, reducedMotion = false) }
        val sky = rule.onNodeWithTag(SKY)
        val start = sky.hillRow()
        rule.mainClock.advanceTimeBy(10_000)
        val moving = sky.hillRow()
        assertTrue(moving != start, "kullarna rör sig medan pausen pågår")
        active = false
        rule.mainClock.advanceTimeBy(500)
        val slowing = sky.hillRow()
        assertTrue(slowing != moving, "bromsar, stannar inte tvärt")
        rule.mainClock.advanceTimeBy(15_000)
        val stopped = sky.hillRow()
        rule.mainClock.advanceTimeBy(20_000)
        assertEquals(stopped, sky.hillRow(), "står stilla")
    }

    @Test
    fun `kullarna lyfts så att allt från innehållets överkant ligger på mörk grund`() {
        assertEquals(0f, hillsLift(Float.NaN))
        assertEquals(0f, hillsLift(HILLS_FLOOR + 0.2f), "ligger innehållet lågt står kullarna kvar")
        assertEquals(-0.1f, hillsLift(HILLS_FLOOR - 0.1f), 1e-6f)
        HillLayer.entries.forEach { assertTrue(it.crest(0.3f, 0.2f, lift = -2f) >= 0f, "kammen lämnar aldrig skärmen") }
        // Lyfter man till nivån täcker bakre kullen allt under den, oavsett glidning.
        val level = 0.25f
        for (i in 0..20) assertTrue(HillLayer.Back.crest(i / 20f, i / 20f, hillsLift(level)) <= level + 1e-4f)
    }

    @Test
    fun `solens dis stannar kvar vid solen och avtar mjukt`() {
        val sun = sunGeometry(Size(360f, 800f), 1f)
        assertTrue(hazeAlpha(1f, 0f) > hazeAlpha(1f, 0.5f) && hazeAlpha(1f, 0.5f) > hazeAlpha(1f, 1f))
        assertEquals(0f, hazeAlpha(1f, 1f))
        assertTrue(hazeRadius(sun, 1f) < 2f * sun.radius)
    }

    @Test
    fun `en rullande förälder med obegränsad höjd kraschar inte`() {
        show { Column(Modifier.verticalScroll(rememberScrollState())) { SunsetBreathing(Modifier.testTag(SKY), reducedMotion = false) } }
        rule.onNodeWithText("Andas in").assertExists()
        rule.mainClock.advanceTimeBy(4_000)
        rule.onNodeWithTag(SKY).assertExists()
    }

    @Test
    fun `rememberBreathPhase byter fas var fjärde sekund och står still vid reducerad rörelse`() {
        val phases = mutableListOf<BreathPhase>()
        show {
            phases += rememberBreathPhase(active = true, reducedMotion = false)
            phases += rememberBreathPhase(active = true, reducedMotion = true)
        }
        val (moving, still) = phases.take(2)
        assertTrue(moving.inhale && still.inhale)
        rule.mainClock.advanceTimeBy(BREATH_MILLIS - 500L)
        assertTrue(moving.progress in 0.5f..0.999f && moving.inhale, "mot full: ${moving.progress}")
        rule.mainClock.advanceTimeBy(700)
        assertTrue(!moving.inhale && !still.inhale, "ut efter ${BREATH_MILLIS} ms")
        rule.mainClock.advanceTimeBy(BREATH_MILLIS.toLong())
        assertTrue(moving.inhale && still.inhale, "in igen efter åtta sekunder")
        assertEquals(0f, still.progress)
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
    fun `SunsetBreathing - med och utan rörelse, klar`() = rule.captureLightAndDarkPaused("SunsetBreathing_lagen") {
        Row(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SunsetBreathing(Modifier.size(SKY_WIDTH, SKY_HEIGHT), reducedMotion = false)
            SunsetBreathing(Modifier.size(SKY_WIDTH, SKY_HEIGHT), reducedMotion = true)
            SunsetBreathing(Modifier.size(SKY_WIDTH, SKY_HEIGHT), active = false, reducedMotion = false)
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
        const val SKY = "sky"
        val BIG_WIDTH = 330.dp
        val BIG_HEIGHT = 700.dp
        const val SUN_LUMINANCE = 0.6f
        const val SUN_EDGE_LUMINANCE = 0.3f
        const val COLOR_TOLERANCE = 0.05f
        val SKY_WIDTH = 110.dp
        val SKY_HEIGHT = 200.dp
    }
}
