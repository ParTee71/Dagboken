package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot

/**
 * Dosgenereringen (MED-4, REC-1, REC-6, REC-8, REC-12) och REC-10-diffen vid spara. Port av 3.x
 * `EnsureTodayEntriesUseCaseTest`, `TidpunktLogicTest` (klockslagen) och `MedicinerRepository.
 * syncPendingDoses`/`ensureEntriesForDate`.
 */
class EnsureDosesTest {

    private val today = day("2026-05-06")

    private fun ensure(vararg prescriptions: Prescription, existing: List<Dose> = emptyList(), date: String = "2026-05-06") =
        ensureDoses(prescriptions.toList(), existing, day(date), today, STOCKHOLM)

    // ── Planerade doser (MED-4) ───────────────────────────────────────────────

    @Test fun `en planerad dos per tidpunkt med 3x-id, receptkoppling och standardklockslag`() {
        val doses = prescription(id = "r1", slots = listOf(Slot.MORNING, Slot.EVENING)).plannedDoses(today, STOCKHOLM)
        assertEquals(listOf("recept_r1_2026-05-06_Morgon", "recept_r1_2026-05-06_Kväll"), doses.map { it.id })
        assertTrue(doses.all { it.status == DoseStatus.PLANNED && it.prescriptionId == "r1" && it.date == today && it.name == "Metformin" && it.unit == "mg" })
        assertEquals(listOf(LocalTime(7, 0), LocalTime(19, 0)), doses.map { it.plannedTime })
    }

    @Test fun `varje schemalagd tidpunkt får sitt standardklockslag (REC-6)`() {
        val doses = prescription(slots = Slot.SCHEDULED).plannedDoses(today, STOCKHOLM)
        assertEquals(listOf(7, 10, 12, 15, 19, 22), doses.map { it.plannedTime!!.hour })
    }

    // 3.x genererade `recept_…_Vid behov` om data hade tidpunkten; 4.0 gör det inte (avvikelse, se doseSlots).
    @Test fun `inga tidpunkter ger Morgon, vid behov och dubbletter ger ingen extra dos`() {
        assertEquals(listOf(Slot.MORNING), prescription(slots = emptyList()).plannedDoses(today, STOCKHOLM).map { it.slot })
        assertEquals(listOf(Slot.NIGHT), prescription(slots = listOf(Slot.AS_NEEDED, Slot.NIGHT, Slot.NIGHT)).plannedDoses(today, STOCKHOLM).map { it.slot })
        assertEquals(emptyList(), prescription(slots = listOf(Slot.AS_NEEDED)).plannedDoses(today, STOCKHOLM))
    }

    @Test fun `receptets anteckning ärvs som förval, en tom anteckning gör det inte (REC-1)`() {
        assertEquals("Tas med mat", prescription(note = "Tas med mat").plannedDoses(today, STOCKHOLM).single().note)
        assertNull(prescription(note = " ").plannedDoses(today, STOCKHOLM).single().note)
    }

    @Test fun `dosen är dagens totala dos med höjning (REC-12)`() {
        val p = prescription(dose = "400", start = "2026-05-01", end = "2026-05-14", boosts = listOf(boost("2026-05-01", "2026-05-05", "400")))
        assertEquals("800", p.plannedDoses(day("2026-05-05"), STOCKHOLM).single().dose)
        assertEquals("400", p.plannedDoses(day("2026-05-06"), STOCKHOLM).single().dose)
    }

    @Test fun `två höjningar i följd ger var sin total`() {
        val p = prescription(dose = "400", boosts = listOf(boost("2026-05-01", "2026-05-05", "400"), boost("2026-05-06", "2026-05-10", "200")))
        assertEquals("800", p.plannedDoses(day("2026-05-05"), STOCKHOLM).single().dose)
        assertEquals("600", p.plannedDoses(day("2026-05-06"), STOCKHOLM).single().dose)
    }

    @Test fun `inaktivt, okänd upprepning eller utanför perioden ger inga doser`() {
        assertEquals(emptyList(), prescription(active = false).plannedDoses(today, STOCKHOLM))
        assertEquals(emptyList(), prescription(schedule = Schedule.Unknown("x")).plannedDoses(today, STOCKHOLM))
        assertEquals(emptyList(), prescription(start = "2026-05-01", end = "2026-05-05").plannedDoses(today, STOCKHOLM))
        assertEquals(emptyList(), prescription(schedule = schedule(Repeat.WEEKENDS)).plannedDoses(today, STOCKHOLM))
    }

