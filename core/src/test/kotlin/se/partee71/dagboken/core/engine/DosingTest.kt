package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.junit.Test
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule

/**
 * Receptets kalender och dos: upprepning (REC-2…REC-4), period (REC-7, REC-8), höjningar (REC-9,
 * REC-11) och dagens totala dos (REC-12). Port av 3.x `DosperiodTest`, `UpprepningTest` (mönstren;
 * synonymerna testas i konverteraren) och `EnsureTodayEntriesUseCaseTest.shouldTakeToday`.
 */
class DosingTest {

    /** Måndag 2026-05-04 och veckan därpå. */
    private val monday = day("2026-05-04")
    private fun week() = (0..6).map { monday.plus(it, DateTimeUnit.DAY) }
    private fun Schedule.pattern(anchor: LocalDate? = null) = week().map { appliesOn(it, anchor) }

    // ── Upprepning ────────────────────────────────────────────────────────────

    @Test fun `dagligen ger dos varje dag`() {
        assertEquals(List(7) { true }, schedule(Repeat.DAILY).pattern())
    }

    @Test fun `vardagar ger måndag till fredag, helger lördag och söndag`() {
        assertEquals(List(5) { true } + List(2) { false }, schedule(Repeat.WEEKDAYS).pattern())
        assertEquals(List(5) { false } + List(2) { true }, schedule(Repeat.WEEKENDS).pattern())
    }

