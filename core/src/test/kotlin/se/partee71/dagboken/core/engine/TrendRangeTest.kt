package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.junit.Test
import se.partee71.dagboken.core.time.datesBetween

/** Periodvalet i Trenders kort (TRD-3): fasta perioder, "Allt" ur posterna och datum över sommartiden. */
class TrendRangeTest {

    private val today = LocalDate(2026, 10, 6)

    @Test
    fun `en fast period är lika många dagar och slutar idag`() {
        val week = TrendRange.SEVEN_DAYS.days(today)
        assertEquals(7, week.size)
        assertEquals(LocalDate(2026, 9, 30), week.first())
        assertEquals(today, week.last())
        assertEquals(LocalDate(2026, 9, 30), TrendRange.SEVEN_DAYS.from(today))
        assertEquals(30, TrendRange.MONTH.days(today).size)
        assertEquals(90, TrendRange.THREE_MONTHS.days(today).size)
        assertEquals(listOf(7, 14, 30, 90, null), TrendRange.entries.map { it.days })
        assertEquals(TrendRange.MONTH, TrendRange.DEFAULT)
    }

    @Test
    fun `dagarna är sammanhängande över sommartidsomställningen`() {
        // Sommartiden slutar 25 oktober 2026 – räknat i datum är det ändå en dag per dag.
        val days = TrendRange.FOURTEEN_DAYS.days(LocalDate(2026, 10, 31))
        assertEquals(14, days.size)
        assertEquals(LocalDate(2026, 10, 18), days.first())
        days.zipWithNext().forEach { (a, b) -> assertEquals(1, a.daysUntil(b), "$a → $b") }
        assertTrue(LocalDate(2026, 10, 25) in days)
        // Och in i sommartiden (29 mars 2026).
        val spring = TrendRange.SEVEN_DAYS.days(LocalDate(2026, 4, 1))
        assertEquals(listOf(26, 27, 28, 29, 30, 31, 1), spring.map { it.day })
    }

    @Test
    fun `Allt går från första posten till idag och är tomt utan poster`() {
        assertEquals(datesBetween(LocalDate(2026, 9, 1), today), TrendRange.ALL.days(today, LocalDate(2026, 9, 1)))
        assertEquals(emptyList(), TrendRange.ALL.days(today, null))
        assertEquals(emptyList(), TrendRange.ALL.days(today))
        assertEquals(listOf(today), TrendRange.ALL.days(today, LocalDate(2026, 10, 9)), "en post i framtiden ger bara idag")
        assertEquals(TrendRange.ALL_FROM, TrendRange.ALL.from(today))
        assertNull(TrendRange.ALL.days)
    }

    @Test
    fun `första posten räknas bland dagarna, poster utan dag inte`() {
        assertEquals(LocalDate(2026, 3, 1), earliestDate(listOf(LocalDate(2026, 9, 1), null, LocalDate(2026, 3, 1))))
        assertNull(earliestDate(listOf(null, null)))
        assertNull(earliestDate(emptyList()))
    }

    @Test
    fun `datesBetween är tom baklänges och en dag för samma dag`() {
        assertEquals(listOf(today), datesBetween(today, today))
        assertEquals(emptyList(), datesBetween(today, LocalDate(2026, 10, 5)))
        assertEquals(3, datesBetween(LocalDate(2026, 10, 4), today).size)
    }
}
