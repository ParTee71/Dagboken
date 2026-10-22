package se.partee71.dagboken.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** Styrka och kommentar per vald känsla (SCR-9, SCR-10): cykeln och hur värdena följer valen. */
class FeelingValuesTest {
    private val calm = Feeling("calm")
    private val grateful = Feeling("calm", "grateful")
    private val humble = Feeling("calm", "grateful", "humble")
    private val moved = Feeling("calm", "grateful", "moved")
    private val glad = Feeling("glad")

    @Test
    fun `långtryckets cykel - ingen, svag, tydlig, stark, ingen`() {
        assertEquals(FeelingStrength.MILD, FeelingStrength.next(null))
        assertEquals(FeelingStrength.CLEAR, FeelingStrength.next(FeelingStrength.MILD))
        assertEquals(FeelingStrength.STRONG, FeelingStrength.next(FeelingStrength.CLEAR))
        assertNull(FeelingStrength.next(FeelingStrength.STRONG))
        assertEquals(listOf("mild", "clear", "strong"), FeelingStrength.entries.map { it.wire })
        assertNull(FeelingStrength.of("framtida"))
        var post = Screening("s", feelings = listOf(calm, glad))
        val seen = (1..4).map {
            post = post.withNextStrength(glad)
            post.strengthOf(glad)
        }
        assertEquals(listOf(FeelingStrength.MILD, FeelingStrength.CLEAR, FeelingStrength.STRONG, null), seen)
        assertEquals(emptyMap(), post.feelingStrengths, "tillbaka till ingen tar bort nyckeln")
        assertEquals(post, post.withNextStrength(Feeling("sad")), "en känsla som inte är vald ändras inte")
        assertEquals(post, post.withNote(Feeling("sad"), "Sol"), "inte heller en kommentar till en känsla som inte är vald")
        val unknown = Screening("s", feelings = listOf(glad), feelingStrengths = mapOf("glad" to "framtida"))
        assertEquals(mapOf("glad" to "mild"), unknown.withNextStrength(glad).feelingStrengths, "ett okänt ord är ingen styrka – nästa steg är svag")
    }

    @Test
    fun `förfining tar med styrka och kommentar till det nya ordet på samma plats`() {
        val post = Screening("s", feelings = listOf(glad, calm)).withNextStrength(calm).withNote(calm, "Sol")
        val refined = post.withFeelings(listOf(glad, grateful)).withFeelings(listOf(glad, humble))
        assertEquals(mapOf("calm.grateful.humble" to "mild"), refined.feelingStrengths)
        assertEquals(mapOf("calm.grateful.humble" to "Sol"), refined.feelingNotes)
        val sibling = refined.withFeelings(listOf(glad, moved))
        assertEquals(FeelingStrength.MILD, sibling.strengthOf(moved), "ett annat ord på samma nivå tar med styrkan")
    }

    @Test
    fun `ett ord som släcks nollställer styrkan men behåller kommentaren`() {
        val post = Screening("s", feelings = listOf(humble)).withNextStrength(humble).withNote(humble, "Sol")
        val back = post.withFeelings(listOf(grateful))
        assertNull(back.strengthOf(grateful))
        assertEquals("Sol", back.noteOf(grateful))
        assertEquals(FeelingStrength.MILD, back.withNextStrength(grateful).strengthOf(grateful), "nästa långtryck börjar om på svag")
        assertEquals(emptyMap(), post.withFeelings(listOf(calm)).feelingStrengths)
    }

    @Test
    fun `en borttagen känsla tar med sig styrka och kommentar - vald igen börjar den tom`() {
        val post = Screening("s", feelings = listOf(glad, calm)).withNextStrength(glad).withNote(glad, "Kaffe").withNextStrength(calm)
        val removed = post.withFeelings(listOf(calm))
        assertEquals(mapOf("calm" to "mild"), removed.feelingStrengths, "de andra står kvar")
        assertEquals(emptyMap(), removed.feelingNotes)
        val again = removed.withFeelings(listOf(calm, glad))
        assertNull(again.strengthOf(glad))
        assertEquals("", again.noteOf(glad))
    }

    @Test
    fun `kommentaren - tom text tar bort den, en känsla som inte är vald ändras inte`() {
        val post = Activity("a", feelings = listOf(glad)).withNote(glad, "Promenad ")
        assertEquals("Promenad ", post.noteOf(glad), "som den skrivs; codecen trimmar")
        assertEquals(emptyMap(), post.withNote(glad, "  ").feelingNotes)
        assertEquals(post, post.withNote(calm, "x"))
    }

    @Test
    fun `bara valda känslor - only filtrerar och behåller samma karta när allt är valt`() {
        val values = mapOf("glad" to "mild")
        assertEquals(values, FeelingValues.only(listOf(glad), values))
        assertEquals(emptyMap(), FeelingValues.only(listOf(calm), values))
        assertEquals(emptyMap(), FeelingValues.carry(listOf(glad), listOf(calm), emptyMap<String, String>(), keepWhenCoarser = true))
    }
}
