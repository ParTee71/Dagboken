package se.partee71.dagboken.ui.common

import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test

class DateFormatTest {

    @Test
    fun `datum visas på svenska utan punkter`() {
        assertEquals("lör 19 dec 2026", DateFormat.display(LocalDate(2026, 12, 19)))
    }

    @Test
    fun `millisekunder från datumväljaren ger samma datum tillbaka`() {
        val date = LocalDate(2026, 2, 28)
        assertEquals(date, DateFormat.fromEpochMillis(DateFormat.toEpochMillis(date)))
        assertEquals(LocalDate(1969, 12, 31), DateFormat.fromEpochMillis(-1))
    }

    @Test
    fun `tider visas med 24 timmar och två siffror`() {
        assertEquals("07:05", DateFormat.time(LocalTime(7, 5)))
        assertEquals("18:00", DateFormat.time(LocalTime(18, 0)))
    }
}
