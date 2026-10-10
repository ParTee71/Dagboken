package se.partee71.dagboken.core.cli

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/** Kommandoradens tolkning, delad av `convertLegacyBackup` och `matchMedicines`. */
class CliArgsTest {

    private val flags = setOf("--in", "--out")
    private val switches = setOf("--force")

    private fun parse(vararg args: String) = CliArgs.parse(arrayOf(*args), flags, switches)

    @Test
    fun `flaggor med värde och brytare i valfri ordning`() {
        val parsed = assertNotNull(parse("--force", "--out", "ut.json", "--in", "in.json"))
        assertEquals(mapOf("--in" to "in.json", "--out" to "ut.json"), parsed.values)
        assertEquals(setOf("--force"), parsed.switches)
        assertEquals("in.json", parsed.value("--in"))
        assertEquals(emptySet(), assertNotNull(parse("--in", "a")).switches, "brytaren är valfri")
        assertEquals(emptyMap(), assertNotNull(parse()).values, "vilka flaggor som krävs avgör anroparen")
    }

    @Test
    fun `okänd flagga, dubblerad flagga eller brytare och flagga utan värde nekas`() {
        assertNull(parse("--in", "a", "--okänd", "b"), "okänd flagga")
        assertNull(parse("in.json"), "värde utan flagga")
        assertNull(parse("--in", "a", "--in", "b"), "dubblerad flagga")
        assertNull(parse("--force", "--in", "a", "--force"), "dubblerad brytare")
        assertNull(parse("--in"), "flagga sist utan värde")
        assertNull(parse("--in", "--out", "b"), "nästa flagga är inget värde")
        assertNull(parse("--force", "ja"), "en brytare tar inget värde")
        assertNull(CliArgs.parse(arrayOf("--force"), flags), "brytare som inte är tillåten")
    }
}
