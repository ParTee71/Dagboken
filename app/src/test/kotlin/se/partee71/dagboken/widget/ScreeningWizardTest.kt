package se.partee71.dagboken.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningWizardTest {

    @Test fun `screeningLastStep is stress when no favorite symptoms`() {
        assertEquals(SCREENING_STEP_STRESS, screeningLastStep(hasFavoriteSymptoms = false))
    }

    @Test fun `screeningLastStep is symptom when favorite symptoms exist`() {
        assertEquals(SCREENING_STEP_SYMPTOM, screeningLastStep(hasFavoriteSymptoms = true))
    }

    @Test fun `next from energy goes to stress`() {
        val step = nextScreeningStep(SCREENING_STEP_ENERGY, "next", hasFavoriteSymptoms = false)
        assertEquals(SCREENING_STEP_STRESS, step)
    }

    @Test fun `next from stress stays at stress when no favorite symptoms`() {
        val step = nextScreeningStep(SCREENING_STEP_STRESS, "next", hasFavoriteSymptoms = false)
        assertEquals(SCREENING_STEP_STRESS, step)
    }

    @Test fun `next from stress goes to symptom when favorite symptoms exist`() {
        val step = nextScreeningStep(SCREENING_STEP_STRESS, "next", hasFavoriteSymptoms = true)
        assertEquals(SCREENING_STEP_SYMPTOM, step)
    }

    @Test fun `back from energy exits the wizard`() {
        val step = nextScreeningStep(SCREENING_STEP_ENERGY, "back", hasFavoriteSymptoms = false)
        assertEquals(SCREENING_STEP_INACTIVE, step)
    }

    @Test fun `back from stress goes to energy`() {
        val step = nextScreeningStep(SCREENING_STEP_STRESS, "back", hasFavoriteSymptoms = true)
        assertEquals(SCREENING_STEP_ENERGY, step)
    }

    @Test fun `cancel exits the wizard from any step`() {
        assertEquals(SCREENING_STEP_INACTIVE, nextScreeningStep(SCREENING_STEP_SYMPTOM, "cancel", true))
    }

    @Test fun `clampStepperValue clamps to 0 and 10`() {
        assertEquals(0, clampStepperValue(0, -1))
        assertEquals(10, clampStepperValue(10, 1))
        assertEquals(6, clampStepperValue(5, 1))
    }

    @Test fun `adjustSymptomScore adds a new symptom`() {
        val scores = adjustSymptomScore(emptyMap(), "Yrsel", 1)
        assertEquals(mapOf("Yrsel" to 1), scores)
    }

    @Test fun `adjustSymptomScore removes symptom when score drops to 0`() {
        val scores = adjustSymptomScore(mapOf("Yrsel" to 1), "Yrsel", -1)
        assertEquals(emptyMap<String, Int>(), scores)
    }

    @Test fun `adjustSymptomScore clamps at 10`() {
        val scores = adjustSymptomScore(mapOf("Yrsel" to 10), "Yrsel", 1)
        assertEquals(mapOf("Yrsel" to 10), scores)
    }
}
