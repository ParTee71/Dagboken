package se.partee71.dagboken.core.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.junit.Test
import se.partee71.dagboken.core.schema.DocumentIds

/** Receptdosernas id är 3.x-schemat (MED-4, DAT-8), så att migrerade doser inte dubbleras. */
class DoseIdsTest {

    @Test
    fun `id-schemat är 3x recept_receptId_datum_tidpunkt`() {
        val id = DoseIds.prescribed("6f1c2a9e-0b7d-4c55", LocalDate(2026, 9, 21), Slot.MIDMORNING)
        assertEquals("recept_6f1c2a9e-0b7d-4c55_2026-09-21_Förmiddag", id)
    }

    @Test
    fun `tidpunkternas 3x-namn och ordning är DAT-1`() {
        assertEquals(listOf("Morgon", "Förmiddag", "Lunch", "Eftermiddag", "Kväll", "Natt", "Vid behov"), Slot.entries.map { it.legacyName })
        assertEquals(Slot.MIDMORNING, Slot.fromLegacyName("Förmiddag"))
        assertEquals(Slot.entries - Slot.AS_NEEDED, Slot.SCHEDULED)
    }

    @Test
    fun `varje schemalagd receptdos har ett id som djuplänken godtar`() {
        for (slot in Slot.SCHEDULED) {
            assertTrue(DocumentIds.isValid(DoseIds.prescribed("6f1c2a9e-0b7d-4c55-9a43-1f2e3d4c5b6a", LocalDate(2026, 12, 31), slot)), slot.name)
        }
    }
}
