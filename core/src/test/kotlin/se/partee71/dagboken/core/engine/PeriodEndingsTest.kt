package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import org.junit.Test
import se.partee71.dagboken.core.engine.PeriodEnding.BoostEnds
import se.partee71.dagboken.core.engine.PeriodEnding.PrescriptionEnds
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule

/** Periodslut för påminnelsen dagen innan (NOT-12) och bannern (MEDF-2). Port av 3.x `PeriodEndingsUseCaseTest`. */
class PeriodEndingsTest {

    private val tomorrow = day("2026-05-10")

    private fun prednisolon(end: String? = null, vararg boosts: Boost, active: Boolean = true, id: String = "r1", name: String = "Prednisolon") =
        prescription(id = id, name = name, dose = "5", start = "2026-05-01", end = end, boosts = boosts.toList(), active = active)

    @Test fun `receptet vars period slutar den dagen rapporteras`() {
        assertEquals(listOf(PrescriptionEnds("r1", "Prednisolon", tomorrow)), listOf(prednisolon("2026-05-10")).endingOn(tomorrow))
    }

    @Test fun `annat slutdatum, tills vidare eller inaktivt rapporteras inte`() {
        assertEquals(emptyList(), listOf(prednisolon("2026-05-11"), prednisolon(null), prednisolon("2026-05-10", active = false)).endingOn(tomorrow))
    }

    @Test fun `en höjning som slutar rapporterar grunddosen som tar över`() {
        val p = prednisolon("2026-05-20", boost("2026-05-01", "2026-05-10", "10"))
        assertEquals(listOf(BoostEnds("r1", "Prednisolon", tomorrow, "5", "mg")), listOf(p).endingOn(tomorrow))
    }

    @Test fun `en höjning som lämnar över till nästa rapporterar den nya totalen`() {
        val p = prednisolon("2026-05-20", boost("2026-05-01", "2026-05-10", "10"), boost("2026-05-11", "2026-05-20", "2,5"))
        assertEquals(listOf(BoostEnds("r1", "Prednisolon", tomorrow, "7,5", "mg")), listOf(p).endingOn(tomorrow))
    }

    @Test fun `höjning som slutar med receptet rapporterar bara receptets slut`() {
        val same = prednisolon("2026-05-10", boost("2026-05-01", "2026-05-10", "10"))
        assertEquals(listOf(PrescriptionEnds("r1", "Prednisolon", tomorrow)), listOf(same).endingOn(tomorrow))
        val open = prednisolon("2026-05-20", boost("2026-05-01", null, "10"))
        assertEquals(emptyList(), listOf(open).endingOn(tomorrow))
    }

    @Test fun `överlappande höjningar i gammal data - den inre som slutar rapporterar den yttre som tar över`() {
        val p = prednisolon(null, boost("2026-05-01", "2026-05-31", "10"), boost("2026-05-08", "2026-05-10", "20"))
        assertEquals(listOf(BoostEnds("r1", "Prednisolon", tomorrow, "15", "mg")), listOf(p).endingOn(tomorrow))
    }

    @Test fun `flera recept som slutar samma dag rapporteras alla`() {
        val list = listOf(prednisolon("2026-05-10"), prednisolon("2026-05-10", id = "r2", name = "Amoxicillin"))
        assertEquals(listOf("r1", "r2"), list.endingOn(tomorrow).map { it.prescriptionId })
    }

    @Test fun `bannern visar slut idag och i morgon, i den ordningen (MEDF-2)`() {
        val list = listOf(prednisolon("2026-05-10", id = "imorgon"), prednisolon("2026-05-09", id = "idag"), prednisolon("2026-05-11", id = "senare"))
        assertEquals(listOf("idag" to day("2026-05-09"), "imorgon" to tomorrow), list.endingSoon(day("2026-05-09")).map { it.prescriptionId to it.date })
    }

    @Test fun `periodslut på skottdagen`() {
        val p = prescription(start = "2028-02-01", end = "2028-02-29", boosts = listOf(boost("2028-02-20", "2028-02-28", "250")))
        assertEquals(listOf(BoostEnds("r1", "Metformin", day("2028-02-28"), "500", "mg")), listOf(p).endingOn(day("2028-02-28")))
        assertEquals(listOf(PrescriptionEnds("r1", "Metformin", day("2028-02-29"))), listOf(p).endingOn(day("2028-02-29")))
    }

    @Test fun `receptets slut är sista dagen schemat ger en dos - helgrecept som slutar en onsdag`() {
        val weekends = prescription(schedule = schedule(Repeat.WEEKENDS), start = "2026-05-01", end = "2026-05-13")
        assertEquals(listOf(PrescriptionEnds("r1", "Metformin", day("2026-05-10"))), listOf(weekends).endingOn(day("2026-05-10")))
        assertEquals(emptyList(), listOf(weekends).endingOn(day("2026-05-13")))
        assertEquals(listOf(day("2026-05-10")), listOf(weekends).endingSoon(day("2026-05-09")).map { it.date })
    }

    @Test fun `en höjning rapporteras bara om receptet ger en dos efter den`() {
        // Helgrecept som slutar onsdag 13 maj, höjning som slutar tisdag 12 maj: ingen dos efter höjningen.
        val weekends = prescription(schedule = schedule(Repeat.WEEKENDS), start = "2026-05-01", end = "2026-05-13", boosts = listOf(boost("2026-05-01", "2026-05-12", "250")))
        assertEquals(emptyList(), listOf(weekends).endingOn(day("2026-05-12")))
        val unknown = prescription(schedule = Schedule.Unknown("monthly"), boosts = listOf(boost("2026-05-01", "2026-05-12", "250")))
        assertEquals(emptyList(), listOf(unknown).endingOn(day("2026-05-12")))
    }

    @Test fun `den nya dosen är nästa dosdags, även efter ett uppehåll`() {
        // Helgrecept: höjningen slutar lördag 9 maj, söndagen har nästa höjning.
        val p = prescription(schedule = schedule(Repeat.WEEKENDS), boosts = listOf(boost("2026-05-01", "2026-05-09", "250"), boost("2026-05-10", "2026-05-31", "100")))
        assertEquals(listOf(BoostEnds("r1", "Metformin", day("2026-05-09"), "600", "mg")), listOf(p).endingOn(day("2026-05-09")))
    }

    @Test fun `höjningens slut är sista dosdagen - helgrecept med höjning som slutar onsdag slutar söndagen före`() {
        val p = prescription(schedule = schedule(Repeat.WEEKENDS), boosts = listOf(boost("2026-05-01", "2026-05-13", "250")))
        assertEquals(listOf(BoostEnds("r1", "Metformin", day("2026-05-10"), "500", "mg")), listOf(p).endingOn(day("2026-05-10")))
        assertEquals(emptyList(), listOf(p).endingOn(day("2026-05-13")))
    }

    @Test fun `receptkortets periodslut idag eller i morgon - bara receptets eget, bara aktivt (MEDF-1)`() {
        val today = day("2026-05-09")
        assertEquals(today, prednisolon("2026-05-09").endingSoonDate(today))
        assertEquals(tomorrow, prednisolon("2026-05-10").endingSoonDate(today))
        assertEquals(null, prednisolon("2026-05-11").endingSoonDate(today))
        assertEquals(null, prednisolon("2026-05-10", active = false).endingSoonDate(today))
        assertEquals(null, prednisolon("2026-05-20", boost("2026-05-01", "2026-05-10", "10")).endingSoonDate(today), "en höjning är inget periodslut")
    }
}
