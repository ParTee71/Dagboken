package se.partee71.dagboken.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildScreeningAktivitetUseCaseTest {

    private val useCase = BuildScreeningAktivitetUseCase()

    @Test fun `build sets type screening and computed fields`() {
        val entry = useCase.build(
            aktivitetName = "Screening",
            datum = "2026-07-28",
            tid = "09:00",
            energy = 7,
            stress = 3,
            symptomScores = mapOf("Yrsel" to 4, "Huvudvärk" to 2),
        )
        assertEquals("screening", entry.type)
        assertEquals("Screening", entry.aktivitet)
        assertEquals(7, entry.energy)
        assertEquals(3, entry.stress)
        assertEquals(6, entry.somatiska)
        assertEquals("Yrsel:4,Huvudvärk:2", entry.symptom)
        assertEquals("2026-07-28", entry.datum)
        assertEquals("09:00", entry.tid)
    }

    @Test fun `build generates a new id when editId is null`() {
        val entry = useCase.build("Screening", "2026-07-28", "09:00", 5, 5, emptyMap())
        assertNotNull(entry.id)
        assertTrue(entry.id.isNotBlank())
    }

    @Test fun `build reuses editId when provided`() {
        val entry = useCase.build("Screening", "2026-07-28", "09:00", 5, 5, emptyMap(), editId = "existing-id")
        assertEquals("existing-id", entry.id)
    }

    @Test fun `build excludes zero-score symptoms from wire format`() {
        val entry = useCase.build(
            "Screening", "2026-07-28", "09:00", 5, 5,
            mapOf("Yrsel" to 0, "Huvudvärk" to 3),
        )
        assertEquals("Huvudvärk:3", entry.symptom)
        assertEquals(3, entry.somatiska)
    }
}
