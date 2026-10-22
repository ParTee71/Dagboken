package se.partee71.dagboken.core.time

import kotlin.test.assertEquals
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Test

/** Veckan, tidsenheterna och minutavrundningen – en gång i `core/time` (regel 4). */
class DatesTest {

    @Test
    fun `måndagen i veckan är datumet självt på en måndag och annars den föregående`() {
        val monday = LocalDate(2026, 10, 12)
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertEquals(monday, monday.weekMonday())
        assertEquals(monday, LocalDate(2026, 10, 14).weekMonday())
        assertEquals(monday, LocalDate(2026, 10, 18).weekMonday(), "söndagen hör till veckan som började på måndagen")
        assertEquals(LocalDate(2026, 10, 19), LocalDate(2026, 10, 19).weekMonday())
    }

    @Test
    fun `måndagen kan ligga i föregående månad och år`() {
        assertEquals(LocalDate(2025, 12, 29), LocalDate(2026, 1, 2).weekMonday())
    }

    @Test
    fun `veckodagarna är sju, måndag först`() {
        val days = weekDays(LocalDate(2026, 10, 12))
        assertEquals(DAYS_PER_WEEK, days.size)
        assertEquals(LocalDate(2026, 10, 12), days.first())
        assertEquals(LocalDate(2026, 10, 18), days.last())
        assertEquals(DayOfWeek.SUNDAY, days.last().dayOfWeek)
    }

    @Test
    fun `tidsenheterna hänger ihop`() {
        assertEquals(3_600, SECONDS_PER_HOUR)
        assertEquals(1_440, MINUTES_PER_DAY)
        assertEquals(86_400, SECONDS_PER_DAY)
    }

    @Test
    fun `avrundning nedåt till hela minuten tar bort sekunder och bråkdelar`() {
        assertEquals(LocalDateTime(2026, 10, 12, 9, 41), LocalDateTime(2026, 10, 12, 9, 41, 59, 999_000_000).truncatedToMinute())
        assertEquals(LocalDateTime(2026, 10, 12, 23, 59), LocalDateTime(2026, 10, 12, 23, 59).truncatedToMinute())
    }
}
