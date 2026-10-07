package se.partee71.dagboken.core.engine.health

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Sömnens mittpunkter per natt – underlaget till regelbundenheten (HLS-10, HLS-13). Portad från 3.x `NightlyMidpointsTest`. */
class NightlyMidpointsTest {

    private fun midpoints(vararg sessions: SleepSession) = nightlyMidpoints(sessions.toList(), STOCKHOLM).values.toList()

    @Test fun `the midpoint sits halfway through the session`() {
        assertEquals(listOf(LocalTime(3, 0)), midpoints(sleep("2026-08-01T23:00", "2026-08-02T07:00")))
    }

    @Test fun `each night contributes one midpoint`() {
        assertEquals(
            listOf(LocalTime(3, 0), LocalTime(3, 30)),
            midpoints(sleep("2026-08-01T23:00", "2026-08-02T07:00"), sleep("2026-08-02T23:30", "2026-08-03T07:30")),
        )
    }

    @Test fun `a night split into two sessions counts once, using the longest`() {
        assertEquals(
            listOf(LocalTime(5, 0)),
            midpoints(sleep("2026-08-01T23:00", "2026-08-02T02:00"), sleep("2026-08-02T02:30", "2026-08-02T07:30")),
        )
    }

    @Test fun `a session crossing midnight is dated by its end, not split into two nights`() {
        val byDay = nightlyMidpoints(listOf(sleep("2026-08-01T22:00", "2026-08-02T06:00")), STOCKHOLM)
        assertEquals(mapOf(LocalDate(2026, 8, 2) to LocalTime(2, 0)), byDay)
    }

    @Test fun `no sessions gives no midpoints`() {
        assertEquals(emptyList<LocalTime>(), midpoints())
    }

    @Test fun `a daytime nap keeps its own midpoint`() {
        assertEquals(listOf(LocalTime(14, 0)), midpoints(sleep("2026-08-02T13:00", "2026-08-02T15:00")))
    }

    @Test fun `the longest session wins even when it starts later`() {
        assertEquals(
            listOf(LocalTime(4, 30)),
            midpoints(sleep("2026-08-02T00:00", "2026-08-02T00:30"), sleep("2026-08-02T01:00", "2026-08-02T08:00")),
        )
    }

    @Test fun `midpoints come in date order whatever the input order`() {
        assertEquals(
            listOf(LocalTime(3, 0), LocalTime(3, 30)),
            midpoints(sleep("2026-08-02T23:30", "2026-08-03T07:30"), sleep("2026-08-01T23:00", "2026-08-02T07:00")),
        )
    }

    @Test fun `the midpoint of a night over the spring-forward change is measured in real time`() {
        // 28 mars 23:00 CET → 29 mars 07:00 CEST är sju timmar, så mitten är 03:30 sommartid, inte 03:00.
        assertEquals(listOf(LocalTime(3, 30)), midpoints(sleep("2026-03-28T23:00", "2026-03-29T07:00")))
    }

    @Test fun `regularity is rolling per night and needs four nights`() {
        val nights = (1..5).map { day -> sleep("2026-08-%02dT23:00".format(day), "2026-08-%02dT07:00".format(day + 1)) }
        val sd = midpointSdByNight(nightlyMidpoints(nights, STOCKHOLM))
        assertEquals((2..6).map { LocalDate(2026, 8, it) }, sd.keys.toList())
        assertNull(sd[LocalDate(2026, 8, 4)])
        assertNotNull(sd[LocalDate(2026, 8, 5)])
        assertEquals(0.0, sd.getValue(LocalDate(2026, 8, 6))!!, 0.001)
    }
}
