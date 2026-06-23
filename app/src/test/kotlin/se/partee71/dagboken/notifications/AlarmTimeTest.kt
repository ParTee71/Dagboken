package se.partee71.dagboken.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmTimeTest {

    private fun epochMs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ─── screeningAlarmTriggerMs ──────────────────────────────────────────────

    @Test fun `screeningAlarmTriggerMs returns today when time is in the future`() {
        val now = LocalDateTime.of(2026, 6, 18, 9, 0)
        val trigger = screeningAlarmTriggerMs(10, 0, now)
        assertEquals(epochMs(2026, 6, 18, 10, 0), trigger)
    }

    @Test fun `screeningAlarmTriggerMs returns next day when time is already past`() {
        val now = LocalDateTime.of(2026, 6, 18, 11, 0)
        val trigger = screeningAlarmTriggerMs(10, 0, now)
        assertEquals(epochMs(2026, 6, 19, 10, 0), trigger)
    }

    @Test fun `screeningAlarmTriggerMs returns next day when now equals alarm time`() {
        val now = LocalDateTime.of(2026, 6, 18, 10, 0)
        val trigger = screeningAlarmTriggerMs(10, 0, now)
        assertEquals(epochMs(2026, 6, 19, 10, 0), trigger)
    }

    @Test fun `screeningAlarmTriggerMs respects minutes`() {
        val now = LocalDateTime.of(2026, 6, 18, 8, 0)
        val trigger = screeningAlarmTriggerMs(8, 30, now)
        assertEquals(epochMs(2026, 6, 18, 8, 30), trigger)
    }
}
