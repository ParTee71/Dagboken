package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test

/** Stundens hjälpfrågor (NOT-21): 30 index, dagens val = dag i året modulo 30, samma hela dagen. */
class ReflectionPromptsTest {

    @Test fun `trettio index i ordning`() {
        assertEquals(30, ReflectionPrompts.COUNT)
        assertEquals((0..29).toList(), ReflectionPrompts.indices.toList())
    }

    @Test fun `dagens fråga är dag i året modulo 30`() {
        assertEquals(1, ReflectionPrompts.indexFor(LocalDate(2026, 1, 1)))
        assertEquals(0, ReflectionPrompts.indexFor(LocalDate(2026, 1, 30)))
        assertEquals(1, ReflectionPrompts.indexFor(LocalDate(2026, 1, 31)))
        // 2026-10-20 är dag 293: 293 mod 30 = 23.
        assertEquals(23, ReflectionPrompts.indexFor(LocalDate(2026, 10, 20)))
        // Skottår: 31 december är dag 366.
        assertEquals(6, ReflectionPrompts.indexFor(LocalDate(2028, 12, 31)))
    }

    @Test fun `deterministiskt, alltid ett giltigt index och alla frågor används under ett år`() {
        val year = generateSequence(LocalDate(2027, 1, 1)) { it.plus(DatePeriod(days = 1)) }.takeWhile { it.year == 2027 }.toList()
        for (date in year) {
            val index = ReflectionPrompts.indexFor(date)
            assertEquals(index, ReflectionPrompts.indexFor(LocalDate.parse(date.toString())), "samma dag ger samma fråga")
            assertTrue(index in ReflectionPrompts.indices, "$date: $index")
        }
        assertEquals(ReflectionPrompts.indices.toSet(), year.map(ReflectionPrompts::indexFor).toSet())
        // Trettio dagar i rad ger trettio olika frågor (inom samma år).
        assertEquals(30, year.drop(40).take(30).map(ReflectionPrompts::indexFor).toSet().size)
    }
}
