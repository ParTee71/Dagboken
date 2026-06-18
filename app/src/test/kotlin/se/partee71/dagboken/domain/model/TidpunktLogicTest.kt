package se.partee71.dagboken.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TidpunktLogicTest {

    // ─── tidpunktToHour ───────────────────────────────────────────────────────

    @Test fun `tidpunktToHour Kväll returns 19`() {
        assertEquals(19, tidpunktToHour("Kväll"))
    }

    @Test fun `tidpunktToHour Vid behov returns null`() {
        assertNull(tidpunktToHour("Vid behov"))
    }

    @Test fun `tidpunktToHour returns null for unknown string`() {
        assertNull(tidpunktToHour("Okänd"))
    }

    @Test fun `tidpunktToHour returns correct hour for every named tidpunkt`() {
        assertEquals(7,  tidpunktToHour("Morgon"))
        assertEquals(10, tidpunktToHour("Förmiddag"))
        assertEquals(12, tidpunktToHour("Lunch"))
        assertEquals(15, tidpunktToHour("Eftermiddag"))
        assertEquals(19, tidpunktToHour("Kväll"))
        assertEquals(22, tidpunktToHour("Natt"))
    }

    @Test fun `all named tidpunkter are derived from TIDP_DEFAULT_TIMES`() {
        for ((name, time) in TIDP_DEFAULT_TIMES) {
            if (name == "Vid behov") continue
            val expectedHour = time.substringBefore(":").toInt()
            assertEquals("$name should map to hour $expectedHour", expectedHour, tidpunktToHour(name))
        }
    }

    // ─── tidpunktSortIndex ────────────────────────────────────────────────────

    @Test fun `tidpunktSortIndex Morgon returns 0`() {
        assertEquals(0, tidpunktSortIndex("Morgon"))
    }

    @Test fun `tidpunktSortIndex Kväll returns 4`() {
        assertEquals(4, tidpunktSortIndex("Kväll"))
    }

    @Test fun `tidpunktSortIndex Vid behov returns 6 (last)`() {
        assertEquals(6, tidpunktSortIndex("Vid behov"))
    }

    @Test fun `tidpunktSortIndex unknown returns TIDP_ORDER size`() {
        assertEquals(TIDP_ORDER.size, tidpunktSortIndex("Okänd"))
    }

    @Test fun `TIDP_ORDER contains all expected periods in sequence`() {
        assertEquals(
            listOf("Morgon", "Förmiddag", "Lunch", "Eftermiddag", "Kväll", "Natt", "Vid behov"),
            TIDP_ORDER,
        )
    }
}
