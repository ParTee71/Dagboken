package se.partee71.dagboken.ui.medicines

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.testing.FakeMedicines
import se.partee71.dagboken.testing.MainDispatcherRule

/** Förslagstillståndet som recept- och vid behov-formuläret delar (REC-14). */
class MedicineSuggestionsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val medicines = FakeMedicines()
    private val name = MutableStateFlow("")

    private fun TestScope.suggestions(enabled: Boolean = true) = MedicineSuggestions(medicines, enabled, name, backgroundScope, main.dispatcher)

    private fun MedicineSuggestions.titles() = matches.value.map { it.entry.title }

    @Test
    fun `förslag först från tre tecken, högst fem, med träffen markerad`() = runTest(main.dispatcher) {
        val suggestions = suggestions()
        name.value = "al"
        assertEquals(emptyList(), suggestions.titles())
        name.value = "alv"
        assertEquals(listOf("Alvedon 60 mg", "Alvedon 500 mg", "Alvedon 500 mg", "Alvedon forte 1 g"), suggestions.titles())
        assertEquals(0 to 3, suggestions.matches.value.first().let { it.start to it.length })
        name.value = "levaxin 50"
        assertEquals(listOf("Levaxin 50 mikrogram"), suggestions.titles())
        assertTrue(suggestions.matches.value.size <= 5)
    }

    @Test
    fun `inga förslag när de är avstängda (ett befintligt läkemedel)`() = runTest(main.dispatcher) {
        val suggestions = suggestions(enabled = false)
        name.value = "alvedon"
        assertEquals(emptyList(), suggestions.titles())
    }

    @Test
    fun `ett val stänger förslagen tills namnet ändras`() = runTest(main.dispatcher) {
        val suggestions = suggestions()
        name.value = "alv"
        suggestions.pick(suggestions.matches.value[1].entry)
        name.value = "Alvedon"
        assertEquals(emptyList(), suggestions.titles())
        name.value = "Alvedon f"
        assertEquals(listOf("Alvedon forte 1 g"), suggestions.titles())
    }
}
