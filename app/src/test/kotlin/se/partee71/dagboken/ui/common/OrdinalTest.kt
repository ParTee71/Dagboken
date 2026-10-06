package se.partee71.dagboken.ui.common

import kotlin.test.assertEquals
import org.junit.Test

/** Svenska ordningstal i "var 2:a dag" / "var 3:e dag". */
class OrdinalTest {
    @Test
    fun `a efter 1 och 2 utom 11 och 12, annars e`() {
        val withA = (1..102).filter(::ordinalTakesA)
        assertEquals(listOf(1, 2, 21, 22, 31, 32, 41, 42, 51, 52, 61, 62, 71, 72, 81, 82, 91, 92, 101, 102), withA)
    }
}
