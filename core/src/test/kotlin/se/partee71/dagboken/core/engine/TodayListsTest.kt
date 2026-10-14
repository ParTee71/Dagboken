package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Slot

/**
 * Idags listor: hälsningen (HEM-1), Mediciner-kortets indelning (MED-1, MED-5, MED-13), vid behov-kortets
 * val (FAV-2, FAV-11) och datumremsans punkter (HEM-14). Fasta datum och tider i Europe/Stockholm.
 */
class TodayListsTest {

    private val today = day("2026-10-04")

    private fun dose(
        id: String,
        slot: Slot,
        status: DoseStatus = DoseStatus.PLANNED,
        date: String = "2026-10-04",
        time: LocalTime? = null,
        name: String = id,
    ) = Dose(id, day(date), slot, name = name, status = status, plannedTime = time)

    // ── Hälsningen (HEM-1) ───────────────────────────────────────────────────

    @Test fun `dygnsdelen följer 3x timmar`() {
        assertEquals(DayPart.NIGHT, dayPartAt(4))
        assertEquals(DayPart.MORNING, dayPartAt(5))
        assertEquals(DayPart.MORNING, dayPartAt(11))
        assertEquals(DayPart.AFTERNOON, dayPartAt(12))
        assertEquals(DayPart.AFTERNOON, dayPartAt(16))
        assertEquals(DayPart.EVENING, dayPartAt(17))
        assertEquals(DayPart.EVENING, dayPartAt(20))
        assertEquals(DayPart.NIGHT, dayPartAt(21))
        assertEquals(DayPart.NIGHT, dayPartAt(0))
    }

    // ── Mediciner-kortet (MED-1, MED-5, MED-13) ─────────────────────────────

    @Test fun `dagens doser delas i att göra, avklarade och kommande, i tidpunktsordning`() {
        val doses = listOf(
            dose("natt", Slot.NIGHT),
            dose("kvall", Slot.EVENING),
            dose("lunch", Slot.LUNCH),
            dose("morgon", Slot.MORNING, DoseStatus.TAKEN),
            dose("formiddag", Slot.MIDMORNING, DoseStatus.SKIPPED),
            dose("morgon2", Slot.MORNING),
            dose("vidbehov", Slot.AS_NEEDED, DoseStatus.TAKEN),
            dose("planeradvidbehov", Slot.AS_NEEDED),
            dose("igar", Slot.MORNING, date = "2026-10-03"),
        )
        val list = doseChecklist(doses, today, at("2026-10-04T10:30"), STOCKHOLM, reminders = ReminderSettings())

        assertEquals(listOf("morgon2" to Due.LATE, "lunch" to Due.SOON, "planeradvidbehov" to null), list.shown.map { it.dose.id to it.due })
        assertEquals(listOf("morgon", "formiddag", "vidbehov"), list.done.map { it.id })
        assertEquals(listOf("kvall", "natt"), list.upcoming.map { it.dose.id }, "mer än 3 h fram")
    }

    @Test fun `en dos precis tre timmar fram är snart, och det planerade klockslaget går före tidpunktens`() {
        val doses = listOf(dose("kvall", Slot.EVENING), dose("tidig", Slot.EVENING, time = LocalTime(15, 0)))
        val list = doseChecklist(doses, today, at("2026-10-04T16:00"), STOCKHOLM, reminders = ReminderSettings())
        assertEquals(listOf("tidig" to Due.LATE, "kvall" to Due.SOON), list.shown.map { it.dose.id to it.due })
        assertTrue(list.upcoming.isEmpty())
    }

