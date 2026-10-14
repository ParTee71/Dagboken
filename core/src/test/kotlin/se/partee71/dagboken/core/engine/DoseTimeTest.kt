package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.SlotReminder

/** Tidpunktens klockslag ur inställningarna (NOT-18, REC-6) och dosens klockslag i schemat (MED-4, DAT-8). */
class DoseTimeTest {
    private val date = day("2026-10-04")

    /** En planerad receptdos för Natt, skapad med standardklockslaget 22:00. */
    private val night = Dose(DoseIds.prescribed("r1", date, Slot.NIGHT), date, Slot.NIGHT, "Sertralin", plannedTime = LocalTime(22, 0), prescriptionId = "r1")

    @Test fun `tidpunktens klockslag är radens, också när påminnelsen eller huvudreglaget är av`() {
        assertEquals(LocalTime(6, 15), OWN_TIMES.timeOf(Slot.MORNING), "påminnelsen av")
        assertEquals(LocalTime(23, 30), OWN_TIMES.timeOf(Slot.NIGHT))
        assertEquals(Slot.LUNCH.defaultTime, OWN_TIMES.timeOf(Slot.LUNCH))
        assertEquals(Slot.SCHEDULED.map { it.defaultTime }, Slot.SCHEDULED.map { ReminderSettings().timeOf(it) }, "standard = REC-6")
    }

    @Test fun `utan rad gäller tidpunktens standard, och vid två rader den första (som larmen)`() {
        assertEquals(LocalTime(12, 0), OWN_TIMES.timeOf(Slot.AS_NEEDED), "Vid behov har ingen rad")
        assertEquals(Slot.NIGHT.defaultTime, ReminderSettings(medSlots = emptyList()).timeOf(Slot.NIGHT))
        val twice = ReminderSettings(medSlots = listOf(SlotReminder(Slot.NIGHT, time = LocalTime(21, 0)), SlotReminder(Slot.NIGHT, time = LocalTime(23, 0))))
        assertEquals(LocalTime(21, 0), twice.timeOf(Slot.NIGHT))
    }

    @Test fun `en planerad eller överhoppad receptdos följer inställningen, inte det lagrade klockslaget`() {
        assertEquals(LocalTime(23, 30), night.scheduledTime(OWN_TIMES))
        assertEquals(LocalTime(23, 30), night.copy(status = DoseStatus.SKIPPED).scheduledTime(OWN_TIMES), "hoppar inte vid överhoppning")
        assertEquals(LocalTime(22, 0), night.scheduledTime(ReminderSettings()))
        assertEquals(LocalTime(23, 30), night.copy(plannedTime = null).scheduledTime(OWN_TIMES))
        assertEquals(LocalTime(23, 30), night.copy(prescriptionId = null).scheduledTime(OWN_TIMES), "migrerad, receptet bara i id:t")
    }

    @Test fun `tagna, vid behov-, extra- och engångsdoser behåller sitt klockslag`() {
        assertEquals(LocalTime(22, 0), night.copy(status = DoseStatus.TAKEN).scheduledTime(OWN_TIMES))
        val extra = Dose("x", date, Slot.AS_NEEDED, "Sertralin", plannedTime = LocalTime(9, 40), prescriptionId = "r1")
        assertEquals(LocalTime(9, 40), extra.scheduledTime(OWN_TIMES))
        val fromPrn = Dose("p", date, Slot.NIGHT, "Melatonin", plannedTime = LocalTime(21, 0), prnId = "m1")
        assertEquals(LocalTime(21, 0), fromPrn.scheduledTime(OWN_TIMES))
        val oneOff = Dose("o", date, Slot.NIGHT, "Melatonin", plannedTime = LocalTime(21, 15))
        assertEquals(LocalTime(21, 15), oneOff.scheduledTime(OWN_TIMES))
        assertEquals(LocalTime(23, 30), oneOff.copy(plannedTime = null).scheduledTime(OWN_TIMES), "utan klockslag: tidpunktens")
    }
}
