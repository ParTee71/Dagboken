package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.engine.FeelingEntry
import se.partee71.dagboken.core.engine.FeelingTally
import se.partee71.dagboken.core.engine.computeFeelingStats
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.diagram.FeelingWordBars
import se.partee71.dagboken.ui.diagram.FittingWords
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Minihjulet och ordträdet (#320): beskrivningen för TalkBack, fyllnaden efter vikten, ordningen och orden som får plats
 * – och minihjulets skärmdumpar (tom, en kärna, blandad, med ord). Ordträdets skärmdumpar står i `DiagramScreenshotTest`.
 */
@RunWith(RobolectricTestRunner::class)
class FeelingVisualsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { DagbokenTheme(content = content) }

    private fun tally(vararg tokens: String): FeelingTally = computeFeelingStats(listOf(FeelingEntry(GalleryDay, tokens.mapNotNull(Feeling::parse)))).tally

    @Test
    fun `minihjulet är en bild som läser meningen, orden i sektorerna läses inte`() {
        show { FeelingMiniWheel(GalleryFeelings.period.tally, "Du bor i Lugn och Glad.", size = FeelingWheelSize.HEATMAP, labels = true) }
        rule.onNodeWithContentDescription("Du bor i Lugn och Glad.").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image))
        assertEquals(0, rule.onAllNodesWithText("Tacksam").fetchSemanticsNodes().size)
    }

    @Test
    fun `fyllnaden - utan val behållarfärgen, annars mot innehållsfärgen efter vikten`() {
        val tone = AppColors.lightExtended.feelingTones.calm
        val counts = tally("calm", "calm", "glad").cores
        assertEquals(tone.container, fillOf(counts.first { it.core == "strong" }, tone))
        assertEquals(lerp(tone.container, tone.content, 0.9f), fillOf(counts.first { it.core == "calm" }, tone))
        assertEquals(lerp(tone.container, tone.content, 0.6f), fillOf(counts.first { it.core == "glad" }, tone), "halva vikten")
    }

    @Test
    fun `ordträdet - rad för rad, vanligast först, utan val sist`() {
        show { FeelingWordBars(GalleryFeelings.period.tally) }
        rule.onNodeWithContentDescription("Lugn: tacksam, avslappnad, närvarande").assertExists()
        rule.onNodeWithContentDescription("Glad: kreativ, nöjd, hoppfull").assertExists()
        rule.onNodeWithContentDescription("Rädd: orolig, osäker").assertExists()
        rule.onNodeWithContentDescription("Stark, inte vald").assertExists()
        val order = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.ContentDescription].single().substringBefore(':').substringBefore(',') }
        assertEquals(listOf("Lugn", "Glad", "Rädd", "Ledsen", "Arg", "Stark"), order)
    }

    @Test
    fun `ordträdet - en kärna utan nyans läses med bara namnet`() {
        show { FeelingWordBars(tally("calm", "calm.grateful", "sad")) }
        rule.onNodeWithContentDescription("Ledsen").assertExists()
        rule.onNodeWithContentDescription("Lugn: tacksam").assertExists()
    }

    @Test
    fun `ordträdet - orden som inte får plats hela ritas inte`() {
        show {
            Box(Modifier.width(120.dp)) {
                FittingWords {
                    Text("tacksam", style = AppTypography.itemSubtitle)
                    Text("avslappnad", style = AppTypography.itemSubtitle)
                    Text("omtänksam", style = AppTypography.itemSubtitle)
                }
            }
        }
        rule.onNodeWithText("tacksam").assertIsDisplayed()
        rule.onNodeWithText("omtänksam").assertIsNotDisplayed()
    }

    @Composable
    private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) { AppCard(content = content) }
    }

    @Test
    fun `FeelingMiniWheel - tom, en kärna och blandad`() = captureLightAndDark("FeelingMiniWheel_tom_en_blandad") {
        Sheet {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                FeelingMiniWheel(FeelingTally.EMPTY, "Inga känslor loggade.")
                FeelingMiniWheel(tally("calm.grateful", "calm"), "Lugn.")
                FeelingMiniWheel(GalleryFeelings.day.tally, "Mest glad och lugn. Lite rädd vid lunch.")
            }
        }
    }

    @Test
    fun `FeelingMiniWheel - veckan med vald dag`() = captureLightAndDark("FeelingMiniWheel_vecka") {
        Sheet {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GalleryFeelings.week.forEachIndexed { index, day ->
                    FeelingMiniWheel(day.tally, day.date.toString(), size = FeelingWheelSize.DAY, selected = index == GalleryFeelings.week.lastIndex)
                }
            }
        }
    }

    @Test
    fun `FeelingMiniWheel - värmekarta med ord`() = captureLightAndDark("FeelingMiniWheel_med_ord") {
        Sheet { FeelingMiniWheel(GalleryFeelings.period.tally, "Värmekarta", size = FeelingWheelSize.HEATMAP, labels = true) }
    }

    private companion object {
        val GalleryDay = LocalDate(2026, 10, 14)
    }
}
