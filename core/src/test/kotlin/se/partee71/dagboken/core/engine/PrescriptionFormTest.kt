package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import org.junit.Test
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule

/** Receptformulärets val: upprepningen och perioden som ett val var, och Förläng (REC-2…REC-4, REC-7, MEDF-5). */
class PrescriptionFormTest {

    // ── Upprepning ────────────────────────────────────────────────────────────

    @Test fun `varje lagrad upprepning visas som ett av tre val`() {
        assertEquals(RepeatChoice.EVERY_DAY, schedule(Repeat.DAILY).choice())
        for (repeat in listOf(Repeat.WEEKDAYS, Repeat.WEEKENDS, Repeat.CUSTOM)) assertEquals(RepeatChoice.WEEKDAYS, schedule(repeat).choice(), repeat.name)
        assertEquals(RepeatChoice.INTERVAL, schedule(Repeat.INTERVAL).choice())
    }

    @Test fun `vardagar och helger visas som sina dagar, anpassad som de lagrade`() {
        assertEquals(WORKWEEK, schedule(Repeat.WEEKDAYS).chosenDays())
        assertEquals(setOf(SATURDAY, SUNDAY), schedule(Repeat.WEEKENDS).chosenDays())
        assertEquals(setOf(MONDAY, FRIDAY), schedule(Repeat.CUSTOM, setOf(MONDAY, FRIDAY)).chosenDays())
    }

    @Test fun `mån–fre sparas som vardagar, lör–sön som helger och annat som anpassad`() {
        val base = schedule(Repeat.CUSTOM)
        assertEquals(Schedule.Repeating(Repeat.WEEKDAYS, WORKWEEK), base.withDays(WORKWEEK))
        assertEquals(Schedule.Repeating(Repeat.WEEKENDS, setOf(SATURDAY, SUNDAY)), base.withDays(setOf(SUNDAY, SATURDAY)))
        assertEquals(Schedule.Repeating(Repeat.CUSTOM, setOf(MONDAY, WEDNESDAY, FRIDAY)), base.withDays(setOf(MONDAY, WEDNESDAY, FRIDAY)))
        assertEquals(Schedule.Repeating(Repeat.CUSTOM, emptySet()), base.withDays(emptySet()))
        assertEquals(Repeat.CUSTOM, base.withDays(WORKWEEK + SATURDAY).repeat)
    }

    @Test fun `byte av val bevarar dagar och intervall, och intervallet är minst varannan dag`() {
        val custom = schedule(Repeat.CUSTOM, setOf(MONDAY, FRIDAY), intervalDays = 3)
        val daily = custom.withChoice(RepeatChoice.EVERY_DAY)
        assertEquals(Schedule.Repeating(Repeat.DAILY, setOf(MONDAY, FRIDAY), 3), daily)
        assertEquals(custom, daily.withChoice(RepeatChoice.WEEKDAYS))
        assertEquals(Schedule.Repeating(Repeat.INTERVAL, setOf(MONDAY, FRIDAY), 3), daily.withChoice(RepeatChoice.INTERVAL))
        assertEquals(2, schedule(Repeat.DAILY, intervalDays = 1).withChoice(RepeatChoice.INTERVAL).intervalDays)
        assertEquals(schedule(Repeat.WEEKENDS), schedule(Repeat.WEEKENDS).withChoice(RepeatChoice.WEEKDAYS))
        assertEquals(Repeat.WEEKDAYS, schedule(Repeat.DAILY, WORKWEEK).withChoice(RepeatChoice.WEEKDAYS).repeat)
    }

    @Test fun `intervallets första dosdagar räknas från start, startdagen inräknad`() {
        assertEquals(listOf(day("2026-10-06"), day("2026-10-09"), day("2026-10-12")), intervalDaysFrom(day("2026-10-06"), 3))
        assertEquals(listOf(day("2026-10-30"), day("2026-11-01")), intervalDaysFrom(day("2026-10-30"), 2, count = 2))
    }

    // ── Period ────────────────────────────────────────────────────────────────

