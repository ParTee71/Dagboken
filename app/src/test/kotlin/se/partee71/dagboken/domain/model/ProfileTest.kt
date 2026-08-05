package se.partee71.dagboken.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Enhetstest för profilvärdena bakom sömnkvalitetens åldersnormer (HLS-11, §19). */
class ProfileTest {

    private val today = LocalDate.of(2026, 8, 5)

    @Test fun `age is the difference in years`() {
        assertEquals(55, ageFromBirthYear(1971, today))
    }

    @Test fun `a missing birth year gives no age`() {
        assertNull(ageFromBirthYear(null, today))
    }

    @Test fun `an implausible birth year is rejected rather than producing a nonsense age`() {
        assertNull(ageFromBirthYear(1800, today))
        assertNull(ageFromBirthYear(today.year + 1, today))
    }

    @Test fun `the current year is accepted`() {
        assertEquals(0, ageFromBirthYear(today.year, today))
    }

    // ─── Lagringsnycklar ─────────────────────────────────────────────────────

    @Test fun `every sex round-trips through its storage key`() {
        // Nycklarna ligger i DataStore och i backupen — byter de värde tappar
        // användaren sin inställning vid nästa återställning.
        Sex.entries.forEach { sex ->
            assertEquals(sex, Sex.fromStorageKey(sex.storageKey))
        }
    }

    @Test fun `storage keys are the documented strings`() {
        assertEquals("man", Sex.MAN.storageKey)
        assertEquals("kvinna", Sex.KVINNA.storageKey)
        assertEquals("ej_angivet", Sex.EJ_ANGIVET.storageKey)
    }

    @Test fun `an unknown or missing key falls back to unspecified`() {
        assertEquals(Sex.EJ_ANGIVET, Sex.fromStorageKey(null))
        assertEquals(Sex.EJ_ANGIVET, Sex.fromStorageKey("annat"))
    }
}
