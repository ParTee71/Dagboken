package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import org.junit.Test
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Slot

/**
 * Vid behov-doser: kylperiod (FAV-4), dagsgräns (FAV-5), efterhandsloggning (MED-16), dosen som
 * loggas (MED-11), receptets extrados (FAV-11) och läget innan man trycker (FAV-13). Port av 3.x `CheckCooldownUseCaseTest`,
 * `CheckDailyLimitUseCaseTest`, `LogVidBehovDosUseCaseTest` (beräkningen) och `ReceptVidBehovTest`.
 */
class CooldownTest {

    // ── Kylperiod (FAV-4) ─────────────────────────────────────────────────────

    @Test fun `ingen kylperiod när den är 0 eller ingen dos tagits`() {
        assertNull(prn(minHoursBetween = 0).cooldownRemaining(listOf(taken("2026-05-06T09:00")), at("2026-05-06T10:00")))
        assertNull(prn().cooldownRemaining(emptyList(), at("2026-05-06T10:00")))
    }

    @Test fun `kvarvarande tid räknas från den senaste tagna dosen`() {
        val doses = listOf(taken("2026-05-06T06:00"), taken("2026-05-06T08:00"))
        assertEquals(2.hours, prn().cooldownRemaining(doses, at("2026-05-06T10:00")))
        assertEquals(30.minutes, prn().cooldownRemaining(doses, at("2026-05-06T11:30")))
    }

    @Test fun `exakt utgången eller passerad kylperiod spärrar inte`() {
        val doses = listOf(taken("2026-05-06T08:00"))
        assertNull(prn().cooldownRemaining(doses, at("2026-05-06T12:00")))
        assertNull(prn().cooldownRemaining(doses, at("2026-05-06T14:00")))
    }

    @Test fun `en dos loggad senare spärrar inte en efterhandsloggning tidigare (MED-16)`() {
        val doses = listOf(taken("2026-05-06T02:00"), taken("2026-05-06T20:00"))
        assertEquals(1.hours, prn().cooldownRemaining(doses, at("2026-05-06T05:00")))
    }

    @Test fun `kylperioden löper över midnatt`() {
        assertEquals(2.hours, prn().cooldownRemaining(listOf(taken("2026-05-06T23:00")), at("2026-05-07T01:00")))
    }

    @Test fun `kylperioden räknar verkliga timmar över sommartidsbytet`() {
        // 29 mars: 01:30 → 03:30 lokal tid är bara en verklig timme.
        assertEquals(3.hours, prn().cooldownRemaining(listOf(taken("2026-03-29T01:30")), at("2026-03-29T03:30")))
    }

    @Test fun `bara tagna doser av samma medicin räknas - namn oavsett skiftläge eller prnId`() {
        val now = at("2026-05-06T10:00")
        assertNull(prn().cooldownRemaining(listOf(taken("2026-05-06T09:00", name = "Ipren")), now))
        assertNull(prn().cooldownRemaining(listOf(taken("2026-05-06T09:00", status = DoseStatus.SKIPPED)), now))
        assertNull(prn().cooldownRemaining(listOf(taken("2026-05-06T09:00", status = DoseStatus.PLANNED)), now))
        assertEquals(3.hours, prn().cooldownRemaining(listOf(taken("2026-05-06T09:00", name = "PARACETAMOL")), now))
        assertEquals(3.hours, prn().cooldownRemaining(listOf(taken("2026-05-06T09:00", name = "Alvedon", prnId = "p1")), now))
    }

    @Test fun `tagningstiden går före createdAt, som saknas kan inte spärra`() {
        val dose = taken("2026-05-06T09:00")
        assertEquals(at("2026-05-06T09:00"), dose.takenMoment())
        assertEquals(at("2026-05-06T07:00"), dose.copy(takenAt = null, createdAt = at("2026-05-06T07:00")).takenMoment())
        assertNull(prn().cooldownRemaining(listOf(dose.copy(takenAt = null, createdAt = null)), at("2026-05-06T10:00")))
    }

    // ── Dagsgräns (FAV-5) ─────────────────────────────────────────────────────

    @Test fun `dagsgränsen 0 är obegränsad, annars nås den vid gränsen`() {
        val two = listOf(taken("2026-05-06T08:00"), taken("2026-05-06T14:00"))
        assertFalse(prn(maxPerDay = 0).dailyLimitReached(two, day("2026-05-06")))
        assertFalse(prn(maxPerDay = 3).dailyLimitReached(two, day("2026-05-06")))
        assertTrue(prn(maxPerDay = 2).dailyLimitReached(two, day("2026-05-06")))
        assertTrue(prn(maxPerDay = 1).dailyLimitReached(two, day("2026-05-06")))
        assertFalse(prn(maxPerDay = 2).dailyLimitReached(two, day("2026-05-07")))
    }