    @Test fun `periodens val, längd och slut`() {
        val tenDays = Period(day("2026-10-06"), day("2026-10-15"))
        assertEquals(PeriodChoice.END_DATE, tenDays.choice())
        assertEquals(PeriodChoice.UNTIL_FURTHER_NOTICE, Period(day("2026-10-06")).choice())
        assertEquals(10, tenDays.lengthDays())
        assertEquals(1, Period(day("2026-10-06"), day("2026-10-06")).lengthDays())
        assertNull(Period(day("2026-10-06")).lengthDays())
        assertNull(Period(end = day("2026-10-06")).lengthDays())
        assertEquals(day("2026-10-20"), tenDays.withLength(15).end)
        assertEquals(day("2026-10-06"), tenDays.withLength(0).end, "minst en dag")
        assertEquals(Period(end = day("2026-10-06")), Period(end = day("2026-10-06")).withLength(5), "utan start oförändrad")
    }

    @Test fun `byte av periodval - tills vidare tar bort slutet, längd och slut ger 14 dagar och ändrar inte varandra`() {
        val tenDays = Period(day("2026-10-06"), day("2026-10-15"))
        assertEquals(Period(day("2026-10-06")), tenDays.withChoice(PeriodChoice.UNTIL_FURTHER_NOTICE))
        assertEquals(tenDays, tenDays.withChoice(PeriodChoice.LENGTH))
        assertEquals(tenDays, tenDays.withChoice(PeriodChoice.END_DATE))
        assertEquals(Period(day("2026-10-06"), day("2026-10-19")), Period(day("2026-10-06")).withChoice(PeriodChoice.LENGTH))
        assertEquals(Period(day("2026-10-06"), day("2026-10-19")), Period(day("2026-10-06")).withChoice(PeriodChoice.END_DATE))
    }

    @Test fun `ny start - längden står kvar under Längd, slutet under Tom`() {
        val tenDays = Period(day("2026-10-06"), day("2026-10-15"))
        assertEquals(Period(day("2026-10-10"), day("2026-10-19")), tenDays.withStart(day("2026-10-10"), keepLength = true))
        assertEquals(Period(day("2026-10-10"), day("2026-10-15")), tenDays.withStart(day("2026-10-10"), keepLength = false))
        assertEquals(Period(day("2026-10-10")), Period(day("2026-10-06")).withStart(day("2026-10-10"), keepLength = true))
    }

    @Test fun `formuläret har alltid ett startdatum - det lagrade, intervallets ankare eller idag`() {
        val today = day("2026-10-06")
        assertEquals(day("2026-01-01"), prescription(start = "2026-01-01").withFormStart(today).period.start)
        assertEquals(day("2025-03-02"), prescription(start = null, createdAt = at("2025-03-02T00:00")).withFormStart(today).period.start)
        assertEquals(today, prescription(start = null).withFormStart(today).period.start)
    }

    // ── Förläng och aktivera ──────────────────────────────────────────────────

    @Test fun `förläng ger en lika lång period från idag, aktivt, och flyttar höjningarna med`() {
        val ended = prescription(
            start = "2026-09-14",
            end = "2026-09-21",
            active = false,
            boosts = listOf(boost("2026-09-16", "2026-09-18", "250"), boost("2026-09-19", null, "100"), boost(null, null, "5")),
        )
        val extended = ended.extendedFrom(day("2026-10-06"))
        assertEquals(Period(day("2026-10-06"), day("2026-10-13")), extended.period)
        assertEquals(true, extended.active)
        assertEquals(listOf(day("2026-10-08") to day("2026-10-10"), day("2026-10-11") to null, null to null), extended.boosts.map { it.start to it.end })
        assertEquals(ended.boosts.map { it.id to it.dose }, extended.boosts.map { it.id to it.dose })
        assertNull(extended.validate(), "en förlängning går att spara direkt")
    }

    @Test fun `förläng utan känd längd ger 14 dagar`() {
        val noStart = prescription(start = null, end = "2026-09-21", active = false)
        assertEquals(Period(day("2026-10-06"), day("2026-10-19")), noStart.extendedFrom(day("2026-10-06")).period)
    }
}
