package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Slot

/** Vilka larm som ska ligga och vad påminnelserna visar (NOT-2…NOT-5, NOT-10, NOT-17…NOT-19). */
class ReminderPlanTest {

    private val now = at("2026-05-06T09:30")
    private val today = day("2026-05-06")

    private fun alarms(settings: ReminderSettings) = reminderAlarms(settings, now, STOCKHOLM)

    // ── Larmen (NOT-2, NOT-4, NOT-12, NOT-18) ─────────────────────────────────

    @Test fun `standardinställningarna ger bara periodslutspåminnelsen kl 09_00, passerad idag`() {
        assertEquals(listOf(ReminderAlarm(Reminder.PeriodEnd, LocalTime(9, 0), at("2026-05-07T09:00"))), alarms(ReminderSettings()))
    }

    @Test fun `medicinpåminnelser på ger ett larm per påslagen tidpunkt 15 minuter före`() {
        val settings = ReminderSettings(medsEnabled = true).let { r ->
            r.copy(medSlots = r.medSlots.map { if (it.slot == Slot.NIGHT) it.copy(enabled = false) else it })
        }
        val meds = alarms(settings).filter { it.reminder is Reminder.Med }
        assertEquals(listOf(Slot.MORNING, Slot.MIDMORNING, Slot.LUNCH, Slot.AFTERNOON, Slot.EVENING), meds.map { (it.reminder as Reminder.Med).slot })
        // Morgon 07:00 har passerat (06:45) → i morgon; Förmiddag 10:00 → 09:45 idag.
        assertEquals(at("2026-05-07T06:45"), meds[0].at)
        assertEquals(at("2026-05-06T09:45"), meds[1].at)
        assertEquals(LocalTime(10, 0), meds[1].time)
    }

    @Test fun `huvudreglaget av ger inga medicinlarm, oavsett raderna`() {
        assertTrue(alarms(ReminderSettings(medsEnabled = false)).none { it.reminder is Reminder.Med })
    }

    @Test fun `bara påslagna måendetillfällen, på sina egna tider (NOT-4, NOT-5)`() {
        val settings = ReminderSettings().let { r ->
            r.copy(screeningOccasions = r.screeningOccasions.map {
                when (it.occasion) {
                    Occasion.BREAKFAST -> it.copy(enabled = true, time = LocalTime(8, 30))
                    Occasion.DINNER -> it.copy(enabled = true)
                    else -> it
                }
            })
        }
        val moods = alarms(settings).filter { it.reminder is Reminder.Mood }
        assertEquals(
            listOf(
                ReminderAlarm(Reminder.Mood(Occasion.BREAKFAST), LocalTime(8, 30), at("2026-05-07T08:30")),
                ReminderAlarm(Reminder.Mood(Occasion.DINNER), LocalTime(17, 0), at("2026-05-06T17:00")),
            ),
            moods,
        )
    }

    @Test fun `medicinlarmen följer tidpunktens egen tid, inte måendetiderna (NOT-2-regression)`() {
        val settings = ReminderSettings(medsEnabled = true).let { r ->
            r.copy(
                medSlots = r.medSlots.map { it.copy(enabled = it.slot == Slot.MORNING) },
                screeningOccasions = r.screeningOccasions.map { it.copy(enabled = true) },
            )
        }
        val morning = alarms(settings).single { it.reminder == Reminder.Med(Slot.MORNING) }
        assertEquals(LocalTime(7, 0), morning.time)
        assertEquals(at("2026-05-07T06:45"), morning.at)
    }

    @Test fun `dubblerade rader i äldre data ger ett larm per tidpunkt och tillfälle`() {
        val r = ReminderSettings(medsEnabled = true)
        val settings = r.copy(
            medSlots = listOf(r.medSlots[0], r.medSlots[0].copy(time = LocalTime(8, 0))),
            screeningOccasions = listOf(r.screeningOccasions[0].copy(enabled = true), r.screeningOccasions[0].copy(enabled = true, time = LocalTime(9, 0))),
        )
        assertEquals(3, alarms(settings).size)
        assertEquals(LocalTime(7, 0), alarms(settings).first().time)
    }

