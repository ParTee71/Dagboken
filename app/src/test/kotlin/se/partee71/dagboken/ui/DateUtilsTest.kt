package se.partee71.dagboken.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
}
