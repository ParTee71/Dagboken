package se.partee71.dagboken.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymptomUtilsTest {

    // ─── encode ───────────────────────────────────────────────────────────────

    @Test fun `encode returns empty string for empty map`() {
        assertEquals("", SymptomUtils.encode(emptyMap()))
    }

    @Test fun `encode omits zero-score entries`() {
        assertEquals("", SymptomUtils.encode(mapOf("Nackvärk" to 0, "Heshet" to 0)))
    }

    @Test fun `encode single symptom`() {
        assertEquals("Nackvärk:3", SymptomUtils.encode(mapOf("Nackvärk" to 3)))
    }

    @Test fun `encode multiple symptoms separated by comma`() {
        val result = SymptomUtils.encode(mapOf("Nackvärk" to 3, "Heshet" to 2))
        assertTrue(result.contains("Nackvärk:3"))
        assertTrue(result.contains("Heshet:2"))
        assertTrue(result.contains(","))
    }

    @Test fun `encode omits zero among positives`() {
        val result = SymptomUtils.encode(mapOf("Nackvärk" to 3, "Heshet" to 0, "Feberkänsla" to 1))
        assertTrue(result.contains("Nackvärk:3"))
        assertTrue(result.contains("Feberkänsla:1"))
        assertTrue(!result.contains("Heshet"))
    }

    // ─── decode ───────────────────────────────────────────────────────────────

    @Test fun `decode returns empty map for blank string`() {
        assertTrue(SymptomUtils.decode("").isEmpty())
        assertTrue(SymptomUtils.decode("   ").isEmpty())
    }

    @Test fun `decode single symptom`() {
        assertEquals(mapOf("Nackvärk" to 3), SymptomUtils.decode("Nackvärk:3"))
    }

    @Test fun `decode multiple symptoms`() {
        val result = SymptomUtils.decode("Nackvärk:3,Heshet:2")
        assertEquals(3, result["Nackvärk"])
        assertEquals(2, result["Heshet"])
    }

    @Test fun `decode handles spaces around comma`() {
        val result = SymptomUtils.decode("Nackvärk:3, Heshet:2")
        assertEquals(3, result["Nackvärk"])
        assertEquals(2, result["Heshet"])
    }

    @Test fun `decode ignores entries without colon`() {
        val result = SymptomUtils.decode("Nackvärk3")
        assertTrue(result.isEmpty())
    }

    @Test fun `decode ignores entries with non-numeric score`() {
        val result = SymptomUtils.decode("Nackvärk:abc")
        assertTrue(result.isEmpty())
    }

    @Test fun `decode handles symptom name with colon via lastIndexOf`() {
        val result = SymptomUtils.decode("Yrsel:lätt:3")
        assertEquals(3, result["Yrsel:lätt"])
    }

    // ─── sum ──────────────────────────────────────────────────────────────────

    @Test fun `sum returns 0 for blank`() {
        assertEquals(0, SymptomUtils.sum(""))
    }

    @Test fun `sum returns total of all scores`() {
        assertEquals(6, SymptomUtils.sum("Nackvärk:3,Heshet:2,Feberkänsla:1"))
    }

    @Test fun `sum round-trips through encode`() {
        val encoded = SymptomUtils.encode(mapOf("Nackvärk" to 3, "Heshet" to 2))
        assertEquals(5, SymptomUtils.sum(encoded))
    }
}
