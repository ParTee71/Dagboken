package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.SlotReminder

/** Påminnelsereglerna bakom Påminnelser-arket (NOT-4, NOT-16, NOT-18): rad för rad och vad som blir aktivt. */
class ReminderSettingsRulesTest {
    private val none = ReminderSettings(
        medsEnabled = false,
        medSlots = Slot.SCHEDULED.map { SlotReminder(it, enabled = false) },
        screeningOccasions = Occasion.entries.map { OccasionReminder(it, enabled = false) }
    )
    private val morning = SlotReminder(Slot.SCHEDULED.first(), enabled = true, time = LocalTime(6, 30))
    private val lunch = OccasionReminder(Occasion.entries[1], enabled = true)

    @Test fun `withSlot ersätter bara tidpunktens rad och behåller ordningen`() {
        val changed = none.withSlot(morning)
        assertEquals(Slot.SCHEDULED, changed.medSlots.map { it.slot })
        assertEquals(morning, changed.medSlots.first())
        assertEquals(none.medSlots.drop(1), changed.medSlots.drop(1))
        assertEquals(none.screeningOccasions, changed.screeningOccasions)
    }

    @Test fun `withOccasion ersätter bara tillfällets rad och behåller ordningen`() {
        val changed = none.withOccasion(lunch)
        assertEquals(Occasion.entries.toList(), changed.screeningOccasions.map { it.occasion })
        assertEquals(lunch, changed.screeningOccasions[1])
        assertEquals(none.screeningOccasions.filterIndexed { i, _ -> i != 1 }, changed.screeningOccasions.filterIndexed { i, _ -> i != 1 })
        assertEquals(none.medSlots, changed.medSlots)
    }

    @Test fun `activeReminders räknar tidpunkter bara när huvudreglaget är på, och påslagna tillfällen alltid`() {
        assertEquals(emptySet<Any>(), none.activeReminders())
        val slotOnly = none.withSlot(morning)
        assertEquals(emptySet<Any>(), slotOnly.activeReminders())
        assertEquals(setOf<Any>(morning.slot), slotOnly.copy(medsEnabled = true).activeReminders())
        assertEquals(setOf<Any>(lunch.occasion), none.withOccasion(lunch).activeReminders())
    }

    @Test fun `turnsOnIn gäller bara det som blir aktivt`() {
        val on = none.copy(medsEnabled = true).withSlot(morning)
        assertTrue(none.turnsOnIn(none.withOccasion(lunch)))
        assertTrue(none.withSlot(morning).turnsOnIn(on), "huvudreglaget på med en påslagen tidpunkt")
        assertFalse(none.turnsOnIn(none.withSlot(morning)), "huvudreglaget av – inget blir aktivt")
        assertFalse(none.turnsOnIn(none.copy(medsEnabled = true)), "ingen tidpunkt påslagen")
        assertFalse(on.turnsOnIn(on.withSlot(morning.copy(time = LocalTime(7, 0)))), "bara klockslaget ändrat")
        assertFalse(on.turnsOnIn(on.copy(medsEnabled = false)), "avslaget")
        assertFalse(on.turnsOnIn(on), "oförändrat")
    }
}
