package se.partee71.dagboken.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.schema.DocumentIds

/** Id-regeln för alternativ (DAT-13): `kind-slug-hash`, deterministisk och kollisionssäker. */
class OptionIdsTest {

    @Test
    fun `id-regeln - lista, slug och sex hex-tecken ur SHA-256 över det exakta namnet`() {
        assertEquals("activity-promenad-c78928", OptionIds.of(OptionKind.ACTIVITY, "Promenad"))
        assertEquals("symptom-huvudvark-d55e7d", OptionIds.of(OptionKind.SYMPTOM, "Huvudvärk"))
        assertEquals("activity-ovrigt-c067e8", OptionIds.of(OptionKind.ACTIVITY, "Övrigt"))
        assertEquals("event-yrsel-6db696", OptionIds.of(OptionKind.EVENT, "Yrsel"))
    }

    @Test
    fun `samma namn ger alltid samma id`() {
        assertEquals(OptionIds.of(OptionKind.ACTIVITY, "Promenad"), OptionIds.of(OptionKind.ACTIVITY, "Promenad"))
    }

    @Test
    fun `kollisioner - samma slug men olika namn ger olika id`() {
        val names = listOf("Promenad", "promenad", "Promenad ", " Promenad", "PROMENAD", "Promenad!", "Promenad ")
        assertEquals(setOf("promenad"), names.map(OptionIds::slug).toSet())
        assertEquals(names.size, names.map { OptionIds.of(OptionKind.ACTIVITY, it) }.toSet().size)
        // Två namn som bara skiljer sig efter slugens 32 tecken.
        val long = "Promenad i skogen med hunden och barnen"
        assertEquals(OptionIds.slug("$long på lördag"), OptionIds.slug("$long på söndag"))
        assertNotEquals(OptionIds.of(OptionKind.ACTIVITY, "$long på lördag"), OptionIds.of(OptionKind.ACTIVITY, "$long på söndag"))
        // Samma namn i olika listor.
        assertNotEquals(OptionIds.of(OptionKind.ACTIVITY, "Övrigt"), OptionIds.of(OptionKind.SYMPTOM, "Övrigt"))
        // Samma text i NFC och NFD: samma slug, men hashen är över de exakta tecknen.
        val nfd = "Övrigt"
        assertEquals(OptionIds.slug("Övrigt"), OptionIds.slug(nfd))
        assertNotEquals(OptionIds.of(OptionKind.SYMPTOM, "Övrigt"), OptionIds.of(OptionKind.SYMPTOM, nfd))
    }

    @Test
    fun `slug - translittererat, gemener, annat än a-z och 0-9 blir bindestreck, hopslagna, trimmade och högst 32 tecken`() {
        assertEquals("yoga-30-min", OptionIds.slug("  Yoga -- 30 min!  "))
        assertEquals("huvudvark", OptionIds.slug("Huvudvärk"))
        assertEquals("aao-e", OptionIds.slug("ÅÄÖ é"))
        assertEquals("", OptionIds.slug("!?#"))
        assertEquals("activity--${OptionIds.of(OptionKind.ACTIVITY, "!?#").takeLast(OptionIds.HASH_LENGTH)}", OptionIds.of(OptionKind.ACTIVITY, "!?#"))
        val slug = OptionIds.slug("a".repeat(31) + " b")
        assertEquals("a".repeat(31), slug, "ett avslutande bindestreck efter kapningen trimmas")
        assertTrue(OptionIds.slug("x".repeat(100)).length == OptionIds.MAX_SLUG)
    }

    @Test
    fun `varje id godtas som dokument-id`() {
        for (name in listOf("Promenad", "Huvudvärk", "ÅÄÖ", "a/b", "..", "x".repeat(200), "😀 Glad")) {
            for (kind in OptionKind.entries) assertTrue(DocumentIds.isValid(OptionIds.of(kind, name)), "$kind $name")
        }
    }
}
