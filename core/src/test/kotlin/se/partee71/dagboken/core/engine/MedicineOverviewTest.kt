package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import org.junit.Test
import se.partee71.dagboken.core.model.PrnMedicine

/** Fliken Mediciners indelning (MEDF-1, MEDF-3, MEDF-5). */
class MedicineOverviewTest {

    private val today = day("2026-09-21")

    @Test fun `pågående efter namn, vid behov efter namn och avslutade senast först – ett utgånget aktivt är avslutat`() {
        val levaxin = prescription(id = "l", name = "Levaxin", start = "2026-01-01")
        val atarax = prescription(id = "a", name = "atarax", active = false)
        val kavepenin = prescription(id = "k", name = "Kåvepenin", start = "2026-09-15", end = "2026-09-21")
        val amoxicillin = prescription(id = "x", name = "Amoxicillin", start = "2026-09-01", end = "2026-09-10", active = false)
        val prednisolon = prescription(id = "p", name = "Prednisolon", start = "2026-08-01", end = "2026-08-02")
        val alvedon = PrnMedicine("2", "alvedon")
        val ipren = PrnMedicine("1", "Ipren")
        val overview = medicineOverview(listOf(levaxin, prednisolon, amoxicillin, atarax, kavepenin), listOf(ipren, alvedon), today)
        assertEquals(listOf(atarax, kavepenin, levaxin), overview.current, "idag är periodens sista dag – inte avslutat än")
        assertEquals(listOf(alvedon, ipren), overview.asNeeded)
        assertEquals(listOf(amoxicillin, prednisolon), overview.ended)
    }

    @Test fun `lika namn ordnas på id`() {
        val b = prescription(id = "b", name = "Levaxin")
        val a = prescription(id = "a", name = "levaxin")
        assertEquals(listOf(a, b), medicineOverview(listOf(b, a), emptyList(), today).current)
    }
}
