package se.partee71.dagboken.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import se.partee71.dagboken.R
import java.time.LocalDate
import java.time.temporal.WeekFields

class DateFormatTest {

    // ─── isoWeekNumber ────────────────────────────────────────────────────────

    @Test fun `isoWeekNumber matches ISO week of the given date`() {
        val date = LocalDate.of(2026, 1, 15)
        assertEquals(date.get(WeekFields.ISO.weekOfWeekBasedYear()), isoWeekNumber(date))
    }

    @Test fun `isoWeekNumber defaults to today`() {
        val today = LocalDate.now()
        assertEquals(today.get(WeekFields.ISO.weekOfWeekBasedYear()), isoWeekNumber())
    }

    // ─── greetingRes ──────────────────────────────────────────────────────────
    // Hälsningen ligger som strängresurs (svenska i strings.xml), så testet
    // verifierar vilken resurs varje tid på dygnet väljer — inte texten i sig.

    @Test fun `greetingRes picks morning between 5 and 11`() {
        listOf(5, 8, 11).forEach { assertEquals(R.string.greeting_morning, greetingRes(it)) }
    }

    @Test fun `greetingRes picks afternoon between 12 and 16`() {
        listOf(12, 14, 16).forEach { assertEquals(R.string.greeting_afternoon, greetingRes(it)) }
    }

    @Test fun `greetingRes picks evening between 17 and 20`() {
        listOf(17, 19, 20).forEach { assertEquals(R.string.greeting_evening, greetingRes(it)) }
    }

    @Test fun `greetingRes picks night for late and early hours`() {
        listOf(0, 3, 4, 21, 23).forEach { assertEquals(R.string.greeting_night, greetingRes(it)) }
    }
}
