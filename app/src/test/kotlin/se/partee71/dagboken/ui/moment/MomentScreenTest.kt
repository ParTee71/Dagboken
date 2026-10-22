package se.partee71.dagboken.ui.moment

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
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
        rule.onNodeWithText("Lägg ner det du gör. Andas med cirkeln.").assertExists()
        rule.onNodeWithText("Vad känner du just nu?").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithText(prompts[0]).assertExists()
        rule.onNodeWithText("Ingen text behövs. Känn efter en stund – orden väljer du sedan.").assertExists()
        rule.onNodeWithText("1:40 kvar").assertExists()
        rule.onNodeWithContentDescription("1 min 40 s kvar").assertExists()
        chooseFeelings.assertIsNotEnabled()
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
