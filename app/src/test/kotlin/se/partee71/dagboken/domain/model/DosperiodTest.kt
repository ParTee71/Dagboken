package se.partee71.dagboken.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** REC-9/REC-12: vilken total dos som gäller en viss dag, inklusive överlappande höjningar. */
class DosperiodTest {

    private fun recept(vararg perioder: Dosperiod, dos: String = "500") = Recept(
        id = "r1", namn = "Metformin", dos = dos, enhet = "mg",
        tidpunkter = listOf("Morgon"), upprepning = "dagligen", dagar = emptyList(),
        aktiv = true, skapad = "2026-01-01", startDatum = "2026-01-01",
        dosperioder = perioder.toList(),
    )

    private fun period(id: String, start: String, slut: String?, dos: String) =
        Dosperiod(id = id, startDatum = start, slutDatum = slut, dos = dos, enhet = "mg")

    @Test fun `base dose applies when no dosperiod covers the date`() {
        val r = recept(period("d1", "2026-02-01", "2026-02-07", "250"))
        assertNull(r.dosperiodFor(LocalDate.of(2026, 1, 20)))
        assertEquals("500" to "mg", r.dosFor(LocalDate.of(2026, 1, 20)))
    }

    @Test fun `an increase is added to the base dose on its first and last day`() {
        val r = recept(period("d1", "2026-02-01", "2026-02-07", "250"))
        assertEquals("750" to "mg", r.dosFor(LocalDate.of(2026, 2, 1)))
        assertEquals("750" to "mg", r.dosFor(LocalDate.of(2026, 2, 7)))
        assertEquals("500" to "mg", r.dosFor(LocalDate.of(2026, 2, 8)))
    }

    @Test fun `open-ended increase applies from its start onwards`() {
        val r = recept(period("d1", "2026-02-01", null, "250"))
        assertEquals("750" to "mg", r.dosFor(LocalDate.of(2026, 6, 1)))
    }

    @Test fun `decimal doses are summed and formatted with a comma`() {
        val r = recept(period("d1", "2026-02-01", null, "0,5"), dos = "1")
        assertEquals("1,5" to "mg", r.dosFor(LocalDate.of(2026, 2, 1)))
    }

    @Test fun `a whole number total keeps no decimals`() {
        val r = recept(period("d1", "2026-02-01", null, "0,5"), dos = "1,5")
        assertEquals("2" to "mg", r.dosFor(LocalDate.of(2026, 2, 1)))
    }

    @Test fun `a non numeric base dose keeps the base dose unchanged`() {
        val r = recept(period("d1", "2026-02-01", null, "1"), dos = "en tablett")
        assertEquals("en tablett" to "mg", r.dosFor(LocalDate.of(2026, 2, 1)))
    }

    @Test fun `hojningFor renders the increase with a plus sign`() {
        val r = recept(period("d1", "2026-02-01", "2026-02-07", "250"))
        assertEquals("+250 mg", r.hojningFor(LocalDate.of(2026, 2, 3)))
        assertNull(r.hojningFor(LocalDate.of(2026, 2, 8)))
    }

    /**
     * Formuläret hindrar överlapp, men importerad eller äldre data kan innehålla det.
     * Då ska den senast påbörjade gälla — en höjning som lagts ovanpå en längre
     * period är den mer specifika.
     */
    @Test fun `overlapping dosperioder resolve to the latest started one`() {
        val r = recept(
            period("bred", "2026-02-01", "2026-02-28", "250"),
            period("smal", "2026-02-10", "2026-02-12", "125"),
        )
        assertEquals("625" to "mg", r.dosFor(LocalDate.of(2026, 2, 11)))
        assertEquals("750" to "mg", r.dosFor(LocalDate.of(2026, 2, 5)))
        assertEquals("750" to "mg", r.dosFor(LocalDate.of(2026, 2, 20)))
    }

    @Test fun `dosperiod with unparsable start date is ignored`() {
        val r = recept(period("trasig", "inte-ett-datum", null, "999"))
        assertEquals("500" to "mg", r.dosFor(LocalDate.of(2026, 2, 11)))
    }

    // ─── parseDos / formatDos ─────────────────────────────────────────────────

    @Test fun `parseDos accepts both comma and dot`() {
        assertEquals(0.5, parseDos("0,5")!!, 0.0001)
        assertEquals(0.5, parseDos("0.5")!!, 0.0001)
        assertEquals(500.0, parseDos(" 500 ")!!, 0.0001)
    }

    @Test fun `parseDos returns null for text doses`() {
        assertNull(parseDos("en tablett"))
        assertNull(parseDos(""))
        assertNull(parseDos(null))
    }

    @Test fun `formatDos drops trailing zeros`() {
        assertEquals("2", formatDos(2.0))
        assertEquals("1,5", formatDos(1.5))
        assertEquals("0,25", formatDos(0.25))
    }
}