    @Test fun `ingen dos före det senare av skapandedagen och periodens start (HEM-10)`() {
        val created = prescription(start = null, createdAt = at("2026-08-01T00:00"))
        assertEquals(0, ensure(created, date = "2026-01-15").create.size, "3.x seedade bakåt utan gräns")
        assertEquals(0, ensure(created, date = "2026-07-31").create.size)
        assertEquals(1, ensure(created, date = "2026-08-01").create.size, "skapandedagen i svensk tid")
        val startedBefore = prescription(start = "2026-07-01", createdAt = at("2026-08-01T00:30"))
        assertEquals(0, ensure(startedBefore, date = "2026-07-15").create.size, "skapandedagen är den senare")
        val startsLater = prescription(start = "2026-08-10", createdAt = at("2026-08-01T00:00"))
        assertEquals(0, ensure(startsLater, date = "2026-08-05").create.size)
        assertEquals(1, ensure(prescription(start = null, createdAt = null), date = "2026-01-15").create.size, "ingen uppgift – ingen gräns")
    }

    @Test fun `createdAt följer väggklockan över sommartidsbytena, id och dag ändras inte`() {
        // 29 mars 2026 ställs klockan fram (CET → CEST), 25 oktober tillbaka: samma lokala 07:00/22:00
        // är en timme tidigare i UTC efter vårbytet och en timme senare efter höstbytet.
        val p = prescription(slots = listOf(Slot.MORNING, Slot.NIGHT))
        fun utc(date: String) = p.plannedDoses(day(date), STOCKHOLM).map { it.createdAt.toString() }
        assertEquals(listOf("2026-03-28T06:00:00Z", "2026-03-28T21:00:00Z"), utc("2026-03-28"))
        assertEquals(listOf("2026-03-29T05:00:00Z", "2026-03-29T20:00:00Z"), utc("2026-03-29"))
        assertEquals(listOf("2026-10-24T05:00:00Z", "2026-10-24T20:00:00Z"), utc("2026-10-24"))
        assertEquals(listOf("2026-10-25T06:00:00Z", "2026-10-25T21:00:00Z"), utc("2026-10-25"))
        val spring = p.plannedDoses(day("2026-03-29"), STOCKHOLM)
        assertEquals(listOf("recept_r1_2026-03-29_Morgon", "recept_r1_2026-03-29_Natt"), spring.map { it.id })
        assertEquals(listOf(LocalTime(7, 0), LocalTime(22, 0)), spring.map { it.plannedTime })
    }

    @Test fun `dagen är den lokala vid midnatt och vid nyår`() {
        val doses = prescription(slots = listOf(Slot.MORNING)).plannedDoses(day("2027-01-01"), STOCKHOLM)
        assertEquals("recept_r1_2027-01-01_Morgon", doses.single().id)
        assertEquals("2027-01-01T06:00:00Z", doses.single().createdAt.toString())
    }

    // ── Dosgenereringen för en dag (MED-4, REC-8) ─────────────────────────────

    @Test fun `genereringen är idempotent - andra körningen skapar inget`() {
        val p = prescription(slots = listOf(Slot.MORNING, Slot.LUNCH))
        val first = ensure(p)
        assertEquals(2, first.create.size)
        assertEquals(EnsurePlan(emptyList(), emptyList()), ensure(p, existing = first.create))
        assertEquals(first, ensure(p))
    }

    @Test fun `en tagen eller överhoppad dos återskapas aldrig, en flyttad (nytt id, MED-15) gör det`() {
        val p = prescription(slots = listOf(Slot.MORNING, Slot.LUNCH))
        val (morning, lunch) = p.plannedDoses(today, STOCKHOLM)
        assertEquals(emptyList(), ensure(p, existing = listOf(morning.copy(status = DoseStatus.TAKEN), lunch.copy(status = DoseStatus.SKIPPED))).create)
        val moved = lunch.copy(id = "flyttad-1", date = day("2026-05-07"))
        assertEquals(listOf(lunch), ensure(p, existing = listOf(morning, moved)).create)
    }

    @Test fun `inaktiva recept hoppas över, flera recept genereras tillsammans`() {
        val plan = ensure(prescription(id = "a"), prescription(id = "b", active = false), prescription(id = "c"))
        assertEquals(listOf("recept_a_2026-05-06_Morgon", "recept_c_2026-05-06_Morgon"), plan.create.map { it.id })
    }

