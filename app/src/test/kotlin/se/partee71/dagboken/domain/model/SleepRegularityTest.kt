package se.partee71.dagboken.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Enhetstest för regelbundenhetsmåttet i sömnkvaliteten (HLS-10): spridningen i
 * sömnens mittpunkt, räknad cirkulärt runt dygnet.
 */
class SleepRegularityTest {

    private fun times(vararg hhmm: String) = hhmm.map { LocalTime.parse(it) }

    @Test fun `too few nights gives null rather than a made-up spread`() {
        assertNull(sleepMidpointSdMinutes(times("03:00", "03:10", "02:50")))
    }

    @Test fun `identical midpoints give zero spread`() {
        assertEquals(0.0, sleepMidpointSdMinutes(times("03:00", "03:00", "03:00", "03:00"))!!, 0.001)
    }

    @Test fun `a regular sleeper has a small spread`() {
        val sd = sleepMidpointSdMinutes(times("03:00", "03:10", "02:50", "03:05", "02:55"))!!
        assertTrue("Förväntade liten spridning, fick $sd", sd < 15.0)
    }

    @Test fun `shift-like timing gives a large spread`() {
        val sd = sleepMidpointSdMinutes(times("22:00", "03:00", "07:00", "01:00", "05:00"))!!
        assertTrue("Förväntade stor spridning, fick $sd", sd > 90.0)
    }

    @Test fun `midpoints either side of midnight are close, not twelve hours apart`() {
        // Kärnan i den cirkulära beräkningen: 23:50 och 00:10 ligger 20 minuter isär.
        // Ett rakt medelvärde av klockslag hade gett en spridning på timmar här.
        val sd = sleepMidpointSdMinutes(times("23:50", "00:10", "23:55", "00:05"))!!
        assertTrue("Förväntade liten spridning över midnatt, fick $sd", sd < 20.0)
    }

    @Test fun `the spread is the same wherever on the clock the nights sit`() {
        val aroundMidnight = sleepMidpointSdMinutes(times("23:30", "00:30", "23:45", "00:15"))!!
        val aroundNoon = sleepMidpointSdMinutes(times("11:30", "12:30", "11:45", "12:15"))!!
        assertEquals(aroundNoon, aroundMidnight, 0.001)
    }
}
