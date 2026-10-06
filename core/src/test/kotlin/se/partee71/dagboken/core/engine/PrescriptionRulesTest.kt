package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import se.partee71.dagboken.core.engine.PrescriptionError.BASE_DOSE_NOT_NUMERIC
import se.partee71.dagboken.core.engine.PrescriptionError.BOOSTS_OVERLAP
import se.partee71.dagboken.core.engine.PrescriptionError.BOOST_END_BEFORE_START
import se.partee71.dagboken.core.engine.PrescriptionError.BOOST_NOT_POSITIVE
import se.partee71.dagboken.core.engine.PrescriptionError.BOOST_OUTSIDE_PERIOD
import se.partee71.dagboken.core.engine.PrescriptionError.BOOST_WITHOUT_DOSE
import se.partee71.dagboken.core.engine.PrescriptionError.END_BEFORE_START
import se.partee71.dagboken.core.model.Boost

/** Receptformulärets regler (REC-7, REC-9) i 3.x-ordning, och förvalen för en ny höjning. Port av 3.x `ReceptForm.validate()`. */
class PrescriptionRulesTest {

    private fun rx(dose: String = "500", start: String? = "2026-05-01", end: String? = "2026-05-31", vararg boosts: Boost) =
        prescription(dose = dose, start = start, end = end, boosts = boosts.toList())

    @Test fun `ett recept utan höjningar, och med höjningar i följd, är giltigt`() {
        assertNull(rx().validate())
        assertNull(rx(end = null).validate())
        assertNull(rx(dose = "1 tablett").validate())
        assertNull(rx("500", "2026-05-01", "2026-05-31", boost("2026-05-01", "2026-05-05", "250"), boost("2026-05-06", null, "0,5")).validate())
    }

    @Test fun `periodens slut före start`() {
        assertEquals(END_BEFORE_START, rx(start = "2026-05-10", end = "2026-05-09").validate())
        assertNull(rx(start = "2026-05-10", end = "2026-05-10").validate())
        assertNull(rx(start = null, end = "2026-05-09").validate())
    }

    @Test fun `höjning utan dos`() {
        assertEquals(BOOST_WITHOUT_DOSE, rx("500", "2026-05-01", "2026-05-31", boost("2026-05-01", "2026-05-05", " ")).validate())
    }

    @Test fun `grunddosen måste vara ett tal när det finns höjningar`() {
        assertEquals(BASE_DOSE_NOT_NUMERIC, rx("1 tablett", "2026-05-01", "2026-05-31", boost("2026-05-01", "2026-05-05", "1")).validate())
    }

    @Test fun `höjningen måste vara ett tal större än 0`() {
        for (dose in listOf("0", "-1", "lite", "0,0", "1e3", "5d")) {
            assertEquals(BOOST_NOT_POSITIVE, rx("500", "2026-05-01", "2026-05-31", boost("2026-05-01", "2026-05-05", dose)).validate(), dose)
        }
    }

    @Test fun `höjningens slut före start`() {
        assertEquals(BOOST_END_BEFORE_START, rx("500", "2026-05-01", "2026-05-31", boost("2026-05-10", "2026-05-09", "1")).validate())
    }

    @Test fun `höjning utanför perioden - kontrolleras före slut före start, så en tom slutdag aldrig ger det felet`() {
        assertEquals(BOOST_OUTSIDE_PERIOD, rx("500", "2026-05-01", "2026-05-31", boost("2026-06-02", null, "1")).validate())
        assertEquals(BOOST_OUTSIDE_PERIOD, rx("500", "2026-05-01", "2026-05-31", boost("2026-04-30", "2026-05-05", "1")).validate())
        assertEquals(BOOST_OUTSIDE_PERIOD, rx("500", "2026-05-01", "2026-05-31", boost("2026-05-20", "2026-06-01", "1")).validate())
        assertEquals(BOOST_OUTSIDE_PERIOD, rx("500", "2026-05-01", "2026-05-31", boost("2026-06-05", "2026-06-01", "1")).validate())
        assertNull(rx("500", "2026-05-01", "2026-05-31", boost("2026-05-01", "2026-05-31", "1")).validate())
        assertNull(rx("500", null, null, boost("2020-01-01", "2099-12-31", "1")).validate())
    }

    @Test fun `överlappande höjningar, även en dag och oavsett ordning`() {
        assertEquals(BOOSTS_OVERLAP, rx("500", "2026-05-01", "2026-05-31", boost("2026-05-06", "2026-05-10", "1"), boost("2026-05-01", "2026-05-06", "1")).validate())
        assertEquals(BOOSTS_OVERLAP, rx("500", "2026-05-01", null, boost("2026-05-01", null, "1"), boost("2026-05-20", "2026-05-25", "1")).validate())
    }

    @Test fun `höjning utan start räknas inte i slut- eller överlappskontrollen, som i boostFor`() {
        assertNull(rx("500", "2026-05-01", null, boost(null, "2026-05-03", "1"), boost(null, "2026-05-01", "1")).validate())
        assertNull(rx("500", "2026-05-01", null, boost(null, null, "1"), boost("2026-05-04", null, "1")).validate())
        assertNull(rx("500", "2026-05-01", "2026-05-31", boost(null, "2026-04-01", "1")).validate())
    }