    @Test fun `passerade recept avslutas mot dagens datum och ger inga doser (REC-8)`() {
        val expired = prescription(id = "x", start = "2026-04-01", end = "2026-05-05")
        val endsToday = prescription(id = "y", end = "2026-05-06")
        val plan = ensure(expired, endsToday, prescription(id = "z", active = false, end = "2026-01-01"))
        assertEquals(listOf("x"), plan.deactivate)
        assertEquals(listOf("recept_y_2026-05-06_Morgon"), plan.create.map { it.id })
        // Bakåtbläddring till en dag inom perioden väcker inte receptet (som 3.x).
        assertEquals(listOf("x"), ensure(expired, date = "2026-05-01").deactivate)
        assertEquals(emptyList(), ensure(expired, date = "2026-05-01").create)
    }

    // ── REC-10: diffen när receptet sparas ────────────────────────────────────

    private val saved = prescription(id = "r1", slots = listOf(Slot.MORNING, Slot.EVENING))
    private fun dosesOf(vararg dates: String) = dates.flatMap { saved.plannedDoses(day(it), STOCKHOLM) }

    @Test fun `ett oförändrat recept ger en tom diff`() {
        assertTrue(saved.syncDoses(dosesOf("2026-05-06", "2026-05-07"), today, STOCKHOLM).isEmpty)
    }

    @Test fun `ny dos och nytt namn uppdaterar otagna doser från idag`() {
        val existing = dosesOf("2026-05-05", "2026-05-06", "2026-05-07")
        val edited = saved.copy(name = "Metformin XR", dose = "1000")
        val sync = edited.syncDoses(existing, today, STOCKHOLM)
        assertEquals(existing.drop(2), sync.update.map { it.copy(name = "Metformin", dose = "500") })
        assertTrue(sync.update.all { it.name == "Metformin XR" && it.dose == "1000" && it.date!! >= today })
        assertEquals(emptyList(), sync.delete)
        assertEquals(emptyList(), sync.create)
    }

    @Test fun `tagna och överhoppade doser rörs aldrig`() {
        val existing = dosesOf("2026-05-06", "2026-05-07").mapIndexed { i, d -> if (i % 2 == 0) d.copy(status = DoseStatus.TAKEN) else d.copy(status = DoseStatus.SKIPPED) }
        val edited = saved.copy(dose = "1000", slots = listOf(Slot.LUNCH), period = saved.period.copy(end = day("2026-05-05")))
        val sync = edited.syncDoses(existing, today, STOCKHOLM)
        assertEquals(emptyList(), sync.update)
        assertEquals(emptyList(), sync.delete)
        assertEquals(emptyList(), sync.create)
        assertTrue(saved.copy(active = false).syncDoses(existing, today, STOCKHOLM).isEmpty)
    }

    @Test fun `doser utanför perioden, upprepningen eller tidpunkterna tas bort`() {
        val existing = dosesOf("2026-05-06", "2026-05-07", "2026-05-09")
        val shorter = saved.copy(period = saved.period.copy(end = day("2026-05-07")))
        assertEquals(dosesOf("2026-05-09"), shorter.syncDoses(existing, today, STOCKHOLM).delete)

        val weekdays = saved.copy(schedule = schedule(Repeat.WEEKDAYS))
        assertEquals(dosesOf("2026-05-09"), weekdays.syncDoses(existing, today, STOCKHOLM).delete)

        val morningOnly = saved.copy(slots = listOf(Slot.MORNING))
        assertEquals(existing.filter { it.slot == Slot.EVENING }, morningOnly.syncDoses(existing, today, STOCKHOLM).delete)
    }

    @Test fun `gårdagens otagna doser och andra recepts doser lämnas orörda`() {
        val yesterday = dosesOf("2026-05-05")
        val other = prescription(id = "r2").plannedDoses(today, STOCKHOLM)
        val edited = saved.copy(dose = "1000", slots = listOf(Slot.LUNCH))
        val sync = edited.syncDoses(yesterday + other + dosesOf("2026-05-06"), today, STOCKHOLM)
        assertTrue((sync.update + sync.delete).none { it in yesterday || it in other })
    }

