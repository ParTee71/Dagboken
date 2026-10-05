package se.partee71.dagboken.ui.common

import kotlin.test.assertEquals
import org.junit.Test
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.Tone

/** Skalans nivå – en indelning för båda riktningarna (AKT-4, AKT-5, AKT-6, SCR-1). */
class ScaleLevelTest {

    private fun zones(range: IntRange, higherIsBetter: Boolean = true) = range.map { scaleLevel(it, range, higherIsBetter).zone }

    @Test
    fun `0–10 delas som i 3x - 0–3 låg, 4–6 medel, 7–10 hög`() {
        val expected = List(4) { ScaleZone.Low } + List(3) { ScaleZone.Mid } + List(4) { ScaleZone.High }
        assertEquals(expected, zones(0..10))
    }

    @Test
    fun `aktivitetens energi −10…+10 delas vid −2 och +4`() {
        val expected = List(8) { ScaleZone.Low } + List(6) { ScaleZone.Mid } + List(7) { ScaleZone.High }
        assertEquals(expected, zones(-10..10))
    }

    @Test
    fun `högre är bättre - Låg i terrakotta, Medel i solgult, Hög i grönt`() {
        assertEquals(R.string.level_low to Tone.Warning, scaleLevel(2).let { it.label to it.tone })
        assertEquals(R.string.level_mid to Tone.Sun, scaleLevel(5).let { it.label to it.tone })
        assertEquals(R.string.level_high to Tone.Positive, scaleLevel(9).let { it.label to it.tone })
    }

    @Test
    fun `högre är sämre - Lätt i grönt, Måttlig i solgult, Svår i terrakotta`() {
        assertEquals(R.string.severity_mild to Tone.Positive, scaleLevel(1, higherIsBetter = false).let { it.label to it.tone })
        assertEquals(R.string.severity_moderate to Tone.Sun, scaleLevel(4, higherIsBetter = false).let { it.label to it.tone })
        assertEquals(R.string.severity_severe to Tone.Warning, scaleLevel(10, higherIsBetter = false).let { it.label to it.tone })
    }

    @Test
    fun `värden utanför skalan räknas som kanten`() {
        assertEquals(ScaleZone.High, scaleLevel(14).zone)
        assertEquals(ScaleZone.Low, scaleLevel(-3).zone)
    }

    @Test
    fun `värdet med tecken bara när skalan går under noll`() {
        assertEquals("7", scaleValueText(7))
        assertEquals("+3", scaleValueText(3, -10..10))
        assertEquals("−2", scaleValueText(-2, -10..10))
        assertEquals("0", scaleValueText(0, -10..10))
    }
}