    @Test fun `första felet i 3x-ordningen vinner`() {
        val everything = rx("tablett", "2026-05-10", "2026-05-09", boost("2026-05-06", "2026-05-01", ""), boost("2026-05-02", "2026-05-03", "0"))
        assertEquals(END_BEFORE_START, everything.validate())
        assertEquals(BOOST_WITHOUT_DOSE, everything.copy(period = everything.period.copy(end = null)).validate())
        assertEquals(BASE_DOSE_NOT_NUMERIC, rx("tablett", "2026-05-01", null, boost("2026-05-06", "2026-05-01", "0")).validate())
        assertEquals(BOOST_NOT_POSITIVE, rx("5", "2026-05-01", null, boost("2026-05-06", "2026-05-01", "0")).validate())
        assertEquals(BOOST_END_BEFORE_START, rx("5", "2026-05-01", null, boost("2026-05-06", "2026-05-01", "1"), boost("2026-05-01", "2026-05-09", "1")).validate())
    }

    // ── Ny höjning ────────────────────────────────────────────────────────────

    @Test fun `ny höjning börjar dagen efter den senast slutande och varar en vecka`() {
        val p = rx("500", "2026-05-01", null, boost("2026-05-01", "2026-05-05", "1"), boost("2026-05-06", "2026-05-12", "1"))
        assertEquals(Boost("ny", day("2026-05-13"), day("2026-05-19"), "", "mg"), p.nextBoostDefaults("ny", today = day("2026-01-01")))
    }

    @Test fun `en höjning utan eget slut slutar med perioden, en utan start räknas inte`() {
        val bounded = rx("500", "2026-05-01", "2026-06-30", boost("2026-05-03", "2026-05-20", "1"), boost(null, "2026-06-20", "1"))
        assertEquals(Boost("ny", day("2026-05-21"), day("2026-05-27"), "", "mg"), bounded.nextBoostDefaults("ny", day("2026-07-01")))
        val openInBounded = rx("500", "2026-05-01", "2026-06-30", boost("2026-05-03", null, "1"))
        assertNull(openInBounded.nextBoostDefaults("ny", day("2026-07-01")))
    }

    @Test fun `förslaget klipps till perioden`() {
        // Sista höjningen slutar dagen före periodens sista dag – förslaget får bara den dagen.
        val almostFull = rx("500", "2026-05-01", "2026-05-31", boost("2026-05-03", "2026-05-30", "1"))
        assertEquals(Boost("ny", day("2026-05-31"), day("2026-05-31"), "", "mg"), almostFull.nextBoostDefaults("ny", day("2026-07-01")))
        assertEquals(day("2026-05-04"), rx("500", "2026-05-01", "2026-05-04").nextBoostDefaults("ny", day("2026-05-02"))?.end)
        // Idag före periodens start (utan start används idag – med start klipps till den).
        assertEquals(day("2026-05-01"), rx("500", "2026-05-01", null).nextBoostDefaults("ny", day("2026-04-01"))?.start)
        assertEquals(day("2026-05-31"), rx("500", null, "2026-05-31").nextBoostDefaults("ny", day("2026-07-01"))?.start)
    }

    @Test fun `inget förslag när det inte finns plats - full period eller öppen höjning i öppen period`() {
        assertNull(rx("500", "2026-05-01", "2026-05-31", boost("2026-05-03", "2026-05-31", "1")).nextBoostDefaults("ny", day("2026-07-01")))
        assertNull(rx("500", "2026-05-01", null, boost("2026-05-03", null, "1")).nextBoostDefaults("ny", day("2026-07-01")))
        assertNull(rx("500", null, null, boost("2026-05-03", null, "1"), boost(null, null, "1")).nextBoostDefaults("ny", day("2026-01-01")))
        // Varje förslag som ges går att spara (med en dos).
        for (p in listOf(rx(), rx(end = null), rx("500", "2026-05-01", "2026-05-31", boost("2026-05-03", "2026-05-29", "1")))) {
            val next = p.nextBoostDefaults("ny", day("2026-05-02"))!!.copy(dose = "1")
            assertNull(p.copy(boosts = p.boosts + next).validate(), p.toString())
        }
    }

    @Test fun `utan höjningar börjar den på periodens start, annars idag`() {
        assertEquals(day("2026-05-01"), rx("500", "2026-05-01", null).nextBoostDefaults("ny", day("2026-07-01"))?.start)
        assertEquals(Boost("ny", day("2028-02-26"), day("2028-03-03"), "", "mg"), rx(start = null, end = null).nextBoostDefaults("ny", today = day("2028-02-26")))
    }

    @Test fun `en lös odaterad höjning hindrar inte att receptet sparas`() {
        for (loose in listOf(boost(null, null, ""), boost(null, "2026-05-03", "0"), boost(null, null, "lite"))) {
            assertNull(rx("500", "2026-05-01", "2026-05-31", loose).validate(), loose.toString())
            assertNull(rx("1 tablett", "2026-05-01", "2026-05-31", loose).validate(), loose.toString())
        }
        assertEquals(BOOST_WITHOUT_DOSE, rx("500", "2026-05-01", "2026-05-31", boost(null, null, "1"), boost("2026-05-02", null, "")).validate())
    }
}
