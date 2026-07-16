package se.partee71.dagboken.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DateUtilsTest {

    @Test fun `formatDisplayDate returns Idag for today`() {
        val today = LocalDate.now().toString()
        assertEquals("Idag", formatDisplayDate(today))
    }

    @Test fun `formatDisplayDate returns Igår for yesterday`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        assertEquals("Igår", formatDisplayDate(yesterday))
    }

    @Test fun `formatDisplayDate returns formatted date for older dates`() {
        val older = "2024-01-15"
        val result = formatDisplayDate(older)
        assertTrue("Expected formatted date, got '$result'", result != "Idag" && result != "Igår")
        assertTrue("Expected capitalized weekday", result[0].isUpperCase())
    }

    @Test fun `formatTime formats as HH-mm with leading zeros`() {
        assertEquals("08:05", formatTime(LocalTime.of(8, 5)))
        assertEquals("23:59", formatTime(LocalTime.of(23, 59)))
        assertEquals("00:00", formatTime(LocalTime.of(0, 0)))
    }

    @Test fun `formatDayDate formats weekday and date with capitalized first letter`() {
        // 2024-01-15 is a Monday
        val result = formatDayDate(LocalDate.of(2024, 1, 15))
        assertEquals("Måndag 15 januari", result)
    }

    @Test fun `formatShortDate formats as day and abbreviated month`() {
        assertEquals("15 jan.", formatShortDate(LocalDate.of(2024, 1, 15)))
        assertEquals("1 dec.", formatShortDate(LocalDate.of(2024, 12, 1)))
    }

    @Test fun `formatShortDateYear formats as day, abbreviated month and year`() {
        assertEquals("15 jan. 2024", formatShortDateYear(LocalDate.of(2024, 1, 15)))
    }

    @Test fun `formatWeekdayShort returns three-letter Swedish weekday abbreviation`() {
        // 2024-01-15 is a Monday
        assertEquals("Mån", formatWeekdayShort(LocalDate.of(2024, 1, 15)))
        assertEquals("Tis", formatWeekdayShort(LocalDate.of(2024, 1, 16)))
        assertEquals("Ons", formatWeekdayShort(LocalDate.of(2024, 1, 17)))
        assertEquals("Tor", formatWeekdayShort(LocalDate.of(2024, 1, 18)))
        assertEquals("Fre", formatWeekdayShort(LocalDate.of(2024, 1, 19)))
        assertEquals("Lör", formatWeekdayShort(LocalDate.of(2024, 1, 20)))
        assertEquals("Sön", formatWeekdayShort(LocalDate.of(2024, 1, 21)))
    }
}
