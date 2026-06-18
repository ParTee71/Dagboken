package se.partee71.dagboken.ui.home

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.WeekFields

class DateFormatTest {

    // ─── formattedDate ────────────────────────────────────────────────────────

    @Test fun `formattedDate contains Vecka followed by current week number`() {
        val weekNum = LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear())
        val result = formattedDate()
        assertTrue("Expected 'Vecka $weekNum' in '$result'", result.contains("Vecka $weekNum"))
    }

    @Test fun `formattedDate starts with capitalised weekday`() {
        val result = formattedDate()
        assertTrue(result[0].isUpperCase())
    }

    @Test fun `formattedDate contains day-of-month number`() {
        val dayOfMonth = LocalDate.now().dayOfMonth.toString()
        val result = formattedDate()
        assertTrue("Expected day '$dayOfMonth' in '$result'", result.contains(dayOfMonth))
    }

    // ─── greeting ─────────────────────────────────────────────────────────────

    @Test fun `greeting returns a non-blank string`() {
        assertTrue(greeting().isNotBlank())
    }

    @Test fun `greeting starts with God`() {
        assertTrue("Expected greeting to start with 'God'", greeting().startsWith("God"))
    }
}
