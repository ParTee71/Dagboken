package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Slot

/** Dosformuläret (MED-11, MED-15, MED-16): dag, klockslag, "Tagen", engångsdosen och flytten av en receptdos. */
class DoseFormTest {
    private val zone = TimeZone.of("Europe/Stockholm")
    private val day = LocalDate(2026, 10, 5)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private val recipe = Dose(
        DoseIds.prescribed("p1", day, Slot.MORNING), day, Slot.MORNING, "Levaxin", "100", "µg", DoseStatus.TAKEN,
        plannedTime = LocalTime(7, 0), takenAt = at(day, 7, 12), prescriptionId = "p1",
    )
    private val prn = Dose("x", day, Slot.AS_NEEDED, "Alvedon", "500", "mg", DoseStatus.TAKEN, plannedTime = LocalTime(14, 0), takenAt = at(day, 14, 0), prnId = "a")

    @Test fun `receptkopplingen ur fältet eller ur ett migrerat dos-id`() {
        assertEquals("p1", recipe.prescriptionRef)
        assertEquals("abc_def", Dose("recept_abc_def_2026-10-05_Förmiddag").prescriptionRef, "receptets id får innehålla _")
        assertNull(prn.prescriptionRef)
        assertNull(Dose("recept_utan_datum").prescriptionRef)
        assertTrue(recipe.isPrescribed)
        assertFalse(prn.isPrescribed)
    }

    @Test fun `klockslaget är tagningstiden för en tagen dos, annars det planerade`() {
        assertEquals(LocalTime(7, 12), recipe.shownTime(zone))
        assertEquals(LocalTime(7, 0), recipe.copy(status = DoseStatus.SKIPPED, takenAt = null).shownTime(zone))
        assertEquals(Slot.EVENING.defaultTime, Dose("d", day, Slot.EVENING).shownTime(zone), "utan klockslag: tidpunktens standard")
    }

    @Test fun `ny dag och nytt klockslag flyttar tagningstiden – receptets klockslag står kvar (MED-15)`() {
        val moved = recipe.onDay(LocalDate(2026, 10, 6), zone)
        assertEquals(LocalDate(2026, 10, 6), moved.date)
        assertEquals(at(LocalDate(2026, 10, 6), 7, 12), moved.takenAt)
        val later = recipe.atTime(LocalTime(9, 30), zone)
        assertEquals(at(day, 9, 30), later.takenAt)
        assertEquals(LocalTime(7, 0), later.plannedTime, "schemats klockslag styrs av receptet")
        assertEquals(LocalTime(16, 45), prn.atTime(LocalTime(16, 45), zone).plannedTime, "en dos utan recept får klockslaget")
    }

    @Test fun `Tagen av är överhoppad utan tagningstid, på igen tagen vid det visade klockslaget (MED-15)`() {
        val skipped = recipe.withTaken(false, zone)
        assertEquals(DoseStatus.SKIPPED, skipped.status)
        assertNull(skipped.takenAt)
        val taken = skipped.withTaken(true, zone)
        assertEquals(DoseStatus.TAKEN, taken.status)
        assertEquals(at(day, 7, 0), taken.takenAt)
    }

    @Test fun `en engångsdos är tagen vid sin tid med Vid behov och utan koppling (MED-11)`() {
        val created = at(day, 15, 0)
        val dose = oneOffDose("n", day, LocalTime(14, 20), zone, created, "mg")
        assertEquals(Dose("n", day, Slot.AS_NEEDED, unit = "mg", status = DoseStatus.TAKEN, plannedTime = LocalTime(14, 20), takenAt = at(day, 14, 20), createdAt = created), dose)
    }

    @Test fun `bara en receptdos som byter dag flyttas, och den behåller receptet (MED-15)`() {
        assertTrue(recipe.movesPrescribedDose(recipe.onDay(LocalDate(2026, 10, 6), zone)))
        assertFalse(recipe.movesPrescribedDose(recipe.atTime(LocalTime(8, 0), zone)))
        assertFalse(prn.movesPrescribedDose(prn.onDay(LocalDate(2026, 10, 6), zone)))
        assertFalse(recipe.copy(id = "slumpat").movesPrescribedDose(recipe.onDay(LocalDate(2026, 10, 6), zone)), "redan flyttad: inte bunden till dagen")
        val next = LocalDate(2026, 10, 6)
        val legacy = recipe.copy(id = "recept_p1_2026-10-05_Morgon", prescriptionId = null)
        assertEquals(legacy.copy(id = "recept_p1_2026-10-06_Morgon", date = next, prescriptionId = "p1"), legacy.moveTarget(legacy.copy(date = next)) { "slumpat" })
        val linked = recipe.copy(prescriptionId = "annat")
        assertEquals("annat", linked.moveTarget(linked.copy(date = next)) { "slumpat" }.prescriptionId, "en befintlig koppling ändras aldrig")
        val asNeeded = recipe.copy(id = "recept_p1_2026-10-05_Vid behov", slot = Slot.AS_NEEDED)
        assertEquals("slumpat", asNeeded.moveTarget(asNeeded.copy(date = next)) { "slumpat" }.id, "Vid behov har inget datumbundet id")
    }

    @Test fun `kontrollens dagar täcker kylperioden, dagsgränsen och en vecka bakåt (FAV-4, FAV-5, MED-16)`() {
        val medicine = PrnMedicine("a", "Alvedon", minHoursBetween = 4)
        assertEquals(LocalDate(2026, 9, 27)..day, medicine.checkDays(at(day, 10), zone), "ett dygn bakåt plus en vecka")
        assertEquals(LocalDate(2026, 9, 26)..day, medicine.copy(minHoursBetween = 50).checkDays(at(day, 10), zone))
    }
}
