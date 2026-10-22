package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import org.junit.Test

/** Skalans zoner vid 40 % och 70 % (AKT-4, AKT-5, AKT-6, SCR-1). */
class ScaleZoneTest {

    /** De första värdena i medel- och högzonen på [range]. */
    private fun boundaries(range: IntRange): Pair<Int, Int> = range.first { scaleZone(it, range) != ScaleZone.Low } to range.first { scaleZone(it, range) == ScaleZone.High }

    @Test
    fun `nollskalan har gränserna 4 och 7 som i 3x`() {
        assertEquals(4 to 7, boundaries(0..10))
        assertEquals(ScaleZone.Low, scaleZone(0))
        assertEquals(ScaleZone.High, scaleZone(10))
    }

    @Test
    fun `aktivitetens energi har gränserna −2 och +4`() {
        assertEquals(-2 to 4, boundaries(-10..10))
        assertEquals(ScaleZone.Low, scaleZone(-10, -10..10))
        assertEquals(ScaleZone.High, scaleZone(10, -10..10))
    }

    @Test
    fun `gränsen exakt på 40 och 70 procent hör till den högre zonen`() {
        assertEquals(ScaleZone.Low, scaleZone(39, 0..100))
        assertEquals(ScaleZone.Mid, scaleZone(40, 0..100))
        assertEquals(ScaleZone.Mid, scaleZone(69, 0..100))
        assertEquals(ScaleZone.High, scaleZone(70, 0..100))
    }

    @Test
    fun `värden utanför skalan räknas som kanten`() {
        assertEquals(ScaleZone.High, scaleZone(14))
        assertEquals(ScaleZone.Low, scaleZone(-3))
    }

    @Test
    fun `en skala med ett enda värde ger ingen division med noll`() {
        assertEquals(ScaleZone.Low, scaleZone(5, 5..5))
    }

    @Test
    fun `spegling byter låg och hög och behåller medel`() {
        assertEquals(listOf(ScaleZone.High, ScaleZone.Mid, ScaleZone.Low), ScaleZone.entries.map { it.mirrored() })
    }
}
