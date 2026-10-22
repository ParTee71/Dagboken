package se.partee71.dagboken.core.model

import kotlin.test.assertEquals
import org.junit.Test

class TraitsTest {

    private data class Option(override val id: String, override val sortOrder: Int) :
        Identified,
        Sortable

    @Test
    fun `Sortable-ordningen sorterar på sortOrder`() {
        val options = listOf(Option("c", sortOrder = 3), Option("a", sortOrder = 1), Option("b", sortOrder = 2))
        assertEquals(listOf("a", "b", "c"), options.sortedWith(Sortable.ORDER).map { it.id })
    }

    @Test
    fun `3x-somatiska i kontot gäller tills symptomen ändras, och summan gäller sedan`() {
        val symptoms = listOf(SymptomScore("symptom-a", 2), SymptomScore("symptom-b", null))
        val stored = Activity("a1", symptoms = symptoms, legacySomatic = 3)
        assertEquals(3, stored.somatic, "det bevarade 3.x-värdet gäller")
        assertEquals(stored, stored.withSymptoms(symptoms), "samma symptom rör inget")
        val edited = stored.withSymptoms(listOf(SymptomScore("symptom-a", 4)))
        assertEquals(null to 4, edited.legacySomatic to edited.somatic)
        assertEquals(2, Activity("a2", symptoms = symptoms).somatic, "utan 3.x-värde: summan, poäng utan värde räknas som 0")
    }
}