    // ── Kontrollen före loggning ──────────────────────────────────────────────

    @Test fun `dagsgränsen spärrar alltid, kylperioden går att förbigå`() {
        val doses = listOf(taken("2026-05-06T08:00"))
        val now = at("2026-05-06T09:00")
        assertEquals(PrnCheck.DailyLimitReached, prn(maxPerDay = 1).checkDose(doses, now, STOCKHOLM, force = true))
        assertEquals(PrnCheck.Cooldown(3.hours), prn().checkDose(doses, now, STOCKHOLM))
        assertEquals(PrnCheck.Allowed, prn().checkDose(doses, now, STOCKHOLM, force = true))
        assertEquals(PrnCheck.Allowed, prn().checkDose(doses, at("2026-05-06T13:00"), STOCKHOLM))
    }

    @Test fun `dagsgränsen gäller den lokala dagen för den valda tidpunkten`() {
        val doses = listOf(taken("2026-05-06T23:30"))
        // 00:15 svensk tid den 7 maj är fortfarande 6 maj i UTC – det är den svenska dagen som räknas.
        assertEquals(PrnCheck.Allowed, prn(minHoursBetween = 0, maxPerDay = 1).checkDose(doses, at("2026-05-07T00:15"), STOCKHOLM))
        assertEquals(PrnCheck.DailyLimitReached, prn(minHoursBetween = 0, maxPerDay = 1).checkDose(doses, at("2026-05-06T23:45"), STOCKHOLM))
    }

    // ── Läget innan man trycker (FAV-13) ──────────────────────────────────────

    @Test fun `läget är Ok utan kylperiod, utan tidigare dos och när kylperioden gått ut`() {
        val now = at("2026-05-06T10:00")
        assertEquals(PrnAvailability.Ok, prn(minHoursBetween = 0).availabilityAt(listOf(taken("2026-05-06T09:59")), now, STOCKHOLM))
        assertEquals(PrnAvailability.Ok, prn().availabilityAt(emptyList(), now, STOCKHOLM))
        assertEquals(PrnAvailability.Ok, prn().availabilityAt(listOf(taken("2026-05-06T06:00")), now, STOCKHOLM), "exakt utgången")
    }

    @Test fun `under kylperioden är nästa tillåtna tidpunkt den senaste dosen plus kylperioden`() {
        val doses = listOf(taken("2026-05-06T06:00"), taken("2026-05-06T08:10"))
        assertEquals(PrnAvailability.Cooldown(at("2026-05-06T12:10")), prn().availabilityAt(doses, at("2026-05-06T10:00"), STOCKHOLM))
        assertEquals(PrnAvailability.Cooldown(at("2026-05-06T12:10")), prn().availabilityAt(doses, at("2026-05-06T12:09"), STOCKHOLM), "samma tid hela perioden")
        assertEquals(PrnAvailability.Ok, prn().availabilityAt(doses, at("2026-05-06T12:10"), STOCKHOLM), "markeringen försvinner när tiden passerats")
    }

    @Test fun `nästa tillåtna tidpunkt kan ligga efter midnatt`() {
        val next = assertIs<PrnAvailability.Cooldown>(prn().availabilityAt(listOf(taken("2026-05-06T21:10")), at("2026-05-06T22:00"), STOCKHOLM)).nextAllowed
        assertEquals(at("2026-05-07T01:10"), next)
        assertEquals(day("2026-05-07"), next.toLocalDateTime(STOCKHOLM).date, "dagen efter – i morgon")
    }

    @Test fun `nästa tillåtna tidpunkt räknas i verkliga timmar över sommartidsbytet`() {
        // 29 mars: 01:30 + 4 verkliga timmar = 06:30 lokal tid (klockan ställs fram 02:00 → 03:00); 27 oktober tillbaka: 01:30 + 4 h = 04:30.
        assertEquals(PrnAvailability.Cooldown(at("2026-03-29T06:30")), prn().availabilityAt(listOf(taken("2026-03-29T01:30")), at("2026-03-29T03:30"), STOCKHOLM))
        val autumn = assertIs<PrnAvailability.Cooldown>(prn().availabilityAt(listOf(taken("2026-10-25T01:30")), at("2026-10-25T02:30"), STOCKHOLM)).nextAllowed
        assertEquals(LocalTime(4, 30), autumn.toLocalDateTime(STOCKHOLM).time)
    }

