package se.partee71.dagboken.core.model

import org.junit.Test
import kotlin.test.assertEquals

class TraitsTest {

    private data class Option(override val id: String, override val sortOrder: Int) : Identified, Sortable

    @Test
    fun `Sortable-ordningen sorterar på sortOrder`() {
        val options = listOf(Option("c", sortOrder = 3), Option("a", sortOrder = 1), Option("b", sortOrder = 2))
        assertEquals(listOf("a", "b", "c"), options.sortedWith(Sortable.ORDER).map { it.id })
    }
}
