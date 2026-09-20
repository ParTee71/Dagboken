package se.partee71.dagboken.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** FAV-11: receptet som vid behov-snabbval i "Fler"-listan på Idag. */
class ReceptVidBehovTest {

    private fun recept(vararg perioder: Dosperiod) = Recept(
        id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
        tidpunkter = listOf("Morgon"), upprepning = "dagligen", dagar = emptyList(),
        aktiv = true, skapad = "2026-01-01", startDatum = "2026-01-01",
        dosperioder = perioder.toList(),
    )

    @Test fun `snabbvalet speglar receptets namn dos och enhet`() {
        val fav = recept().asVidBehovFavorit(LocalDate.of(2026, 1, 20))
        assertEquals("Metformin", fav.namn)
        assertEquals("500", fav.dos)
        assertEquals("mg", fav.enhet)
        assertEquals("Vid behov", fav.tidpunkt)
    }

    @Test fun `snabbvalets id har receptprefix sa det inte forvaxlas med en favorit`() {
        val fav = recept().asVidBehovFavorit(LocalDate.of(2026, 1, 20))
        assertEquals("${RECEPT_VIDBEHOV_ID_PREFIX}r1", fav.id)
        assertTrue(fav.id.startsWith(RECEPT_VIDBEHOV_ID_PREFIX))
    }

    @Test fun `snabbvalet visar dagens hojda dos (REC-12)`() {
        val r = recept(
            Dosperiod(id = "d1", startDatum = "2026-02-01", slutDatum = "2026-02-07", dos = "250", enhet = "mg"),
        )
        assertEquals("750", r.asVidBehovFavorit(LocalDate.of(2026, 2, 3)).dos)
        assertEquals("500", r.asVidBehovFavorit(LocalDate.of(2026, 2, 8)).dos)
    }

    @Test fun `snabbvalet har varken kylperiod eller dagsgrans`() {
        val fav = recept().asVidBehovFavorit(LocalDate.of(2026, 1, 20))
        assertEquals(0, fav.minTidMellan)
        assertEquals(0, fav.maxDoserPerDag)
        assertEquals(false, fav.isFavorite)
    }
}