    @Test fun `en planerad receptdos förfaller och sorteras efter inställningens klockslag, en tagen behåller sitt`() {
        // Natt 23:30 i inställningen (dosen skapades med 22:00): kl. 20:00 är den kommande, inte snart.
        val recipe = dose("a", Slot.NIGHT, time = LocalTime(22, 0)).copy(prescriptionId = "r1")
        val oneOff = dose("b", Slot.NIGHT, time = LocalTime(23, 0))
        val standard = doseChecklist(listOf(recipe, oneOff), today, at("2026-10-04T20:00"), STOCKHOLM, ReminderSettings())
        assertEquals(listOf(Triple("a", Due.SOON, LocalTime(22, 0)), Triple("b", Due.SOON, LocalTime(23, 0))), standard.shown.map { Triple(it.dose.id, it.due, it.time) })
        val own = doseChecklist(listOf(recipe, oneOff), today, at("2026-10-04T20:00"), STOCKHOLM, OWN_TIMES)
        assertEquals(listOf("b" to Due.SOON), own.shown.map { it.dose.id to it.due })
        assertEquals(listOf(Triple("a", Due.UPCOMING, LocalTime(23, 30))), own.upcoming.map { Triple(it.dose.id, it.due, it.time) })
        val late = doseChecklist(listOf(recipe, oneOff), today, at("2026-10-04T23:45"), STOCKHOLM, OWN_TIMES)
        assertEquals(listOf("b", "a"), late.shown.map { it.dose.id }, "23:00 före 23:30 – inte namnordning")
        val taken = recipe.copy(status = DoseStatus.TAKEN)
        assertEquals(listOf(taken), doseChecklist(listOf(taken), today, at("2026-10-04T20:00"), STOCKHOLM, OWN_TIMES).done)
        assertEquals(null, doseChecklist(listOf(dose("v", Slot.AS_NEEDED)), today, at("2026-10-04T20:00"), STOCKHOLM, OWN_TIMES).shown.single().time)
    }

    @Test fun `en tidigare dag har inga kommande och inget försenat`() {
        val doses = listOf(dose("natt", Slot.NIGHT, date = "2026-10-01"), dose("morgon", Slot.MORNING, date = "2026-10-01"))
        val list = doseChecklist(doses, day("2026-10-01"), at("2026-10-04T08:00"), STOCKHOLM, reminders = ReminderSettings())
        assertEquals(listOf("morgon" to Due.PAST, "natt" to Due.PAST), list.shown.map { it.dose.id to it.due })
        assertTrue(list.upcoming.isEmpty())
    }

    @Test fun `samma tidpunkt ordnas på klockslag, namn och id`() {
        val doses = listOf(
            dose("b", Slot.MORNING, name = "omega"),
            dose("a", Slot.MORNING, name = "Omega"),
            dose("c", Slot.MORNING, name = "Levaxin"),
            dose("d", Slot.MORNING, name = "Zink", time = LocalTime(6, 0)),
        )
        val list = doseChecklist(doses, today, at("2026-10-04T09:00"), STOCKHOLM, reminders = ReminderSettings())
        assertEquals(listOf("d", "c", "a", "b"), list.shown.map { it.dose.id })
    }

    // ── Vid behov-kortet (FAV-2, FAV-11) ─────────────────────────────────────

    @Test fun `favoriter är snabbval, övriga och aktiva recept i Fler – utan dubbletter på namn`() {
        val alvedon = PrnMedicine("1", "Alvedon", favorite = true)
        val imigran = PrnMedicine("2", "imigran", favorite = true)
        val loratadin = PrnMedicine("3", "Loratadin")
        val levaxin = Prescription("l", "Levaxin", period = Period(day("2026-01-01")))
        val sameName = Prescription("a", " alvedon ", period = Period(day("2026-01-01")))
        val paused = Prescription("p", "Atarax", active = false)
        val ended = Prescription("e", "Amoxicillin", period = Period(day("2026-09-01"), day("2026-10-03")))
        val notStarted = Prescription("n", "Kåvepenin", period = Period(day("2026-10-05")))
        val betapred = Prescription("b", "Betapred")

        val choices = asNeededChoices(listOf(loratadin, imigran, alvedon), listOf(levaxin, sameName, paused, ended, notStarted, betapred), today)

        assertEquals(listOf(alvedon, imigran), choices.favorites)
        assertEquals(listOf(loratadin), choices.others)
        assertEquals(listOf(betapred, levaxin), choices.prescriptions)
        assertEquals(3, choices.moreCount)
    }

