package se.partee71.dagboken.core.engine

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.schema.wireValue

/**
 * Portad från 3.x `domain/model/ProfileTest`: profilvärdena bakom sömnkvalitetens åldersnormer
 * (HLS-11). 3.x-testerna av könets lagringsnycklar: 3.x-nycklarna (`man`/`kvinna`/`ej_angivet`) prövas i
 * `BackupJsonConverterTest` ("kön - alla tre 3x-värden får sitt 4-punkt-0-namn …"), 4.0:s nycklar
 * nedan ("4.0:s lagringsnycklar …") och mot `firestore.rules` i `RulesEnumsTest`.
 */
class ProfileTest {

    private val today = LocalDate(2026, 8, 5)

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

    @Test fun `the plausible range is exactly 120 years back`() {
        assertEquals(1906..2026, plausibleBirthYears(today))
        assertEquals(120, ageFromBirthYear(1906, today))
        assertNull(ageFromBirthYear(1905, today))
    }

    @Test fun `age counts whole calendar years, also on new year's eve and day`() {
        // Som i 3.x: bara året räknas, inte födelsedagen.
        assertEquals(54, ageFromBirthYear(1971, LocalDate(2025, 12, 31)))
        assertEquals(55, ageFromBirthYear(1971, LocalDate(2026, 1, 1)))
    }

    @Test fun `4_0 storage keys for sex are the documented strings and read back`() {
        assertEquals(listOf("male", "female", "unspecified"), Sex.entries.map { it.wire })
        Sex.entries.forEach { assertEquals(it, wireValue<Sex>(it.wire)) }
    }
}
