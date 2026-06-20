package se.partee71.dagboken.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreeningTimeTest {

    @Test fun `parse returns correct hour and minute`() {
        val st = ScreeningTime.parse("08:30")!!
        assertEquals(8, st.hour)
        assertEquals(30, st.min)
    }

    @Test fun `parse handles midnight`() {
        val st = ScreeningTime.parse("00:00")!!
        assertEquals(0, st.hour)
        assertEquals(0, st.min)
    }

    @Test fun `parse handles end of day`() {
        val st = ScreeningTime.parse("23:59")!!
        assertEquals(23, st.hour)
        assertEquals(59, st.min)
    }

    @Test fun `parse returns null when hour is non-numeric`() {
        assertNull(ScreeningTime.parse("ab:30"))
    }

    @Test fun `parse returns null when minute is non-numeric`() {
        assertNull(ScreeningTime.parse("08:xx"))
    }

    @Test fun `parse returns null for empty string`() {
        assertNull(ScreeningTime.parse(""))
    }

    @Test fun `parse returns null when only colon`() {
        assertNull(ScreeningTime.parse(":"))
    }

    @Test fun `parse handles default screening event times`() {
        val times = listOf("08:00", "12:00", "17:00", "21:00")
        val expected = listOf(
            ScreeningTime(8, 0),
            ScreeningTime(12, 0),
            ScreeningTime(17, 0),
            ScreeningTime(21, 0),
        )
        times.zip(expected).forEach { (t, e) ->
            assertEquals("parse($t)", e, ScreeningTime.parse(t))
        }
    }
}