    @Test fun `samma namn med olika styrka är olika mediciner, så båda syns (REC-14)`() {
        val prn = PrnMedicine("1", "Alvedon", strength = "500 mg")
        val sameStrength = Prescription("a", "alvedon", strength = " 500 mg", period = Period(day("2026-01-01")))
        val otherStrength = Prescription("b", "Alvedon", strength = "1 g", period = Period(day("2026-01-01")))
        assertEquals(listOf(otherStrength), asNeededChoices(listOf(prn), listOf(sameStrength, otherStrength), today).prescriptions)
    }

    @Test fun `en medicin utan styrka och samma med styrka är samma medicin, åt båda håll (FAV-11)`() {
        val withStrength = Prescription("a", "Alvedon", strength = "500 mg", period = Period(day("2026-01-01")))
        val without = Prescription("b", "Alvedon", period = Period(day("2026-01-01")))
        assertEquals(emptyList(), asNeededChoices(listOf(PrnMedicine("1", "Alvedon")), listOf(withStrength), today).prescriptions)
        assertEquals(emptyList(), asNeededChoices(listOf(PrnMedicine("1", "alvedon", strength = "500  MG")), listOf(without, withStrength), today).prescriptions)
    }

    // ── Datumremsans punkter (HEM-14) ────────────────────────────────────────

    @Test fun `dagar med avklarade doser eller måendeloggar får en punkt, inte orörda planerade doser`() {
        val doses = listOf(
            dose("a", Slot.MORNING, DoseStatus.TAKEN, date = "2026-10-01"),
            dose("b", Slot.MORNING, DoseStatus.SKIPPED, date = "2026-10-02"),
            dose("c", Slot.MORNING, date = "2026-10-03"),
        )
        val screenings = listOf(Screening("s", day("2026-10-04"), occasion = Occasion.LUNCH))
        assertEquals(setOf(day("2026-10-01"), day("2026-10-02"), day("2026-10-04")), datesWithEntries(doses, screenings))
    }

    // ── Tagningstid vid avbockning (MED-14) ──────────────────────────────────

    @Test fun `idag bockas av nu, en tidigare dag på dosens planerade klockslag`() {
        val now = at("2026-10-04T10:30")
        assertEquals(now, dose("a", Slot.EVENING).checkOffTime(now, STOCKHOLM, reminders = ReminderSettings()))
        assertEquals(at("2026-10-02T19:00"), dose("b", Slot.EVENING, date = "2026-10-02").checkOffTime(now, STOCKHOLM, reminders = ReminderSettings()))
        assertEquals(at("2026-10-02T08:15"), dose("c", Slot.MORNING, date = "2026-10-02", time = LocalTime(8, 15)).checkOffTime(now, STOCKHOLM, reminders = ReminderSettings()))
        assertEquals(now, Dose("d", date = null).checkOffTime(now, STOCKHOLM, reminders = ReminderSettings()))
    }

    @Test fun `en planerad receptdos en tidigare dag bockas av på inställningens klockslag (NOT-18)`() {
        val now = at("2026-10-04T10:30")
        val recipe = dose("a", Slot.NIGHT, date = "2026-10-02", time = LocalTime(22, 0)).copy(prescriptionId = "r1")
        assertEquals(at("2026-10-02T23:30"), recipe.checkOffTime(now, STOCKHOLM, OWN_TIMES))
        assertEquals(at("2026-10-02T22:00"), recipe.checkOffTime(now, STOCKHOLM, ReminderSettings()))
        assertEquals(now, recipe.copy(date = today).checkOffTime(now, STOCKHOLM, OWN_TIMES), "idag: nu")
    }
}