    @Test fun `anpassad ger bara valda veckodagar (REC-3)`() {
        val s = schedule(Repeat.CUSTOM, days = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY))
        assertEquals(listOf(true, false, false, false, false, false, true), s.pattern())
        assertEquals(List(7) { false }, schedule(Repeat.CUSTOM).pattern())
    }

    @Test fun `intervall räknas från ankaret, dag 0 inräknad (REC-4)`() {
        val s = schedule(Repeat.INTERVAL, intervalDays = 3)
        assertEquals(listOf(true, false, false, true, false, false, true), s.pattern(anchor = monday))
        assertEquals(listOf(false, true, false, false, true, false, false), s.pattern(anchor = monday.plus(1, DateTimeUnit.DAY)))
    }

    @Test fun `intervall på en dag eller mindre är dagligen, utan ankare är dagen dag 0`() {
        assertEquals(List(7) { true }, schedule(Repeat.INTERVAL, intervalDays = 1).pattern(monday))
        assertEquals(List(7) { true }, schedule(Repeat.INTERVAL, intervalDays = 0).pattern(monday))
        assertEquals(List(7) { true }, schedule(Repeat.INTERVAL, intervalDays = 2).pattern(anchor = null))
    }

    @Test fun `intervall före ankaret följer samma rytm bakåt`() {
        val s = schedule(Repeat.INTERVAL, intervalDays = 2)
        assertTrue(s.appliesOn(day("2026-05-02"), anchor = monday))
        assertFalse(s.appliesOn(day("2026-05-03"), anchor = monday))
    }

    @Test fun `intervall över skottdagen räknar 29 februari som en dag`() {
        val s = schedule(Repeat.INTERVAL, intervalDays = 2)
        val anchor = day("2028-02-27")
        assertTrue(s.appliesOn(day("2028-02-29"), anchor))
        assertFalse(s.appliesOn(day("2028-03-01"), anchor))
        assertTrue(s.appliesOn(day("2028-03-02"), anchor))
    }

    @Test fun `okänd upprepning ger aldrig en dos`() {
        assertEquals(List(7) { false }, Schedule.Unknown("monthly").pattern(monday))
        assertFalse(prescription(schedule = Schedule.Unknown()).appliesOn(monday))
    }

    @Test fun `intervallet räknas från periodens start före skapandedagen (REC-4)`() {
        val p = prescription(schedule = schedule(Repeat.INTERVAL), start = "2026-05-02", createdAt = at("2026-05-03T09:00"))
        assertEquals(day("2026-05-02"), p.intervalAnchor())
        assertTrue(p.appliesOn(monday))
    }

    @Test fun `ett migrerat recept får samma dosdagar oavsett enhetens tidszon`() {
        // Konverteraren skriver 3.x `skapad` som midnatt Europe/Stockholm – i UTC och New York är det
        // fortfarande dagen före. Ankaret ska ändå vara den svenska dagen på alla enheter.
        val p = prescription(schedule = schedule(Repeat.INTERVAL), start = null, createdAt = at("2026-05-04T00:00"))
        assertEquals(monday, p.intervalAnchor())
        for (zone in listOf("UTC", "America/New_York", "Europe/Stockholm").map(TimeZone::of)) {
            assertEquals(listOf(true, false, true), (0..2).map { p.plannedDoses(monday.plus(it, DateTimeUnit.DAY), zone).isNotEmpty() }, zone.id)
        }
    }

    @Test fun `utan periodstart räknas intervallet från skapandedagen i svensk tid, även strax efter midnatt`() {
        // 00:30 svensk tid den 3 maj är fortfarande 2 maj i UTC – dagen ska vara den svenska.
        val p = prescription(schedule = schedule(Repeat.INTERVAL), start = null, createdAt = at("2026-05-03T00:30"))
        assertEquals(day("2026-05-03"), p.intervalAnchor())
        assertFalse(p.appliesOn(monday))
        assertTrue(p.appliesOn(monday.plus(1, DateTimeUnit.DAY)))
        assertNull(prescription(start = null).intervalAnchor())
    }

    // ── Period (REC-7, REC-8) ─────────────────────────────────────────────────

    @Test fun `perioden gäller första och sista dagen men inte utanför`() {
        val period = Period(day("2026-05-01"), day("2026-05-10"))
        assertTrue(period.covers(day("2026-05-01")))
        assertTrue(period.covers(day("2026-05-10")))
        assertFalse(period.covers(day("2026-04-30")))
        assertFalse(period.covers(day("2026-05-11")))
    }

    @Test fun `utan start finns ingen bakre gräns, utan slut gäller den tills vidare`() {
        assertTrue(Period(end = day("2026-05-10")).covers(day("1999-01-01")))
        assertTrue(Period(start = day("2020-01-01")).covers(day("2030-01-01")))
        assertTrue(prescription(start = null, createdAt = at("2026-08-01T00:00")).appliesOn(day("2026-01-15")))
    }

    @Test fun `en period på en dag och en på skottdagen`() {
        assertTrue(Period(day("2028-02-29"), day("2028-02-29")).covers(day("2028-02-29")))
        assertFalse(Period(day("2028-02-29"), day("2028-02-29")).covers(day("2028-03-01")))
    }

    @Test fun `periodens sista dosdag är slutdagen eller närmaste dosdag före den`() {
        // Onsdag 2026-05-13 ger ingen helgdos – sista dosen tas söndag 10 maj.
        assertEquals(day("2026-05-10"), prescription(schedule = schedule(Repeat.WEEKENDS), end = "2026-05-13").lastDoseDay())
        assertEquals(day("2026-05-13"), prescription(end = "2026-05-13").lastDoseDay())
        assertEquals(day("2026-05-11"), prescription(schedule = schedule(Repeat.INTERVAL, intervalDays = 10), start = "2026-05-01", end = "2026-05-13").lastDoseDay())
        assertNull(prescription(schedule = schedule(Repeat.WEEKENDS), start = "2026-05-11", end = "2026-05-13").lastDoseDay())
        assertNull(prescription(schedule = Schedule.Unknown(), end = "2026-05-13").lastDoseDay())
        assertNull(prescription(end = null).lastDoseDay())
    }

    @Test fun `sista dosdagen räknas i konstant tid även med mycket långa intervall`() {
        // Utan periodstart: ankaret är skapandedagen. 100 000 dagars intervall → bara ankardagen ger dos.
        val huge = prescription(schedule = schedule(Repeat.INTERVAL, intervalDays = 100_000), start = null, end = "2026-05-13", createdAt = at("2026-01-01T00:00"))
        assertEquals(day("2026-01-01"), huge.lastDoseDay())
        assertEquals(null, huge.nextDoseDayAfter(day("2026-01-01")))
    }

    @Test fun `period kortare än cykeln, och ankaret efter periodens slut`() {
        val short = prescription(schedule = schedule(Repeat.INTERVAL, intervalDays = 7), start = "2026-05-10", end = "2026-05-13")
        assertEquals(day("2026-05-10"), short.lastDoseDay())
        assertNull(short.nextDoseDayAfter(day("2026-05-10")))
        // Utan start, skapad 1 juni men slut 13 maj: rytmen bakåt från ankaret ger 12 maj.
        val anchorAfter = prescription(schedule = schedule(Repeat.INTERVAL, intervalDays = 10), start = null, end = "2026-05-13", createdAt = at("2026-06-01T00:00"))
        assertEquals(day("2026-05-12"), anchorAfter.lastDoseDay())
        assertTrue(anchorAfter.appliesOn(day("2026-05-12")))
    }

    @Test fun `nästa dosdag efter en dag`() {
        val weekends = prescription(schedule = schedule(Repeat.WEEKENDS))
        assertEquals(day("2026-05-09"), weekends.nextDoseDayAfter(monday))
        assertEquals(day("2026-05-10"), weekends.nextDoseDayAfter(day("2026-05-09")))
        assertNull(prescription(schedule = schedule(Repeat.CUSTOM)).nextDoseDayAfter(monday))
        assertEquals(day("2026-05-07"), prescription(schedule = schedule(Repeat.INTERVAL, intervalDays = 3), start = "2026-05-04").nextDoseDayAfter(monday))
    }

    @Test fun `receptet har passerats först dagen efter sista dagen (REC-8)`() {
        val p = prescription(end = "2026-05-10")
        assertFalse(p.hasExpiredOn(day("2026-05-10")))
        assertTrue(p.hasExpiredOn(day("2026-05-11")))
        assertFalse(prescription(end = null).hasExpiredOn(day("2099-12-31")))
    }

    // ── Höjningar och dagens dos (REC-9, REC-11, REC-12) ──────────────────────

    @Test fun `grunddosen gäller när ingen höjning täcker dagen`() {
        val p = prescription(boosts = listOf(boost("2026-02-01", "2026-02-07", "250")))
        assertNull(p.boostFor(day("2026-01-20")))
        assertEquals("500", p.doseFor(day("2026-01-20")))
    }

    @Test fun `höjningen läggs till grunddosen första och sista dagen`() {
        val p = prescription(boosts = listOf(boost("2026-02-01", "2026-02-07", "250")))
        assertEquals("750", p.doseFor(day("2026-02-01")))
        assertEquals("750", p.doseFor(day("2026-02-07")))
        assertEquals("500", p.doseFor(day("2026-02-08")))
    }

    @Test fun `höjning utan slut gäller till periodens slut, eller tills vidare`() {
        val open = boost("2026-02-01", null, "250")
        assertEquals("750", prescription(boosts = listOf(open)).doseFor(day("2026-06-01")))
        val bounded = prescription(end = "2026-02-10", boosts = listOf(open))
        assertEquals(day("2026-02-10"), bounded.boostEnd(open))
        assertNull(bounded.boostFor(day("2026-02-11")))
    }

    @Test fun `decimaler summeras och skrivs med komma, heltal utan decimaler`() {
        assertEquals("1,5", prescription(dose = "1", boosts = listOf(boost("2026-02-01", null, "0,5"))).doseFor(day("2026-02-01")))
        assertEquals("2", prescription(dose = "1,5", boosts = listOf(boost("2026-02-01", null, "0.5"))).doseFor(day("2026-02-01")))
    }

    @Test fun `grunddos som inte är ett tal lämnas oförändrad`() {
        assertEquals("en tablett", prescription(dose = "en tablett", boosts = listOf(boost("2026-02-01", null, "1"))).doseFor(day("2026-02-01")))
        assertEquals("500", prescription(boosts = listOf(boost("2026-02-01", null, "lite"))).doseFor(day("2026-02-01")))
    }

    @Test fun `överlappande höjningar i gammal data - den senast påbörjade vinner (REC-11)`() {
        val p = prescription(boosts = listOf(boost("2026-02-10", "2026-02-12", "125"), boost("2026-02-01", "2026-02-28", "250")))
        assertEquals("625", p.doseFor(day("2026-02-11")))
        assertEquals("750", p.doseFor(day("2026-02-05")))
        assertEquals("750", p.doseFor(day("2026-02-20")))
    }

    @Test fun `höjning utan start räknas inte`() {
        assertEquals("500", prescription(boosts = listOf(boost(null, null, "999"))).doseFor(day("2026-02-11")))
    }

    @Test fun `höjning över skottdagen`() {
        val p = prescription(boosts = listOf(boost("2028-02-28", "2028-02-29", "250")))
        assertEquals("750", p.doseFor(day("2028-02-29")))
        assertEquals("500", p.doseFor(day("2028-03-01")))
    }

    // ── parseDose / formatDose ────────────────────────────────────────────────

    @Test fun `parseDose godtar komma, punkt och blanktecken runt om`() {
        assertEquals(0.5, parseDose("0,5"))
        assertEquals(0.5, parseDose("0.5"))
        assertEquals(500.0, parseDose(" 500 "))
    }

    @Test fun `parseDose ger null för text och för tal som inte är ändliga`() {
        for (text in listOf("en tablett", "", "  ", "NaN", "Infinity", null)) assertNull(parseDose(text), text)
    }

    @Test fun `parseDose är strikt - inga exponenter, hex, suffix eller tecken`() {
        for (text in listOf("5d", "2f", "1e3", "0x10", "1,5 mg", "-1", "+1", "1,2,3", "1.2.3", ",", ".")) assertNull(parseDose(text), text)
        assertEquals(5.0, parseDose("5,"))
        assertEquals(0.5, parseDose(",5"))
        assertEquals(12.25, parseDose("12.25"))
    }

    @Test fun `för långa siffersträngar ger null och formatDose kastar aldrig`() {
        assertNull(parseDose("9".repeat(400)))
        assertEquals("Infinity", formatDose(Double.POSITIVE_INFINITY))
        assertEquals("NaN", formatDose(Double.NaN))
        assertEquals("1" + "0".repeat(300), formatDose(1e300))
        // En summa som skulle bli oändlig ger grunddosen oförändrad.
        val p = prescription(dose = "1" + "0".repeat(308), boosts = listOf(boost("2026-02-01", null, "1" + "0".repeat(308))))
        assertEquals(p.dose, p.doseFor(day("2026-02-01")))
    }

    @Test fun `formatDose stryker avslutande nollor och flyttalsbrus`() {
        assertEquals("2", formatDose(2.0))
        assertEquals("1,5", formatDose(1.5))
        assertEquals("0,25", formatDose(0.25))
        assertEquals("0,3", formatDose(0.1 + 0.2))
        assertEquals("1250", formatDose(1250.0))
    }
}
