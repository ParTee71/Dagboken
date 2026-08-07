package se.partee71.dagboken.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import se.partee71.dagboken.data.datastore.DEFAULT_MED_NOTIFICATIONS
import se.partee71.dagboken.data.datastore.MED_NOTIFICATION_TIDPUNKTER
import se.partee71.dagboken.data.datastore.MedNotificationConfig
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

    // ─── medAlarmTriggerMs ────────────────────────────────────────────────────

    @Test fun `medAlarmTriggerMs fires 15 min before scheduled time`() {
        val now = LocalDateTime.of(2026, 6, 18, 7, 0)
        val trigger = medAlarmTriggerMs(8, 0, now = now)
        assertEquals(epochMs(2026, 6, 18, 7, 45), trigger)
    }

    @Test fun `medAlarmTriggerMs returns next day when lead time has already passed`() {
        val now = LocalDateTime.of(2026, 6, 18, 8, 0)
        val trigger = medAlarmTriggerMs(8, 0, now = now)
        assertEquals(epochMs(2026, 6, 19, 7, 45), trigger)
    }

    @Test fun `medAlarmTriggerMs handles midnight rollover`() {
        val now = LocalDateTime.of(2026, 6, 18, 23, 50)
        val trigger = medAlarmTriggerMs(0, 0, now = now)
        assertEquals(epochMs(2026, 6, 19, 23, 45), trigger)
    }

    @Test fun `medAlarmTriggerMs respects custom lead minutes`() {
        val now = LocalDateTime.of(2026, 6, 18, 9, 0)
        val trigger = medAlarmTriggerMs(10, 0, leadMinutes = 30, now = now)
        assertEquals(epochMs(2026, 6, 18, 9, 30), trigger)
    }

    // ─── medAlarmPlans (NOT-18) ───────────────────────────────────────────────

    @Test fun `med alarm plans cover every enabled medicine time`() {
        val plans = medAlarmPlans(DEFAULT_MED_NOTIFICATIONS)

        assertEquals(MED_NOTIFICATION_TIDPUNKTER, plans.map { it.tidpunkt })
        assertEquals(MED_NOTIFICATION_TIDPUNKTER.indices.toList(), plans.map { it.slot })
    }

    @Test fun `med alarm plans skip disabled times`() {
        val configs = DEFAULT_MED_NOTIFICATIONS.mapIndexed { i, c -> c.copy(enabled = i == 2) }

        val plans = medAlarmPlans(configs)

        assertEquals(1, plans.size)
        assertEquals(2, plans[0].slot)
        assertEquals("Lunch", plans[0].tidpunkt)
    }

    @Test fun `med alarm plans ignore entries beyond the known medicine times`() {
        val configs = DEFAULT_MED_NOTIFICATIONS + MedNotificationConfig(enabled = true, time = "23:00")

        assertEquals(MED_NOTIFICATION_TIDPUNKTER.size, medAlarmPlans(configs).size)
    }

    /**
     * Regression: medicinlarmen hämtades ur screeningkonfigurationen, så morgondosens
     * påminnelse hamnade 15 minuter före screeningtiden (07:45 för en screening 08:00)
     * i stället för 15 minuter före tidpunkten Morgon 07:00.
     */
    @Test fun `morning dose reminder is fifteen minutes before the medicine time, not the screening time`() {
        val now = LocalDateTime.of(2026, 6, 18, 5, 0)
        val morgon = medAlarmPlans(DEFAULT_MED_NOTIFICATIONS).single { it.tidpunkt == "Morgon" }

        val (hour, minute) = morgon.time.split(":").map { it.toInt() }
        assertEquals("07:00", morgon.time)
        assertEquals(epochMs(2026, 6, 18, 6, 45), medAlarmTriggerMs(hour, minute, now = now))
    }
}