    @Test fun `en ny tidpunkt skapas direkt för idag, eller för dagarna till och med through`() {
        val existing = dosesOf("2026-05-06")
        val withLunch = saved.copy(slots = listOf(Slot.MORNING, Slot.LUNCH, Slot.EVENING))
        assertEquals(listOf("recept_r1_2026-05-06_Lunch"), withLunch.syncDoses(existing, today, STOCKHOLM).create.map { it.id })
        assertEquals(
            listOf("recept_r1_2026-05-06_Lunch", "recept_r1_2026-05-07_Morgon", "recept_r1_2026-05-07_Lunch", "recept_r1_2026-05-07_Kväll"),
            withLunch.syncDoses(existing, today, STOCKHOLM, through = day("2026-05-07")).create.map { it.id },
        )
        assertEquals(emptyList(), withLunch.copy(active = false).syncDoses(existing, today, STOCKHOLM).create)
    }

    @Test fun `diffen är idempotent - tillämpad och körd igen blir den tom`() {
        val existing = dosesOf("2026-05-06", "2026-05-07")
        val edited = saved.copy(dose = "750", slots = listOf(Slot.MORNING, Slot.NIGHT))
        val sync = edited.syncDoses(existing, today, STOCKHOLM, through = day("2026-05-07"))
        val byId = existing.associateBy { it.id }.toMutableMap()
        sync.delete.forEach { byId.remove(it.id) }
        (sync.update + sync.create).forEach { byId[it.id] = it }
        assertTrue(edited.syncDoses(byId.values, today, STOCKHOLM, through = day("2026-05-07")).isEmpty)
    }

    @Test fun `okänd upprepning eller okänd tidpunkt från en nyare app rör inga doser`() {
        val existing = dosesOf("2026-05-06", "2026-05-07")
        val unknown = saved.copy(schedule = Schedule.Unknown("monthly"), dose = "1000", slots = listOf(Slot.LUNCH))
        assertTrue(unknown.syncDoses(existing, today, STOCKHOLM, through = day("2026-05-07")).isEmpty)
        // En dos vars tidpunkt avkodats som "Vid behov" (okänt värde) uppdateras eller raderas aldrig.
        val strange = existing.first().copy(id = "recept_r1_2026-05-06_Senkväll", slot = Slot.AS_NEEDED)
        val sync = saved.copy(dose = "1000", period = saved.period.copy(end = day("2026-05-05"))).syncDoses(listOf(strange), today, STOCKHOLM)
        assertTrue(sync.isEmpty)
    }

    @Test fun `ett avaktiverat recept tar bort sina planerade doser från idag men skapar inga (REC-5)`() {
        val planned = dosesOf("2026-05-05", "2026-05-06", "2026-05-07")
        val taken = planned[2].copy(status = DoseStatus.TAKEN)
        val existing = planned.take(2) + taken + planned.drop(3)
        val sync = saved.copy(active = false).syncDoses(existing, today, STOCKHOLM, through = day("2026-05-07"))
        assertEquals(existing.filter { it.date!! >= today && it.status == DoseStatus.PLANNED }, sync.delete)
        assertEquals(emptyList(), sync.update)
        assertEquals(emptyList(), sync.create)
    }

    @Test fun `en dos skapas aldrig över ett befintligt id, vilken status eller vilket recept den än har`() {
        val (morning, evening) = dosesOf("2026-05-06")
        val takenElsewhere = morning.copy(status = DoseStatus.TAKEN, prescriptionId = null, date = day("2026-05-01"))
        val sync = saved.syncDoses(listOf(takenElsewhere, evening.copy(status = DoseStatus.SKIPPED)), today, STOCKHOLM)
        assertEquals(emptyList(), sync.create)
    }

    @Test fun `migrerade doser utan receptkoppling med receptets id-form räknas som receptets`() {
        val unlinked = dosesOf("2026-05-06").map { it.copy(prescriptionId = null) }
        // Ett annat recept vars id börjar likadant ("r1_x") och en vanlig dos utan koppling räknas inte.
        val lookalike = unlinked.first().copy(id = "recept_r1_x_2026-05-06_Morgon")
        val plain = unlinked.first().copy(id = "egen-dos")
        val sync = saved.copy(dose = "1000", slots = listOf(Slot.MORNING)).syncDoses(unlinked + lookalike + plain, today, STOCKHOLM)
        assertEquals(listOf("recept_r1_2026-05-06_Kväll"), sync.delete.map { it.id })
        assertEquals(listOf("recept_r1_2026-05-06_Morgon"), sync.update.map { it.id })
        assertEquals(emptyList(), sync.create)
    }
}
