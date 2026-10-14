package se.partee71.dagboken.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Ordkatalogen (SCR-8, DAT-14): 6 kärnor × 6 nyanser × 2 ord = 72 precisare ord, 114 unika tokens, i hjulets ordning – och [FeelingCatalog.isKnown]. */
class FeelingCatalogTest {

    private val allWords: List<Feeling> = FeelingCatalog.cores.flatMap { core ->
        FeelingCatalog.nuances(core).flatMap { nuance -> FeelingCatalog.details(core, nuance).map { Feeling(core, nuance, it) } }
    }

    @Test
    fun `sex kärnor i hjulets ordning, var och en med sex nyanser och två ord per nyans`() {
        assertEquals(listOf("glad", "strong", "calm", "sad", "angry", "scared"), FeelingCatalog.cores)
        for (core in FeelingCatalog.cores) {
            assertEquals(6, FeelingCatalog.nuances(core).size, core)
            for (nuance in FeelingCatalog.nuances(core)) assertEquals(2, FeelingCatalog.details(core, nuance).size, "$core.$nuance")
        }
        assertEquals(72, allWords.size, "72 ord med alla tre nivåerna")
        assertEquals(listOf("excited", "energetic", "playful", "creative", "hopeful", "content"), FeelingCatalog.nuances("glad"))
        assertEquals(listOf("overwhelmed", "tense"), FeelingCatalog.details("scared", "anxious"))
        assertEquals(listOf("humble", "moved"), FeelingCatalog.details("calm", "grateful"))
    }

    @Test
    fun `alla tokens är unika och i gemener a-z - resursnyckeln är feeling_ plus delarna`() {
        val tokens = allWords.flatMap { listOf(it.core, it.nuance!!, it.detail!!) }.toSet()
        assertEquals(6 + 36 + 72, tokens.size, "kärnor, nyanser och ord överlappar inte")
        assertEquals(72, allWords.map { "${it.core}.${it.nuance}.${it.detail}" }.toSet().size)
        for (token in tokens) assertTrue(Regex("^[a-z]+$").matches(token), token)
    }

    @Test
    fun `isKnown - kärna, nyans och ord på sin plats är kända, allt annat okänt`() {
        for (word in allWords) {
            assertTrue(FeelingCatalog.isKnown(word), word.toString())
            assertTrue(FeelingCatalog.isKnown(Feeling(word.core, word.nuance)), "${word.core}.${word.nuance}")
            assertTrue(FeelingCatalog.isKnown(Feeling(word.core)), word.core)
        }
        assertFalse(FeelingCatalog.isKnown(Feeling("bored")), "en nyans är ingen kärna")
        assertFalse(FeelingCatalog.isKnown(Feeling("Glad")), "tokens är gemener")
        assertFalse(FeelingCatalog.isKnown(Feeling("glad", "anxious")), "nyansen hör till en annan kärna")
        assertFalse(FeelingCatalog.isKnown(Feeling("scared", "anxious", "giddy")), "ordet hör till en annan nyans")
        assertFalse(FeelingCatalog.isKnown(Feeling("scared", null, "tense")), "ett ord utan nyans")
        assertFalse(FeelingCatalog.isKnown(Feeling("scared", "framtida")))
        assertFalse(FeelingCatalog.isKnown(Feeling("scared", "anxious", "framtida")))
        assertEquals(emptyList(), FeelingCatalog.nuances("okänd"))
        assertEquals(emptyList(), FeelingCatalog.details("scared", "okänd"))
        assertEquals(emptyList(), FeelingCatalog.details("okänd", "anxious"))
    }

    @Test
    fun `högst sex känslor per logg`() {
        assertEquals(6, FeelingCatalog.MAX_FEELINGS)
    }
}
