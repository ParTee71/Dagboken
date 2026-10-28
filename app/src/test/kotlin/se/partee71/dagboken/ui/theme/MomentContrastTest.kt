package se.partee71.dagboken.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.assertTrue
import org.junit.Test

/** Stundens kvällston (NOT-20): texten klarar 4,5:1 mot kullarna, himlens övre del och solen (WCAG 1.4.3). */
class MomentContrastTest {

    private val moment = AppColors.moment

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun assertReadable(name: String, text: Color, background: Color) = assertTrue(contrast(text, background) >= 4.5, "$name: ${"%.2f".format(contrast(text, background))}:1")

    @Test
    fun `nedre texterna och knapparnas text ligger på kullarna`() {
        listOf("bakre kullen" to moment.hillBack, "främre kullen" to moment.hillFront).forEach { (hill, color) ->
            assertReadable("text på $hill", moment.text, color)
            assertReadable("dämpad text på $hill", moment.textMuted, color)
            assertReadable("nedräkningen på $hill", moment.accent, color)
            // Knapparna (mörkt tema): Inte nu i primärfärgen, Välj känslor med onPrimary på primary.
            assertReadable("Inte nu på $hill", AppColors.dark.primary, color)
        }
        assertReadable("Välj känslor", AppColors.dark.onPrimary, AppColors.dark.primary)
    }

    @Test
    fun `rubriken och uppmaningen ligger på himlens övre del`() {
        // Överst: de två första stoppen; rubrik och uppmaning står inom de översta ~15 procenten.
        moment.sky.take(2).forEach {
            assertReadable("rubriken på himlen", moment.accent, it)
            assertReadable("uppmaningen på himlen", moment.textMuted, it)
        }
    }

    @Test
    fun `fasen i solen klarar kontrasten mot hela solen`() {
        listOf(moment.sunCore, moment.sunMid, moment.sunEdge).forEach { assertReadable("fasen i solen", moment.onSun, it) }
    }
}
