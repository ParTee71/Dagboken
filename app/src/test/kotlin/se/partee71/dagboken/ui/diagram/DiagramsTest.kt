package se.partee71.dagboken.ui.diagram

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.performTouchInput
import kotlin.test.assertFalse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.engine.IntervalPoint
import se.partee71.dagboken.core.engine.StackedPoint
import se.partee71.dagboken.core.engine.TrendDirection
import se.partee71.dagboken.testing.pixels
import se.partee71.dagboken.ui.components.AppMenuItem
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Diagrammens beteende och tillgänglighet (etapp 4.3): det gemensamma tomma läget, talbar
 * sammanfattning (NFR-14), min/max-raden (TRD-9), trend (TRD-13), föregående period (TRD-18),
 * staplade segment (TRD-16), sparklinen (HEM-7) och den kompakta rullgardinen (TRD-12).
 */
@RunWith(RobolectricTestRunner::class)
class DiagramsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { DagbokenTheme(darkTheme = false, content = content) }

    private fun described(text: String) = rule.onNodeWithContentDescription(text, substring = true)

    private val energy = listOf(5f, 6f, null, 6f, 7f, null, 8f)

    // ---- Gemensamt tomt läge ----

    @Test
    fun `under två punkter visar varje diagram samma tomma läge med uppmaning`() {
        show {
            LineChart(listOf(ChartSeries("Vilopuls", listOf(null, 58f, null))), label = "Vilopuls", emptyHint = "Koppla klockan för att se vilopulsen.")
        }
        rule.onNodeWithText("För lite data än").assertIsDisplayed()
        rule.onNodeWithText("Koppla klockan för att se vilopulsen.").assertIsDisplayed()
        rule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Vilopuls: för lite data än. Koppla klockan för att se vilopulsen."),
            ),
        ).assertIsDisplayed()
    }

    @Test
    fun `sparklinen och det staplade diagrammet har samma tomma läge`() {
        show { SparklineChart(listOf(null, null, 6f), label = "Energi") }
        rule.onNodeWithText("För lite data än").assertIsDisplayed()
        rule.onNodeWithText("Logga några dagar till så syns kurvan här.").assertIsDisplayed()
    }

    @Test
    fun `det staplade diagrammet med en natt är tomt`() {
        show { StackedBarChart(listOf(StackedPoint(listOf(1f, 1.5f, 4f, 0.5f))), stages(), label = "Sömnstadier") }
        rule.onNodeWithText("För lite data än").assertIsDisplayed()
    }

    // ---- LineChart ----

    @Test
    fun `linjediagrammet läser upp serien och visar lägst, högst, snitt och trend`() {
        show { LineChart(listOf(ChartSeries("Energi", energy)), label = "Energi per dag") }
        described("Energi: 5 värden, lägsta 5, högsta 8, senaste 8, stigande trend").assertIsDisplayed()
        rule.onNodeWithText("Lägst 5 · Högst 8 · Snitt 6,4").assertIsDisplayed()
        rule.onNodeWithText("Trend uppåt").assertIsDisplayed()
    }

    @Test
    fun `en serie ritar trendlinjen solgul, flera serier i seriens färg`() {
        show { LineChart(listOf(ChartSeries("Energi", energy)), label = "Energi") }
        assertTrue(chart("Energi:").pixels(AppColors.light.secondary) > 0, "solgul trendlinje")
    }

    @Test
    fun `flera serier har ingen solgul trend utan teckenförklaring per serie`() {
        val stress = listOf(3f, 4f, 4f, null, 5f, 4f, 3f)
        show { LineChart(listOf(ChartSeries("Energi", energy), ChartSeries("Stress", stress, AppColors.swatch(4))), label = "Mående") }
        assertEquals(0, chart("Energi:").pixels(AppColors.light.secondary))
        rule.onNodeWithText("Stress").assertIsDisplayed()
        rule.onNodeWithText("Trend").assertIsDisplayed()
        described("Stress: 6 värden").assertIsDisplayed()
    }

    @Test
    fun `föregående period läses upp, står i teckenförklaringen och räknas in i min och max`() {
        val previous = listOf(4f, 4f, 5f, 5f, 9f, 5f, 4f)
        show { LineChart(listOf(ChartSeries("Energi", energy)), previous = listOf(ChartSeries("Energi", previous)), label = "Energi") }
        rule.onNodeWithText("Energi (föregående)").assertIsDisplayed()
        described("Energi (föregående): 7 värden, lägsta 4, högsta 9").assertIsDisplayed()
        rule.onNodeWithText("Lägst 4 · Högst 9 · Snitt 6,4").assertIsDisplayed()
    }

    @Test
    fun `kurvorna delas vid luckor och trenderna ritas sist`() {
        val lines = linePlot(
            listOf(ChartSeries("Energi", energy, Color.Red)),
            listOf(ChartSeries("Energi", listOf(1f, 2f, 3f), Color.Gray)),
            trendColor = Color.Yellow,
        )
        assertEquals(listOf(listOf(0, 1, 2), listOf(0, 1), listOf(3, 4), listOf(6), listOf(0, 2), listOf(0, 6)), lines.map { it.xs })
        assertEquals(listOf(false, false, false, false, true, true), lines.map { it.style.dashed })
        assertEquals(Color.Gray, lines[4].style.color, "föregående periods trend i sin färg")
        assertEquals(Color.Yellow, lines[5].style.color)
        assertTrue(lines.none { it.ys.contains(0f) }, "en lucka blir aldrig en nolla")
    }

    // ---- IntervalBarChart ----

    @Test
    fun `intervalldiagrammet visas redan med en dag och läser upp spannet`() {
        show { IntervalBarChart(listOf(null, IntervalPoint(4f, 5.5f, 7f), null), label = "Energi (dag)") }
        described("Energi (dag): 1 dag, lägsta 4, högsta 7, ingen trend").assertIsDisplayed()
        rule.onNodeWithText("Lägst 4 · Högst 7 · Snitt 5,5").assertIsDisplayed()
    }

    @Test
    fun `intervalldiagrammet utan någon dag är tomt`() {
        show { IntervalBarChart(listOf(null, null), label = "Energi (dag)") }
        rule.onNodeWithText("För lite data än").assertIsDisplayed()
    }

    @Test
    fun `intervalldiagrammet färgar dagen ur energiskalan`() {
        show { IntervalBarChart(listOf(IntervalPoint(8f, 9f, 10f), IntervalPoint(8f, 9f, 10f)), label = "Energi (dag)") }
        val chart = chart("Energi (dag):")
        assertTrue(chart.pixels(AppColors.lightExtended.energy.high) > 0, "hög energi är grön")
        assertEquals(0, chart.pixels(AppColors.lightExtended.energy.low))
    }

    // ---- StackedBarChart ----

    @Test
    fun `staplade diagrammet läser upp dominerande segment och visar teckenförklaringen`() {
        val nights = listOf(
            StackedPoint(listOf(1f, 1.5f, 4f, 0.5f)),
            StackedPoint(listOf(null, null, null, null)),
            StackedPoint(listOf(1.2f, null, 4.5f, 0.3f)),
        )
        show { StackedBarChart(nights, stages(), label = "Sömnstadier") }
        described("Sömnstadier: 2 staplar, lägsta 6, högsta 7, mest Lätt").assertIsDisplayed()
        listOf("Djup", "REM", "Lätt", "Vaken", "Trend").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        assertTrue(chart("Sömnstadier:").pixels(AppColors.lightExtended.sleepStages.deep) > 0)
    }

    @Test
    fun `staplar med linje och fält – axeln rymmer linjen, raden gäller staplarna och linjen läses upp (TRD-21)`() {
        val bars = listOf(null, 6f, null, null, 4f).map { StackedPoint(listOf(it)) }
        show {
            StackedBarChart(
                bars,
                listOf(StackSegment("Händelser", AppColors.lightExtended.energy.low)),
                label = "Händelser och sjukdom",
                line = ChartSeries("Incheckningar", listOf(null, null, 8f, 7f, 5f)),
                bands = listOf(ChartBand(2, 4, "Förkylning")),
            )
        }
        described("Händelser och sjukdom: 2 staplar, lägsta 4, högsta 6, mest Händelser").assertIsDisplayed()
        described("Incheckningar: 3 värden, lägsta 5, högsta 8, senaste 5, fallande trend").assertIsDisplayed()
        rule.onNodeWithText("Händelser: Lägst 4 · Högst 6 · Snitt 5").assertIsDisplayed()
        listOf("Händelser", "Incheckningar", "Förkylning", "Trend").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        assertTrue(chart("Händelser och sjukdom:").pixels(AppColors.light.primary) > 0, "linjen ritas i teal")
    }

    @Test
    fun `linjen ensam räcker för att diagrammet ska ritas och får då raden under – utan stapeltrend ingen trendrad`() {
        show {
            StackedBarChart(
                listOf(StackedPoint(listOf(null)), StackedPoint(listOf(null)), StackedPoint(listOf(null))),
                listOf(StackSegment("Händelser", AppColors.lightExtended.energy.low)),
                label = "Händelser och sjukdom",
                line = ChartSeries("Incheckningar", listOf(6f, null, 4f)),
            )
        }
        rule.onNodeWithText("För lite data än").assertDoesNotExist()
        rule.onNodeWithText("Incheckningar: Lägst 4 · Högst 6 · Snitt 5").assertIsDisplayed()
        rule.onNodeWithText("Trend nedåt").assertIsDisplayed()
        rule.onNodeWithText("Trend").assertDoesNotExist()
        rule.onNodeWithText("Incheckningar").assertIsDisplayed()
    }

    // ---- SparklineChart ----

    @Test
    fun `sparklinen visar lägst, högst och idag och länkar till Trender`() {
        var opened = 0
        show { SparklineChart(listOf(6f, 7f, null, 5f, 8f), label = "Energi", onOpenTrends = { opened++ }) }
        rule.onNodeWithText("Lägst 5 · Högst 8 · Idag 8").assertIsDisplayed()
        rule.onNodeWithText("Visa i Trender").assertHasClickAction().performClick()
        assertEquals(1, opened)
        assertTrue(chart("Energi:").pixels(AppColors.light.secondary) > 0, "dagens punkt och trenden är solgula")
    }

    @Test
    fun `sparklinen utan värde idag skriver ett streck`() {
        show { SparklineChart(listOf(6f, 7f, 5f, null), label = "Energi") }
        rule.onNodeWithText("Lägst 5 · Högst 7 · Idag —").assertIsDisplayed()
    }

    // ---- MinMaxCaption ----

    @Test
    fun `min och max visas alltid, snitt och trend bara när de anges`() {
        show { MinMaxCaption(3f, 8f) }
        rule.onNodeWithText("Lägst 3 · Högst 8").assertIsDisplayed()
        rule.onNodeWithText("Trend", substring = true).assertDoesNotExist()
    }

    @Test
    fun `trendpillen säger riktningen i ord`() {
        show { MinMaxCaption(3f, 8.5f, average = 6.2f, trend = TrendDirection.FLAT) }
        rule.onNodeWithText("Lägst 3 · Högst 8,5 · Snitt 6,2").assertIsDisplayed()
        rule.onNodeWithText("Trend oförändrad").assertIsDisplayed()
    }

    // ---- CompactDropdownButton ----

    @Test
    fun `rullgardinen är en 48 dp-rullgardin som öppnar menyn och väljer`() {
        var chosen = ""
        show {
            CompactDropdownButton(
                "14 dagar",
                listOf("7 dagar", "14 dagar", "Allt").map { AppMenuItem(it, { chosen = it }) },
            )
        }
        rule.onNodeWithText("14 dagar")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .assertTouchHeightIsEqualTo(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()
        rule.onNodeWithText("Allt").assertIsDisplayed().performClick()
        assertEquals("Allt", chosen)
    }

    // ---- Decimalkomma, tema och zoom ----

    @Test
    fun `decimaler skrivs med svenskt komma även i skärmläsarens sammanfattning`() {
        show { LineChart(listOf(ChartSeries("Energi", listOf(5.5f, 6.25f, 7.4f))), label = "Energi") }
        described("Energi: 3 värden, lägsta 5,5, högsta 7,4, senaste 7,4").assertIsDisplayed()
    }

    @Test
    fun `temabyte utan omstart ritar kurvan i det nya temats färg`() {
        var dark by mutableStateOf(false)
        rule.setContent { DagbokenTheme(darkTheme = dark) { LineChart(listOf(ChartSeries("Energi", energy)), label = "Energi") } }
        assertTrue(chart("Energi:").pixels(AppColors.light.primary) > 0)
        dark = true
        rule.waitForIdle()
        assertTrue(chart("Energi:").pixels(AppColors.dark.primary) > 0, "kurvan i mörkt temas teal")
        assertEquals(0, chart("Energi:").pixels(AppColors.light.primary), "inget kvar i ljust temas teal")
    }

    @Test
    fun `stapeldiagrammet är utzoomat igen när data byts, även utan x-etiketter`() {
        val first = listOf(IntervalPoint(2f, 3f, 4f), IntervalPoint(4f, 5f, 6f), IntervalPoint(6f, 7f, 8f))
        val second = listOf(IntervalPoint(3f, 4f, 5f), IntervalPoint(4f, 5f, 6f), IntervalPoint(6f, 7f, 8f))
        var points by mutableStateOf(first)
        show { IntervalBarChart(points, label = "Energi (dag)") }
        val start = chart("Energi (dag):").image()
        chart("Energi (dag):").performTouchInput { pinch(center - Offset(20f, 0f), centerLeft, center + Offset(20f, 0f), centerRight) }
        assertFalse(chart("Energi (dag):").image().contentEquals(start), "nypningen zoomade in")
        points = second
        rule.waitForIdle()
        points = first
        rule.waitForIdle()
        assertTrue(chart("Energi (dag):").image().contentEquals(start), "nya data → helt utzoomat (TRD-10)")
    }

    @Test
    fun `linjediagrammet är utzoomat igen när data byts, även utan x-etiketter`() {
        val second = energy.map { it?.plus(1f) }
        var points by mutableStateOf(energy)
        show { LineChart(listOf(ChartSeries("Energi", points)), label = "Energi") }
        val start = chart("Energi:").image()
        chart("Energi:").performTouchInput { pinch(center - Offset(20f, 0f), centerLeft, center + Offset(20f, 0f), centerRight) }
        assertFalse(chart("Energi:").image().contentEquals(start), "nypningen zoomade in")
        points = second
        rule.waitForIdle()
        points = energy
        rule.waitForIdle()
        assertTrue(chart("Energi:").image().contentEquals(start), "nya data → helt utzoomat (TRD-10)")
    }

    // ---- 3.x StackedBarChartRenderTest (androidTest) – samma fall i JVM ----

    @Test
    fun `staplade diagrammet utan datum ritas och läses upp`() {
        show { StackedBarChart(listOf(StackedPoint(listOf(2f, 1f, 4f, 1f)), StackedPoint(listOf(1.5f, 1.5f, 4f, 0.5f))), stages(), label = "Sömnstadier") }
        described("Sömnstadier: 2 staplar, lägsta 7,5, högsta 8, mest Lätt, fallande trend").assertIsDisplayed()
        assertTrue(chart("Sömnstadier:").pixels(AppColors.lightExtended.sleepStages.rem) > 0)
    }

    @Test
    fun `en natt utan något stadium är en lucka, inte en nollhög stapel`() {
        val night = StackedPoint(listOf(1f, 1f, 4f, 0.5f))
        show { StackedBarChart(listOf(night, StackedPoint(listOf(null, null, null, null)), night), stages(), label = "Sömnstadier") }
        described("Sömnstadier: 2 staplar, lägsta 6,5, högsta 6,5, mest Lätt, oförändrad trend").assertIsDisplayed()
    }

    // ---- Hjälpare ----

    @Composable
    private fun stages() = listOf(
        StackSegment("Djup", AppColors.extended.sleepStages.deep),
        StackSegment("REM", AppColors.extended.sleepStages.rem),
        StackSegment("Lätt", AppColors.extended.sleepStages.light),
        StackSegment("Vaken", AppColors.extended.sleepStages.awake),
    )

    private fun chart(descriptionStart: String) = described(descriptionStart)

    private fun SemanticsNodeInteraction.image(): IntArray {
        val map = captureToImage().toPixelMap()
        return IntArray(map.width * map.height) { map[it % map.width, it / map.width].toArgb() }
    }
}