    @Test fun `nästa larm för en påminnelse, avslagen ger inget`() {
        val settings = ReminderSettings(medsEnabled = true)
        assertEquals(at("2026-05-06T11:45"), nextAlarm(Reminder.Med(Slot.LUNCH), settings, now, STOCKHOLM)?.at)
        assertNull(nextAlarm(Reminder.Med(Slot.LUNCH), settings.copy(medsEnabled = false), now, STOCKHOLM))
        assertNull(nextAlarm(Reminder.Mood(Occasion.LUNCH), settings, now, STOCKHOLM))
        assertEquals(at("2026-05-07T09:00"), nextAlarm(Reminder.PeriodEnd, settings, now, STOCKHOLM)?.at)
    }

    @Test fun `nästa larm på senast kända klockslaget när inställningarna inte går att läsa`() {
        assertEquals(at("2026-05-06T11:45"), nextAlarmAt(Reminder.Med(Slot.LUNCH), LocalTime(12, 0), now, STOCKHOLM).at)
        assertEquals(at("2026-05-06T12:00"), nextAlarmAt(Reminder.Mood(Occasion.LUNCH), LocalTime(12, 0), now, STOCKHOLM).at)
    }

    @Test fun `ett sent larm ligger kvar på dagens tid bara om just den tiden var schemalagd och inte utlöst (NOT-14)`() {
        val settings = ReminderSettings(medsEnabled = true).let { r -> r.copy(medSlots = r.medSlots.map { it.copy(enabled = it.slot == Slot.MORNING) }) }
        val morning = Reminder.Med(Slot.MORNING)
        val due = at("2026-05-06T06:45")
        val tomorrow = at("2026-05-07T06:45")
        fun at655(scheduled: Instant? = due, fired: Instant? = null, grace: Duration = 30.minutes, now: Instant = at("2026-05-06T06:55")) =
            reminderAlarms(settings, now, STOCKHOLM, grace, listOfNotNull(scheduled?.let { morning to it }).toMap(), listOfNotNull(fired?.let { morning to it }).toMap())
                .single { it.reminder == morning }.at

        assertEquals(due, at655(), "schemalagd 06:45, inte utlöst – ligger kvar idag")
        assertEquals(tomorrow, at655(fired = at("2026-05-06T06:46")), "utlöst – i morgon")
        assertEquals(due, at655(fired = at("2026-05-05T06:45")), "gårdagens utlösning räknas inte")
        assertEquals(tomorrow, at655(scheduled = null), "inget schemalagt (nyss påslagen, tom process) – i morgon (NOT-5)")
        assertEquals(tomorrow, at655(scheduled = at("2026-05-06T07:45")), "tiden flyttad bakåt förbi nu – i morgon (NOT-5)")
        assertEquals(tomorrow, at655(grace = Duration.ZERO), "utan marginal: i morgon")
        assertEquals(tomorrow, at655(now = at("2026-05-06T07:30")), "äldre än marginalen: i morgon")
    }

    @Test fun `efter omstart läggs de senast schemalagda tiderna om (NOT-6)`() {
        val med = Reminder.Med(Slot.MORNING)
        val mood = Reminder.Mood(Occasion.LUNCH)
        val now = at("2026-05-06T09:30")
        val scheduled = mapOf(
            med to at("2026-05-06T06:45"), // passerat för länge sedan → i morgon
            mood to at("2026-05-06T12:00"), // fortfarande framtida → samma
            Reminder.PeriodEnd to at("2026-05-06T09:10"), // missat under omstarten, inte utlöst → direkt
        )
        val restored = restoredAlarms(scheduled, emptyMap(), now, STOCKHOLM, 30.minutes).associateBy { it.reminder }

        assertEquals(ReminderAlarm(med, LocalTime(7, 0), at("2026-05-07T06:45")), restored[med])
        assertEquals(ReminderAlarm(mood, LocalTime(12, 0), at("2026-05-06T12:00")), restored[mood])
        assertEquals(at("2026-05-06T09:10"), restored[Reminder.PeriodEnd]?.at)
        val fired = restoredAlarms(scheduled, mapOf(Reminder.PeriodEnd to at("2026-05-06T09:10")), now, STOCKHOLM, 30.minutes)
        assertEquals(at("2026-05-07T09:10"), fired.single { it.reminder == Reminder.PeriodEnd }.at, "redan utlöst → i morgon")
        assertEquals(emptyList(), restoredAlarms(emptyMap(), emptyMap(), now, STOCKHOLM, 30.minutes))
    }

