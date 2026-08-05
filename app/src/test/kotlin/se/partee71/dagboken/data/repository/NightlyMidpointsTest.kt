package se.partee71.dagboken.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Enhetstest för [nightlyMidpoints] — underlaget till regelbundenhetsmåttet i
 * sömnkvaliteten (HLS-10).
 */
class NightlyMidpointsTest {

    private val zone = ZoneId.of("Europe/Stockholm")

    private fun window(from: String, to: String) = SleepWindow(
        LocalDateTime.parse(from).atZone(zone).toInstant(),
        LocalDateTime.parse(to).atZone(zone).toInstant(),
    )

    @Test fun `the midpoint sits halfway through the session`() {
        val midpoints = nightlyMidpoints(listOf(window("2026-08-01T23:00", "2026-08-02T07:00")), zone)
        assertEquals(listOf(LocalTime.of(3, 0)), midpoints)
    }

    @Test fun `each night contributes one midpoint`() {
        val midpoints = nightlyMidpoints(
            listOf(
                window("2026-08-01T23:00", "2026-08-02T07:00"),
                window("2026-08-02T23:30", "2026-08-03T07:30"),
            ),
            zone,
        )
        assertEquals(listOf(LocalTime.of(3, 0), LocalTime.of(3, 30)), midpoints)
    }

    @Test fun `a night split into two sessions counts once, using the longest`() {
        // Samsung delar ibland en avbruten natt i två sessioner. Räknades båda skulle
        // natten se ut som två olika läggtider och blåsa upp spridningen.
        val midpoints = nightlyMidpoints(
            listOf(
                window("2026-08-01T23:00", "2026-08-02T02:00"),
                window("2026-08-02T02:30", "2026-08-02T07:30"),
            ),
            zone,
        )
        assertEquals(listOf(LocalTime.of(5, 0)), midpoints)
    }

    @Test fun `a session crossing midnight is dated by its end, not split into two nights`() {
        val midpoints = nightlyMidpoints(listOf(window("2026-08-01T22:00", "2026-08-02T06:00")), zone)
        assertEquals(1, midpoints.size)
        assertEquals(LocalTime.of(2, 0), midpoints.single())
    }

    @Test fun `no sessions gives no midpoints`() {
        assertEquals(emptyList<LocalTime>(), nightlyMidpoints(emptyList(), zone))
    }

    @Test fun `a daytime nap keeps its own midpoint`() {
        val midpoints = nightlyMidpoints(listOf(window("2026-08-02T13:00", "2026-08-02T15:00")), zone)
        assertEquals(listOf(LocalTime.of(14, 0)), midpoints)
    }

    @Test fun `the longest session wins even when it starts later`() {
        val midpoints = nightlyMidpoints(
            listOf(
                window("2026-08-02T00:00", "2026-08-02T00:30"),
                window("2026-08-02T01:00", "2026-08-02T08:00"),
            ),
            zone,
        )
        assertEquals(listOf(LocalTime.of(4, 30)), midpoints)
    }
}
