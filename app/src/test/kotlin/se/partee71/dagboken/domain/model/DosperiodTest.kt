package se.partee71.dagboken.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** REC-9: vilken dos som gäller en viss dag, inklusive överlappande dosperioder. */
class DosperiodTest {

    private fun recept(vararg perioder: Dosperiod) = Recept(
        id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
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

    @Test fun `dosperiod applies on its first and last day`() {
        val r = recept(period("d1", "2026-02-01", "2026-02-07", "250"))
        assertEquals("250" to "mg", r.dosFor(LocalDate.of(2026, 2, 1)))
        assertEquals("250" to "mg", r.dosFor(LocalDate.of(2026, 2, 7)))
        assertEquals("500" to "mg", r.dosFor(LocalDate.of(2026, 2, 8)))
    }

    @Test fun `open-ended dosperiod applies from its start onwards`() {
        val r = recept(period("d1", "2026-02-01", null, "250"))
        assertEquals("250" to "mg", r.dosFor(LocalDate.of(2026, 6, 1)))
    }

    /**
     * Formuläret hindrar överlapp, men importerad eller äldre data kan innehålla det.
     * Då ska den senast påbörjade gälla — en nedtrappning som lagts ovanpå en längre
     * period är den mer specifika.
     */
    @Test fun `overlapping dosperioder resolve to the latest started one`() {
        val r = recept(
            period("bred", "2026-02-01", "2026-02-28", "250"),
            period("smal", "2026-02-10", "2026-02-12", "125"),
        )
        assertEquals("125" to "mg", r.dosFor(LocalDate.of(2026, 2, 11)))
        assertEquals("250" to "mg", r.dosFor(LocalDate.of(2026, 2, 5)))
        assertEquals("250" to "mg", r.dosFor(LocalDate.of(2026, 2, 20)))
    }

    @Test fun `dosperiod with unparsable start date is ignored`() {
        val r = recept(period("trasig", "inte-ett-datum", null, "999"))
        assertEquals("500" to "mg", r.dosFor(LocalDate.of(2026, 2, 11)))
    }
}
