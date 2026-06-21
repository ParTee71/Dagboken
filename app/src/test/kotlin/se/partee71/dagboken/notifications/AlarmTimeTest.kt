package se.partee71.dagboken.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmTimeTest {

    private fun epochMs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ─── medAlarmTriggerMs ────────────────────────────────────────────────────

    @Test fun `medAlarmTriggerMs Morgon fires 15 min before 07-00`() {
        val trigger = medAlarmTriggerMs("2026-06-18", "Morgon")
        assertEquals(epochMs(2026, 6, 18, 6, 45), trigger)
    }

    @Test fun `medAlarmTriggerMs Kväll fires 15 min before 19-00 (not 20-00)`() {
        val trigger = medAlarmTriggerMs("2026-06-18", "Kväll")
        assertEquals(epochMs(2026, 6, 18, 18, 45), trigger)
    }

    @Test fun `medAlarmTriggerMs Natt fires 15 min before 22-00`() {
        val trigger = medAlarmTriggerMs("2026-06-18", "Natt")
        assertEquals(epochMs(2026, 6, 18, 21, 45), trigger)
    }

    @Test fun `medAlarmTriggerMs Vid behov returns null`() {
        assertNull(medAlarmTriggerMs("2026-06-18", "Vid behov"))
    }

    @Test fun `medAlarmTriggerMs unknown tidpunkt returns null`() {
        assertNull(medAlarmTriggerMs("2026-06-18", "Okänd"))
    }

    @Test fun `medAlarmTriggerMs trigger is exactly 15 minutes before the tidpunkt hour`() {
        for ((tidpunkt, expectedHour) in listOf(
            "Morgon"      to 7,
            "Förmiddag"   to 10,
            "Lunch"       to 12,
            "Eftermiddag" to 15,
            "Kväll"       to 19,
            "Natt"        to 22,
        )) {
            val trigger = medAlarmTriggerMs("2026-06-18", tidpunkt) ?: error("null for $tidpunkt")
            val expected = epochMs(2026, 6, 18, expectedHour, 0) - 15 * 60 * 1000L
            assertEquals("$tidpunkt trigger should be 15 min before ${expectedHour}:00", expected, trigger)
        }
    }

    // ─── screeningAlarmTriggerMs ──────────────────────────────────────────────

    @Test fun `screeningAlarmTriggerMs returns today when time is in the future`() {
        val now = LocalDateTime.of(2026, 6, 18, 9, 0)  // 09:00
        val trigger = screeningAlarmTriggerMs(10, 0, now)
        assertEquals(epochMs(2026, 6, 18, 10, 0), trigger)
    }

    @Test fun `screeningAlarmTriggerMs returns next day when time is already past`() {
        val now = LocalDateTime.of(2026, 6, 18, 11, 0)  // 11:00
        val trigger = screeningAlarmTriggerMs(10, 0, now)
        assertEquals(epochMs(2026, 6, 19, 10, 0), trigger)  // next day
    }

    @Test fun `screeningAlarmTriggerMs returns next day when now equals alarm time`() {
        val now = LocalDateTime.of(2026, 6, 18, 10, 0)  // exactly at alarm time
        val trigger = screeningAlarmTriggerMs(10, 0, now)
        assertEquals(epochMs(2026, 6, 19, 10, 0), trigger)
    }

    @Test fun `screeningAlarmTriggerMs respects minutes`() {
        val now = LocalDateTime.of(2026, 6, 18, 8, 0)
        val trigger = screeningAlarmTriggerMs(8, 30, now)
        assertEquals(epochMs(2026, 6, 18, 8, 30), trigger)
    }
}
