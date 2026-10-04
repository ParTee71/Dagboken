package se.partee71.dagboken.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalTime
import org.junit.Test

/** Härledningen av måltidstillfället för screeningar från 3.x (DAT-12). */
class OccasionTest {

    @Test
    fun `3x-namnet avgör först, oavsett klockslag`() {
        assertEquals(Occasion.BREAKFAST, Occasion.derive("Efter frukost", LocalTime(22, 0)))
        assertEquals(Occasion.LUNCH, Occasion.derive("Lunch", null))
        assertEquals(Occasion.DINNER, Occasion.derive("Kvällsmat", LocalTime(7, 0)))
        assertEquals(Occasion.BEDTIME, Occasion.derive("Läggdags", LocalTime(12, 0)))
    }

    @Test
    fun `annat namn - närmaste påminnelsetid räknat runt dygnet`() {
        assertEquals(Occasion.BREAKFAST, Occasion.derive("Screening", LocalTime(9, 59)))
        assertEquals(Occasion.LUNCH, Occasion.derive("", LocalTime(10, 1)))
        assertEquals(Occasion.BREAKFAST, Occasion.derive("", LocalTime(10, 0)), "lika nära → det tidigare")
        assertEquals(Occasion.DINNER, Occasion.derive("", LocalTime(18, 59)))
        assertEquals(Occasion.BEDTIME, Occasion.derive("", LocalTime(2, 29)), "efter midnatt är närmare läggdags än frukost")
        assertEquals(Occasion.BREAKFAST, Occasion.derive("", LocalTime(2, 31)))
    }

    @Test
    fun `användarens påminnelsetider gäller före standardtiderna`() {
        val times = mapOf(Occasion.BREAKFAST to LocalTime(6, 0), Occasion.LUNCH to LocalTime(11, 0))
        assertEquals(Occasion.LUNCH, Occasion.derive("", LocalTime(9, 0), times))
    }

    @Test
    fun `utan namn och klockslag finns inget tillfälle`() {
        assertNull(Occasion.derive("Screening", null))
    }

    @Test
    fun `3x-namnen och de lagrade namnen är unika`() {
        assertEquals(listOf("Efter frukost", "Lunch", "Kvällsmat", "Läggdags"), Occasion.entries.map { it.legacyName })
        assertEquals(Occasion.entries.size, Occasion.entries.map { it.wire }.toSet().size)
    }
}
