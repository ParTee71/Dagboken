package se.partee71.dagboken.core.schema

import java.io.File
import kotlin.test.assertEquals
import org.junit.Test
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.WireEnum

/**
 * Enum-listorna i `firestore.rules` är desamma som de lagrade namnen i `:core` – ett nytt värde i
 * Kotlin utan rules (eller tvärtom) skulle nekas av servern eller släppa igenom okända värden.
 */
class RulesEnumsTest {

    private val rules = File("../firestore.rules").readText()

    /** `function namn() { return ['a', 'b']; }` i rules. */
    private fun rulesList(function: String): List<String> {
        val body = Regex("""function $function\(\) \{ return \[([^\]]*)\]; \}""").find(rules)?.groupValues?.get(1)
            ?: error("firestore.rules saknar $function()")
        return Regex("'([^']*)'").findAll(body).map { it.groupValues[1] }.toList()
    }

    private fun wires(values: List<WireEnum>) = values.map { it.wire }

    @Test
    fun `enum-listorna i rules är de lagrade namnen`() {
        assertEquals(wires(OptionKind.entries), rulesList("optionKinds"))
        assertEquals(wires(Slot.entries), rulesList("slots"))
        assertEquals(wires(Slot.SCHEDULED), rulesList("scheduledSlots"))
        assertEquals(wires(DoseStatus.entries), rulesList("doseStatuses"))
        assertEquals(wires(Occasion.entries), rulesList("occasions"))
        assertEquals(wires(Repeat.entries), rulesList("repeats"))
        assertEquals(wires(ThemeMode.entries), rulesList("themeModes"))
        assertEquals(wires(Sex.entries), rulesList("sexes"))
    }

    @Test
    fun `de lagrade namnen är unika inom varje enum`() {
        for (values in listOf(OptionKind.entries, Slot.entries, DoseStatus.entries, Occasion.entries, Repeat.entries, ThemeMode.entries, Sex.entries)) {
            assertEquals(values.size, wires(values).toSet().size, values.first()::class.simpleName)
        }
    }
}
