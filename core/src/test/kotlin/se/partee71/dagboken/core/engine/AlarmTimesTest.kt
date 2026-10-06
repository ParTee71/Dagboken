package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlinx.datetime.LocalTime
import org.junit.Test

/** När påminnelserna utlöses (NOT-2, NOT-5, NOT-14). Port av 3.x `AlarmTimeTest`, med sommartid. */
class AlarmTimesTest {

    private fun next(time: String, now: String) = nextDailyAt(LocalTime.parse(time), at(now), STOCKHOLM)

    private fun nextMed(time: String, now: String, lead: Int = MED_LEAD_MINUTES) = nextMedAlarm(LocalTime.parse(time), at(now), STOCKHOLM, lead)

    // ── Dagligt larm (mående, periodslut) ─────────────────────────────────────

    @Test fun `en tid senare idag utlöses idag`() {
        assertEquals(at("2026-06-18T10:00"), next("10:00", now = "2026-06-18T09:00"))
    }

    @Test fun `en passerad tid utlöses i morgon (NOT-5)`() {
        assertEquals(at("2026-06-19T10:00"), next("10:00", now = "2026-06-18T11:00"))
    }

    @Test fun `exakt nu räknas som passerad`() {
        assertEquals(at("2026-06-19T10:00"), next("10:00", now = "2026-06-18T10:00"))
    }

    @Test fun `minuterna följer med`() {
        assertEquals(at("2026-06-18T08:30"), next("08:30", now = "2026-06-18T08:00"))
    }

    @Test fun `över månads- och årsskiftet`() {
        assertEquals(at("2027-01-01T08:00"), next("08:00", now = "2026-12-31T21:00"))
    }

    // ── Medicinlarm 15 min före (NOT-2) ───────────────────────────────────────

    @Test fun `medicinlarmet kommer 15 minuter före tidpunkten`() {
        assertEquals(at("2026-06-18T06:45"), nextMed("07:00", now = "2026-06-18T05:00"))
    }

    @Test fun `passerad påminnelsetid ger morgondagens larm`() {
        assertEquals(at("2026-06-19T07:45"), nextMed("08:00", now = "2026-06-18T08:00"))
        assertEquals(at("2026-06-19T07:45"), nextMed("08:00", now = "2026-06-18T07:45"))
    }

    @Test fun `midnattsvridning - 00_00 minus 15 är 23_45`() {
        assertEquals(LocalTime(23, 45), medAlarmTime(LocalTime(0, 0)))
        assertEquals(LocalTime(23, 55), medAlarmTime(LocalTime(0, 10), leadMinutes = 15))
        assertEquals(at("2026-06-18T23:45"), nextMed("00:00", now = "2026-06-18T23:00"))
        assertEquals(at("2026-06-19T23:45"), nextMed("00:00", now = "2026-06-18T23:50"))
    }

    @Test fun `egen förvarning`() {
        assertEquals(at("2026-06-18T09:30"), nextMed("10:00", now = "2026-06-18T09:00", lead = 30))
        assertEquals(LocalTime(10, 0), medAlarmTime(LocalTime(10, 0), leadMinutes = 0))
    }

    @Test fun `påminnelsen 23_45 gäller nästa dags doser, morgonens samma dags`() {
        assertEquals(day("2026-06-19"), medReminderDate(at("2026-06-18T23:45"), STOCKHOLM))
        assertEquals(day("2026-06-18"), medReminderDate(at("2026-06-18T06:45"), STOCKHOLM))
        // Ett inexakt larm som kommer sent hör fortfarande till dagen.
        assertEquals(day("2026-06-18"), medReminderDate(at("2026-06-18T07:20"), STOCKHOLM))
    }

    // ── Sommartid (Europe/Stockholm: 29 mars 02→03, 25 okt 03→02) ─────────────

    @Test fun `vårens hopp - samma lokala klockslag dagen efter, 23 timmar senare`() {
        assertEquals(at("2026-03-29T07:00"), next("07:00", now = "2026-03-28T08:00"))
        assertEquals(at("2026-03-29T06:45"), nextMed("07:00", now = "2026-03-28T08:00"))
    }

    @Test fun `ett klockslag som inte finns vid vårens hopp blir det första giltiga efter`() {
        assertEquals(at("2026-03-29T03:30"), next("02:30", now = "2026-03-29T00:00"))
    }

    @Test fun `höstens återgång - samma lokala klockslag, 25 timmar senare`() {
        assertEquals(at("2026-10-25T07:00"), next("07:00", now = "2026-10-24T08:00"))
        assertEquals(at("2026-10-25T21:00"), next("21:00", now = "2026-10-25T09:00"))
    }
}