    @Test fun `dagsgränsen nådd ger antalet tagna idag och gränsen`() {
        val doses = listOf(taken("2026-05-05T22:00"), taken("2026-05-06T07:00"), taken("2026-05-06T08:00"), taken("2026-05-06T09:00", name = "Ipren"))
        val now = at("2026-05-06T20:00")
        assertEquals(PrnAvailability.DailyLimit(taken = 2, limit = 2), prn(maxPerDay = 2).availabilityAt(doses, now, STOCKHOLM))
        assertEquals(PrnAvailability.Ok, prn(maxPerDay = 3).availabilityAt(doses, now, STOCKHOLM))
        assertEquals(PrnAvailability.Ok, prn(maxPerDay = 2).availabilityAt(doses, at("2026-05-07T00:05"), STOCKHOLM), "en ny dag, kylperioden ute")
    }

    @Test fun `dagsgränsen går före kylperioden, som i trycket`() {
        val doses = listOf(taken("2026-05-06T08:00"), taken("2026-05-06T09:00"))
        val now = at("2026-05-06T10:00")
        assertEquals(PrnCheck.DailyLimitReached, prn(maxPerDay = 2).checkDose(doses, now, STOCKHOLM))
        assertEquals(PrnAvailability.DailyLimit(taken = 2, limit = 2), prn(maxPerDay = 2).availabilityAt(doses, now, STOCKHOLM))
        assertEquals(PrnAvailability.Cooldown(at("2026-05-06T13:00")), prn(maxPerDay = 3).availabilityAt(doses, now, STOCKHOLM))
    }

    // ── Dosen som loggas (MED-11, FAV-11) ─────────────────────────────────────

    @Test fun `vid behov-dosen är tagen nu, med medicinens dos och anteckning`() {
        val dose = prn(note = "Ta med mat").takenDose("ny-1", at("2026-05-07T00:15:42"), STOCKHOLM)
        assertEquals("ny-1", dose.id)
        assertEquals(day("2026-05-07"), dose.date)
        assertEquals(LocalTime(0, 15), dose.plannedTime)
        assertEquals(at("2026-05-07T00:15:42"), dose.takenAt)
        assertEquals(DoseStatus.TAKEN, dose.status)
        assertEquals(Slot.AS_NEEDED, dose.slot)
        assertEquals("p1", dose.prnId)
        assertNull(dose.prescriptionId)
        assertEquals(listOf("Paracetamol", "500", "mg", "Ta med mat"), listOf(dose.name, dose.dose, dose.unit, dose.note))
        assertNull(prn(note = "").takenDose("ny-2", at("2026-05-07T08:00"), STOCKHOLM).note)
    }

    @Test fun `receptets extrados är vid behov utan receptkoppling, med dagens höjda dos (FAV-11)`() {
        val p = prescription(note = "Tas med mat", boosts = listOf(boost("2026-02-01", "2026-02-07", "250")))
        val dose = p.extraDose("ny-3", at("2026-02-03T12:00"), STOCKHOLM)
        assertEquals(listOf("750", "mg", "Metformin", "Tas med mat"), listOf(dose.dose, dose.unit, dose.name, dose.note))
        assertEquals(Slot.AS_NEEDED, dose.slot)
        assertNull(dose.prescriptionId)
        assertNull(dose.prnId)
        assertEquals("500", p.extraDose("ny-4", at("2026-02-08T12:00"), STOCKHOLM).dose)
    }

    @Test fun `vid behov-dosen och extradosen får medicinens styrka, som namnet`() {
        assertEquals("500 mg", prn().copy(strength = "500 mg").takenDose("ny-5", at("2026-05-07T08:00"), STOCKHOLM).strength)
        assertEquals("850 mg", prescription().copy(strength = "850 mg").extraDose("ny-6", at("2026-05-07T08:00"), STOCKHOLM).strength)
        assertEquals("", prn().takenDose("ny-7", at("2026-05-07T08:00"), STOCKHOLM).strength)
    }

    @Test fun `ett tomt namn matchar aldrig på namn, bara på prnId`() {
        val blank = prn().copy(name = " ")
        val now = at("2026-05-06T10:00")
        assertNull(blank.cooldownRemaining(listOf(taken("2026-05-06T09:00", name = " "), taken("2026-05-06T09:30", name = "")), now))
        assertEquals(3.hours, blank.cooldownRemaining(listOf(taken("2026-05-06T09:00", name = "", prnId = "p1")), now))
        assertFalse(prn().copy(name = "").dailyLimitReached(listOf(taken("2026-05-06T09:00", name = "")), day("2026-05-06")))
    }
}
