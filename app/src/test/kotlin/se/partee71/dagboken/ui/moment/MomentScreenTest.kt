package se.partee71.dagboken.ui.moment

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.ReflectionPrompts
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.reminders.MomentLaunch
import se.partee71.dagboken.testing.captureLightAndDarkPaused
import se.partee71.dagboken.ui.components.HILLS_FLOOR
import se.partee71.dagboken.ui.components.HILLS_TOP
import se.partee71.dagboken.ui.components.hazeAlpha
import se.partee71.dagboken.ui.components.hazeRadius
import se.partee71.dagboken.ui.components.skyColorAt
import se.partee71.dagboken.ui.components.sunGeometry
import se.partee71.dagboken.ui.components.textRadius
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Stundens helskärm (NOT-20, NOT-21): rubriken med tillfälle och tid, nedräkningen, Välj känslor först när pausen är
 * klar, Inte nu, hjälpfrågan och reducerad rörelse – och skärmdumpar ljust + mörkt (start, mitt i, klar) bredvid mockupen
 * (tavla H). Påhittad data; inga hälsodata finns på skärmen.
 */
@RunWith(RobolectricTestRunner::class)
class MomentScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prompts = context.resources.getStringArray(R.array.moment_prompts)
    private val today = LocalDate(2026, 10, 14)

    private fun show(content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { DagbokenTheme(content = content) }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun screen(remaining: Int, prompt: String? = prompts[0], reducedMotion: Boolean = false, onChoose: () -> Unit = {}, onNotNow: () -> Unit = {}) = show {
        MomentScreen("Läggdags", "21:00", prompt, { remaining }, onChoose, onNotNow, reducedMotion = reducedMotion)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    /** Stunden i en ruta av [width] × [height] med textstorlek [fontScale]. */
    private fun screenIn(width: Dp, height: Dp, fontScale: Float = 1f, prompt: String? = prompts[0]) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DagbokenTheme {
                    Box(Modifier.size(width, height)) { MomentScreen("Läggdags", "21:00", prompt, { 100 }, {}, {}, reducedMotion = false) }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(100)
    }

    private val chooseFeelings get() = rule.onNodeWithText(context.getString(R.string.moment_choose_feelings))

    @Test
    fun `hjälpfrågorna är exakt 30, i nutid och om nuet (NOT-21)`() {
        assertEquals(ReflectionPrompts.COUNT, prompts.size)
        assertEquals(prompts.size, prompts.toSet().size, "inga dubbletter")
        val pastOrCause = Regex("""\b(idag|i dag|igår|i går|kände|var det|varför|hände|hade)\b""", RegexOption.IGNORE_CASE)
        prompts.forEach { assertEquals(null, pastOrCause.find(it)?.value, "\"$it\" ska handla om nuet") }
        listOf("Finns det mer än en känsla? Var i kroppen känns den?", "Vad är starkast just nu?", "Är det lätt eller tungt att andas?")
            .forEach { assertEquals(true, it in prompts, "issuets exempel: $it") }
    }

    @Test
    fun `rubriken har tillfälle och tid, frågan är känslostegets, och Välj känslor är inaktiv tills pausen är klar (NOT-20)`() {
        screen(remaining = 100)
        rule.onNodeWithText("Stunden · Läggdags 21:00").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithText("Lägg ner det du gör. Andas med solen.").assertExists()
        rule.onNodeWithText("Vad känner du just nu?").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithText(prompts[0]).assertExists()
        rule.onNodeWithText("Ingen text behövs. Känn efter en stund – orden väljer du sedan.").assertExists()
        rule.onNodeWithText("1:40 kvar").assertExists()
        rule.onNodeWithContentDescription("1 min 40 s kvar").assertExists()
        chooseFeelings.assertIsNotEnabled()
    }

    @Test
    fun `texter och knappar nedtill ligger på kullarnas mörka grund (NOT-20)`() {
        screen(remaining = 100)
        val height = rule.onRoot().getBoundsInRoot().height
        val top = rule.onNodeWithText("1:40 kvar").getBoundsInRoot().top
        assertTrue(top >= height * HILLS_FLOOR, "nedräkningen börjar på $top av $height")
        rule.onNodeWithText("Lägg ner det du gör. Andas med solen.").getBoundsInRoot().bottom.let {
            assertTrue(it < height * HILLS_TOP, "uppmaningen ligger på himlen: $it")
        }
    }

    @Test
    fun `rubriken och uppmaningen håller kontrasten mot himlen och solens dis i värsta läget, 390x844 (NOT-20)`() = headerContrast(390.dp, 844.dp)

    @Test
    fun `rubriken och uppmaningen håller kontrasten mot himlen och solens dis i värsta läget, 360x640 (NOT-20)`() = headerContrast(360.dp, 640.dp)

    @Test
    fun `rubriken och uppmaningen håller kontrasten mot himlen och solens dis i värsta läget, 411x914 (NOT-20)`() = headerContrast(411.dp, 914.dp)

    private fun headerContrast(width: Dp, height: Dp) {
        val colors = AppColors.moment
        run {
            screenIn(width, height)
            val w = width.value
            val h = height.value
            val label = rule.onNodeWithText("Andas in").getBoundsInRoot()
            val sun = sunGeometry(Size(w, h), 1f, textRadius(label.width.value, label.height.value))
            val haze = hazeRadius(sun, 1f)
            listOf("Stunden · Läggdags 21:00" to colors.accent, "Lägg ner det du gör. Andas med solen." to colors.textMuted).forEach { (text, color) ->
                val bounds = rule.onNodeWithText(text).getBoundsInRoot()
                val nearX = sun.center.x.coerceIn(bounds.left.value, bounds.right.value)
                val nearY = sun.center.y.coerceIn(bounds.top.value, bounds.bottom.value)
                val distance = hypot(nearX - sun.center.x, nearY - sun.center.y)
                val alpha = hazeAlpha(1f, distance / haze)
                // Himlen vid textens nedre kant (den ljusaste) med diset ovanpå.
                val background = lerp(skyColorAt(bounds.bottom.value / h, colors.sky), colors.sunMid, alpha)
                assertTrue(contrast(color, background) >= 4.5, "\"$text\" ${width}x$height: ${contrast(color, background)}:1 (dis ${(alpha * 100).toInt()} %)")
            }
        }
    }

    @Test
    fun `nedre innehållet ligger på mörk grund på en vanlig skärm med lång fråga (NOT-20)`() = darkGround(390.dp, 844.dp, 1f)

    @Test
    fun `nedre innehållet ligger på mörk grund vid dubbel textstorlek på en låg skärm (NOT-20)`() = darkGround(360.dp, 480.dp, 2f)

    @Test
    fun `nedre innehållet ligger på mörk grund vid större text på en kort skärm (NOT-20)`() = darkGround(360.dp, 600.dp, 1.3f)

    private fun darkGround(width: Dp, height: Dp, scale: Float) {
        run {
            screenIn(width, height, scale, prompt = prompts.maxBy { it.length } + " " + prompts[1])
            val density = rule.density.density
            val image = rule.onRoot().captureToImage().toPixelMap()
            val top = rule.onNodeWithText("1:40 kvar").getBoundsInRoot().top
            for (offset in listOf(2f, 12f, 40f)) {
                val y = ((top.value + offset) * density).toInt().coerceAtMost(image.height - 1)
                for (x in listOf(2, image.width - 3)) {
                    assertTrue(image[x, y].luminance() < DARK_GROUND, "${width}x$height ×$scale: ($x,$y) ${image[x, y]}")
                }
            }
            // Inget klipps: också Inte nu finns att rulla fram till.
            rule.onNodeWithText("Inte nu").assertExists()
        }
    }

    @Test
    fun `när pausen är klar blir Välj känslor aktiv, och Inte nu stänger alltid (NOT-20)`() {
        val clicks = mutableListOf<String>()
        screen(remaining = 0, onChoose = { clicks += "känslor" }, onNotNow = { clicks += "inte nu" })
        rule.onNodeWithText("Pausen är klar").assertExists()
        chooseFeelings.assertIsEnabled().performClick()
        rule.onNodeWithText("Inte nu").performClick()
        assertEquals(listOf("känslor", "inte nu"), clicks)
    }

    @Test
    fun `Inte nu går att trycka mitt i pausen (NOT-20)`() {
        var closed = 0
        screen(remaining = 60, onNotNow = { closed++ })
        rule.onNodeWithText("Inte nu").assertIsEnabled().performClick()
        assertEquals(1, closed)
    }

    @Test
    fun `utan hjälpfråga visas bara frågan, och vid reducerad rörelse andningen som text (NOT-20, NOT-21)`() {
        screen(remaining = 30, prompt = null, reducedMotion = true)
        prompts.forEach { rule.onNodeWithText(it).assertDoesNotExist() }
        rule.onNodeWithText("Andas in").assertExists()
        rule.mainClock.advanceTimeBy(4_100)
        rule.onNodeWithText("Andas ut").assertExists()
    }

    @Test
    fun `nedräkningen går en sekund i taget och aktiverar Välj känslor när den är slut (NOT-20)`() {
        var done = 0
        val launch = MomentLaunch(Occasion.LUNCH, LocalTime(12, 0), seconds = 15, question = true)
        show { MomentRoute(launch, today, onChooseFeelings = {}, onNotNow = {}, onPauseDone = { done++ }) }
        rule.onNodeWithText("Stunden · Lunch 12:00").assertExists()
        rule.onNodeWithText(prompts[ReflectionPrompts.indexFor(today)]).assertExists()
        rule.onNodeWithText("0:15 kvar").assertExists()
        chooseFeelings.assertIsNotEnabled()

        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithText("0:10 kvar").assertExists()
        assertEquals(0, done)

        rule.mainClock.advanceTimeBy(10_500)
        rule.onNodeWithText("Pausen är klar").assertExists()
        chooseFeelings.assertIsEnabled()
        assertEquals(1, done)
    }

    @Test
    fun `en ny Stunden medan en är öppen börjar om nedräkningen och Välj känslor (NOT-20)`() {
        val first = MomentLaunch(Occasion.LUNCH, LocalTime(12, 0), seconds = 2, question = false)
        var launch by mutableStateOf(first)
        show { MomentRoute(launch, today, onChooseFeelings = {}, onNotNow = {}) }
        rule.mainClock.advanceTimeBy(2_500)
        chooseFeelings.assertIsEnabled()

        launch = MomentLaunch(Occasion.DINNER, LocalTime(17, 0), seconds = 3, question = false)
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("0:03 kvar").assertExists()
        chooseFeelings.assertIsNotEnabled()

        rule.mainClock.advanceTimeBy(3_500)
        chooseFeelings.assertIsEnabled()
    }

    // ── Skärmdumpar (tavla H) ──

    @Test
    fun `Stunden - start`() = rule.captureLightAndDarkPaused("Moment_start") {
        MomentScreen("Läggdags", "21:00", prompts[0], { 120 }, {}, {}, reducedMotion = false)
    }

    @Test
    fun `Stunden - mitt i`() = rule.captureLightAndDarkPaused("Moment_mitt_i") {
        MomentScreen("Läggdags", "21:00", prompts[0], { 100 }, {}, {}, reducedMotion = false)
    }

    @Test
    fun `Stunden - klar`() = rule.captureLightAndDarkPaused("Moment_klar") {
        MomentScreen("Läggdags", "21:00", prompts[0], { 0 }, {}, {}, reducedMotion = false)
    }
}

private const val DARK_GROUND = 0.06f
