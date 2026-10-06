package se.partee71.dagboken.ui.common

import androidx.compose.ui.graphics.Color
import kotlin.test.assertEquals
import org.junit.Test

/** Jämförs färger (TRD-17): egen färg först, aldrig två lika – vid krock nästa lediga ur paletten. */
class SeriesColorsTest {

    private val palette = listOf(Color.Red, Color.Green, Color.Blue)
    private val teal = Color.Cyan

    private fun resolve(preferred: List<Color?>) = distinctSeriesColors(preferred, teal, { palette[it] }, palette.size)

    @Test
    fun `varje serie behåller sin egen färg när de inte krockar`() {
        assertEquals(listOf(Color.Green, teal, Color.Red), resolve(listOf(Color.Green, null, Color.Red)))
    }

    @Test
    fun `två serier utan egen färg – den andra tar nästa lediga ur paletten`() {
        assertEquals(listOf(teal, Color.Red), resolve(listOf(null, null)))
        assertEquals(listOf(Color.Red, Color.Green, Color.Blue), resolve(listOf(Color.Red, Color.Red, Color.Red)))
    }

    @Test
    fun `den lediga färgen hoppar över färger som redan är tagna`() {
        assertEquals(listOf(Color.Red, Color.Green, Color.Blue), resolve(listOf(Color.Red, Color.Green, Color.Red)))
    }

    @Test
    fun `är paletten slut behålls den egna färgen hellre än ingen alls`() {
        assertEquals(listOf(Color.Red, Color.Green, Color.Blue, teal, Color.Red), resolve(listOf(Color.Red, Color.Red, Color.Red, Color.Red, Color.Red)))
    }

    @Test
    fun `tom lista ger tom lista`() {
        assertEquals(emptyList(), resolve(emptyList()))
    }
}
