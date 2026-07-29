package se.partee71.dagboken.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.domain.model.Aktivitet
import java.time.LocalTime

class ScreeningEventsTest {

    private fun aktivitet(namn: String, datum: String = "2026-07-29") = Aktivitet(
        id = namn, timestamp = "${datum}T09:00:00.000Z", datum = datum, tid = "09:00",
        aktivitet = namn, energy = 5, stress = 3, somatiska = 0, symptom = "", type = "screening",
    )

    // ─── activeScreeningEventLabels ───────────────────────────────────────────

    @Test fun `activeScreeningEventLabels only includes enabled configs`() {
        val configs = listOf(
            ScreeningEventConfig(enabled = true, time = "08:00"),
            ScreeningEventConfig(enabled = false, time = "12:00"),
            ScreeningEventConfig(enabled = true, time = "19:00"),
        )
        val active = activeScreeningEventLabels(configs)
        assertEquals(listOf("Efter frukost" to "08:00", "Kvällsmat" to "19:00"), active)
    }

    @Test fun `activeScreeningEventLabels returns empty list when nothing enabled`() {
        val configs = listOf(ScreeningEventConfig(enabled = false, time = "08:00"))
        assertTrue(activeScreeningEventLabels(configs).isEmpty())
    }

    // ─── computeScreeningEvents ───────────────────────────────────────────────

    @Test fun `computeScreeningEvents marks an event as logged when a matching aktivitet exists`() {
        val events = computeScreeningEvents(
            activeEvents = listOf("Lunch" to "12:00"),
            screeningsForDate = listOf(aktivitet("Lunch")),
            nowTime = LocalTime.of(13, 0),
            isToday = true,
        )
        assertTrue(events.single().logged)
        assertFalse(events.single().overdue)
    }

    @Test fun `computeScreeningEvents marks an unlogged past-due event as overdue only when today`() {
        val overdueToday = computeScreeningEvents(
            activeEvents = listOf("Lunch" to "12:00"),
            screeningsForDate = emptyList(),
            nowTime = LocalTime.of(13, 0),
            isToday = true,
        )
        assertTrue(overdueToday.single().overdue)

        val notOverdueOnOtherDay = computeScreeningEvents(
            activeEvents = listOf("Lunch" to "12:00"),
            screeningsForDate = emptyList(),
            nowTime = LocalTime.of(13, 0),
            isToday = false,
        )
        assertFalse(notOverdueOnOtherDay.single().overdue)
    }

    @Test fun `computeScreeningEvents is not overdue before the scheduled time`() {
        val events = computeScreeningEvents(
            activeEvents = listOf("Kvällsmat" to "19:00"),
            screeningsForDate = emptyList(),
            nowTime = LocalTime.of(13, 0),
            isToday = true,
        )
        assertFalse(events.single().overdue)
    }

    @Test fun `computeScreeningEvents returns empty list when no events are active`() {
        val events = computeScreeningEvents(
            activeEvents = emptyList(),
            screeningsForDate = emptyList(),
            nowTime = LocalTime.of(13, 0),
            isToday = true,
        )
        assertTrue(events.isEmpty())
    }
}