    @Test fun `efter omstart - medicinlarmet 23_45 ger tidpunkten 00_00`() {
        val restored = restoredAlarms(mapOf(Reminder.Med(Slot.NIGHT) to at("2026-05-05T23:45")), emptyMap(), at("2026-05-06T09:30"), STOCKHOLM)
        assertEquals(ReminderAlarm(Reminder.Med(Slot.NIGHT), LocalTime(0, 0), at("2026-05-06T23:45")), restored.single())
    }

    // ── Medicinpåminnelsens doser (NOT-3, NOT-10, NOT-17) ─────────────────────

    private val metformin = prescription(id = "met", name = "Metformin", dose = "500", slots = listOf(Slot.MORNING, Slot.EVENING))
    private val levaxin = prescription(id = "lev", name = "Levaxin", dose = "100", slots = listOf(Slot.MORNING))

    private fun stored(p: se.partee71.dagboken.core.model.Prescription, slot: Slot, status: DoseStatus = DoseStatus.PLANNED) =
        p.plannedDoses(today, STOCKHOLM).single { it.slot == slot }.copy(status = status)

    @Test fun `planerade doser för tidpunkten, och receptens som ännu inte skapats`() {
        val doses = slotDoses(Slot.MORNING, today, today, listOf(metformin, levaxin), listOf(stored(metformin, Slot.MORNING)), STOCKHOLM)
        assertEquals(listOf(DoseIds.prescribed("met", today, Slot.MORNING)), doses.stored.map { it.id })
        assertEquals(listOf(DoseIds.prescribed("lev", today, Slot.MORNING)), doses.missing.map { it.id })
        assertEquals(listOf("Levaxin", "Metformin"), doses.all.map { it.name })
    }

    @Test fun `tagna och överhoppade doser ger ingen notis (NOT-3)`() {
        val existing = listOf(stored(metformin, Slot.MORNING, DoseStatus.TAKEN), stored(levaxin, Slot.MORNING, DoseStatus.SKIPPED))
        assertTrue(slotDoses(Slot.MORNING, today, today, listOf(metformin, levaxin), existing, STOCKHOLM).isEmpty)
    }

    @Test fun `andra tidpunkter, andra dagar och vid behov-doser räknas inte`() {
        val prn = Dose("prn", date = today, slot = Slot.AS_NEEDED, name = "Alvedon")
        val prnWithSlot = Dose("prn2", date = today, slot = Slot.MORNING, name = "Ipren", prnId = "ipren")
        val yesterday = stored(metformin, Slot.MORNING).copy(id = "igår", date = day("2026-05-05"))
        val doses = slotDoses(Slot.MORNING, today, today, emptyList(), listOf(stored(metformin, Slot.EVENING), prn, prnWithSlot, yesterday), STOCKHOLM)
        assertTrue(doses.isEmpty)
        assertTrue(slotDoses(Slot.AS_NEEDED, today, today, listOf(metformin), emptyList(), STOCKHOLM).isEmpty)
    }

    @Test fun `en dos som saknas har dagens totala dos med höjningen (REC-12)`() {
        val boosted = prescription(id = "pred", name = "Prednisolon", dose = "5", boosts = listOf(boost("2026-05-01", "2026-05-10", "10")))
        assertEquals(listOf("15"), slotDoses(Slot.MORNING, today, today, listOf(boosted), emptyList(), STOCKHOLM).all.map { it.dose })
    }

    @Test fun `inaktiva recept och recept som inte gäller dagen ger inget`() {
        val inactive = metformin.copy(active = false)
        val later = prescription(id = "senare", start = "2026-05-10")
        assertTrue(slotDoses(Slot.MORNING, today, today, listOf(inactive, later), emptyList(), STOCKHOLM).isEmpty)
    }

    // ── Måendepåminnelsen (NOT-19) ─────────────────────────────────────────────

    @Test fun `bara det loggade tillfället tystas`() {
        val lunch = listOf(Screening("s1", date = today, occasion = Occasion.LUNCH))
        assertFalse(moodReminderDue(Occasion.LUNCH, lunch))
        assertTrue(moodReminderDue(Occasion.DINNER, lunch))
        assertTrue(moodReminderDue(Occasion.BREAKFAST, emptyList()))
    }
}
