package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.engine.IntervalPoint
import se.partee71.dagboken.core.engine.StackedPoint
import se.partee71.dagboken.core.engine.TrendDirection
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.testing.clickWithoutRipple
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppMenuItem
import se.partee71.dagboken.ui.components.GalleryCharts
import se.partee71.dagboken.ui.components.galleryStages
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Skärmdumpar (ljust + mörkt) av diagrammen i mockupens tillstånd (canvasen "Komponenter", avsnitt 7):
 * tomt, ett värde, luckor, trendlinje, föregående period och stor text. Påhittad data ur galleriet.
 * Vico-modellen byggs synkront, så bilderna behöver ingen pausad klocka.
 */
@RunWith(RobolectricTestRunner::class)
class DiagramScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) { AppCard(content = content) }
    }

    @Test
    fun `LineChart - luckor och trendlinje`() = captureLightAndDark("LineChart_luckor_trend") {
        Sheet { LineChart(listOf(ChartSeries("Energi", GalleryCharts.energy)), xLabels = GalleryCharts.days14, label = "Energi per dag") }
    }

    @Test
    fun `LineChart - föregående period`() = captureLightAndDark("LineChart_foregaende_period") {
        Sheet {
            LineChart(
                listOf(ChartSeries("Energi", GalleryCharts.energy)),
                xLabels = GalleryCharts.days14,
                previous = listOf(ChartSeries("Energi", GalleryCharts.energyPrevious)),
                label = "Energi per dag",
            )
        }
    }

    @Test
    fun `LineChart - flera serier`() = captureLightAndDark("LineChart_flera_serier") {
        Sheet {
            LineChart(
                listOf(
                    ChartSeries("Energi", GalleryCharts.energy),
                    ChartSeries("Stress", listOf(4f, 4f, 5f, 3f, null, 4f, 5f, 6f, null, 4f, 3f, 4f, 3f, 3f), AppColors.swatch(4)),
                ),
                xLabels = GalleryCharts.days14,
                label = "Mående",
            )
        }
    }

    @Test
    fun `LineChart - ett värde och tomt`() {
        captureLightAndDark("LineChart_ett_varde") { Sheet { LineChart(listOf(ChartSeries("Vilopuls", listOf(null, 58f, null))), label = "Vilopuls") } }
        captureLightAndDark("LineChart_tomt") { Sheet { LineChart(listOf(ChartSeries("Vilopuls", emptyList())), label = "Vilopuls") } }
    }

    @Test
    fun `LineChart - stor text`() = captureLightAndDark("LineChart_stor_text") {
        LargeText { Sheet { LineChart(listOf(ChartSeries("Energi", GalleryCharts.energy)), xLabels = GalleryCharts.days14, label = "Energi") } }
    }

    @Test
    fun `IntervalBarChart - vecka med lucka, ett värde och tomt`() {
        captureLightAndDark("IntervalBarChart_vecka") { Sheet { IntervalBarChart(GalleryCharts.energySpans, xLabels = GalleryCharts.days7, label = "Energi (dag)") } }
        captureLightAndDark("IntervalBarChart_ett_varde") {
            Sheet { IntervalBarChart(listOf(null, null, IntervalPoint(3f, 4.5f, 6f), null), xLabels = GalleryCharts.days7.take(4), label = "Energi (dag)") }
        }
        captureLightAndDark("IntervalBarChart_tomt") { Sheet { IntervalBarChart(listOf(null, null), label = "Energi (dag)") } }
    }

    @Test
    fun `IntervalBarChart - stor text`() = captureLightAndDark("IntervalBarChart_stor_text") {
        LargeText { Sheet { IntervalBarChart(GalleryCharts.energySpans, xLabels = GalleryCharts.days7, label = "Energi (dag)") } }
    }

    @Test
    fun `StackedBarChart - sömnstadier med luckor och tomt`() {
        captureLightAndDark("StackedBarChart_somn") { Sheet { StackedBarChart(GalleryCharts.sleep, galleryStages(), xLabels = GalleryCharts.days7, label = "Sömnstadier") } }
        captureLightAndDark("StackedBarChart_tomt") {
            Sheet { StackedBarChart(listOf(StackedPoint(listOf(1f, 1.5f, 4f, 0.5f))), galleryStages(), label = "Sömnstadier") }
        }
    }

    @Test
    fun `StackedBarChart - staplar, linje och fält`() = captureLightAndDark("StackedBarChart_handelser_sjukdom") {
        Sheet {
            StackedBarChart(
                GalleryCharts.eventBars,
                listOf(StackSegment("Händelser", AppColors.tone(Tone.Warning).content)),
                xLabels = GalleryCharts.days14,
                label = "Händelser och sjukdom",
                line = ChartSeries("Incheckningar", GalleryCharts.checkins),
                bands = GalleryCharts.episodes,
            )
        }
    }

    @Test
    fun `StackedBarChart - stor text`() = captureLightAndDark("StackedBarChart_stor_text") {
        LargeText { Sheet { StackedBarChart(GalleryCharts.sleep, galleryStages(), xLabels = GalleryCharts.days7, label = "Sömnstadier") } }
    }

    @Test
    fun `SparklineChart - vecka, idag saknas och tomt`() {
        captureLightAndDark("SparklineChart_vecka") {
            Sheet { SparklineChart(GalleryCharts.week, xLabels = GalleryCharts.weekdays, label = "Energi", onOpenTrends = {}) }
        }
        captureLightAndDark("SparklineChart_idag_saknas") {
            Sheet { SparklineChart(GalleryCharts.week.dropLast(1) + null, xLabels = GalleryCharts.weekdays, label = "Energi") }
        }
        captureLightAndDark("SparklineChart_tomt") { Sheet { SparklineChart(listOf(null, null, null, 6f), label = "Energi") } }
    }

    @Test
    fun `SparklineChart - stor text`() = captureLightAndDark("SparklineChart_stor_text") {
        LargeText { Sheet { SparklineChart(GalleryCharts.week, xLabels = GalleryCharts.weekdays, label = "Energi", onOpenTrends = {}) } }
    }

    @Test
    fun `MinMaxCaption - lägen`() = captureLightAndDark("MinMaxCaption_lagen") {
        Sheet {
            MinMaxCaption(3f, 8f)
            MinMaxCaption(3f, 8f, average = 6.2f, trend = TrendDirection.RISING)
            MinMaxCaption(55f, 64f, trend = TrendDirection.FALLING, today = null, showToday = true)
            MinMaxCaption(5f, 8f, trend = TrendDirection.FLAT, today = 8f)
        }
    }

    @Test
    fun `CompactDropdownButton - stängd`() = captureLightAndDark("CompactDropdownButton_stangd") {
        Sheet { CompactDropdownButton("14 dagar", periodItems()) }
    }

    @Test
    fun `CompactDropdownButton - öppen`() = rule.captureScreenLightAndDark(
        "CompactDropdownButton_oppen",
        open = { onNodeWithText("14 dagar").clickWithoutRipple() },
    ) {
        Sheet { CompactDropdownButton("14 dagar", periodItems()) }
    }

    private fun periodItems() = listOf("7 dagar", "14 dagar", "Månad", "3 månader", "Allt").map {
        AppMenuItem(it, {}, icon = if (it == "14 dagar") R.drawable.ic_check else null)
    }

    @Composable
    private fun LargeText(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = FONT_SCALE), content = content)
    }

    private companion object {
        const val FONT_SCALE = 1.3f
    }
}
