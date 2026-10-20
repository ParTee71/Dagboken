package se.partee71.dagboken.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

/** Kroppen och sammanhanget (DAT-15): katalogerna i visningsordning och växlingsregeln [TagCatalog.toggle]. */
class SensationsTest {

    private val body = BodySensationCatalog
    private val context = ContextTagCatalog

    @Test
    fun `katalogerna har sina ord i visningsordning, unika och på lagringsformen`() {
        assertEquals(
            listOf("tense_jaw", "heart_racing", "stomach_knot", "heavy_chest", "restless_legs", "warm_face", "headache", "tired_body", "light_body", "warm_soft", "nothing"),
            body.tokens
        )
        assertEquals(listOf("sleep", "family", "work", "health", "medicines", "alone_time", "weather", "finances", "friends", "exercise", "food"), context.tokens)
        for (catalog in listOf(body, context)) {
            assertEquals(catalog.tokens.size, catalog.tokens.toSet().size, "unika")
            assertTrue(catalog.tokens.all(TagCatalog.TOKEN::matches), "1–32 gemener a–z och _: ${catalog.tokens}")
            assertTrue(catalog.tokens.size <= TagCatalog.MAX_TAGS, "hela katalogen ryms i en logg")
        }
        assertEquals(TagCatalog.MAX_TAGS, maxOf(body.tokens.size, context.tokens.size), "taket är den största katalogen")
        assertEquals(BodySensationCatalog.NOTHING, body.exclusive)
        assertEquals(null, context.exclusive)
    }

    @Test
    fun `tokenformen är 1-32 gemener a-z och understreck utom true, false och null - prövad mot definitionen`() {
        fun expected(text: String) = text.length in 1..TagCatalog.MAX_TOKEN && text.all { it in 'a'..'z' || it == '_' } && text !in TagCatalog.RESERVED
        fun check(text: String) = assertEquals(expected(text), TagCatalog.TOKEN.matches(text), text)
        val all = ('a'..'z') + '_'
        // Varje sträng med 1–4 tecken ur hela alfabetet, och med 5 tecken ur gränstecknen i mönstrets teckenklasser.
        var words = listOf("")
        repeat(4) { words = words.flatMap { w -> all.map { w + it } }.also { it.forEach(::check) } }
        val edges = "abdefgklmnoqrstuvz_".toList()
        for (w in words.filter { word -> word.all { it in edges } }) for (c in edges) check(w + c)
        for (n in 0..TagCatalog.MAX_TOKEN + 2) check("f".repeat(n))
        for (text in listOf("true", "false", "null", "truee", "tru", "nul", "fals", "falsee", "nulls", "tr_e", "_null", "Sleep", "sömn", "work2", "a b", "a-b", "a.b")) check(text)
    }

    @Test
    fun `en katalog med ogiltigt token, dubblett eller okänt uteslutande token går inte att skapa`() {
        assertFailsWith<IllegalArgumentException> { TagCatalog(listOf("Sömn")) }
        assertFailsWith<IllegalArgumentException> { TagCatalog(listOf("sleep", "sleep")) }
        assertFailsWith<IllegalArgumentException> { TagCatalog(listOf("sleep"), exclusive = "nothing") }
    }

    @Test
    fun `vanlig växling - väljer och avmarkerar, resultatet i katalogens ordning`() {
        assertEquals(listOf("work"), context.toggle(emptyList(), "work"))
        assertEquals(listOf("sleep", "work"), context.toggle(listOf("work"), "sleep"), "katalogens ordning, inte valordningen")
        assertEquals(listOf("sleep", "work", "food"), context.toggle(listOf("food", "sleep"), "work"))
        assertEquals(listOf("food"), context.toggle(listOf("work", "food"), "work"))
        assertEquals(emptyList(), context.toggle(listOf("work"), "work"))
        assertEquals(listOf("sleep"), context.toggle(listOf("sleep", "sleep", "work"), "work"), "dubbletter slås ihop")
        assertEquals(context.tokens, context.tokens.fold(emptyList<String>()) { selected, token -> context.toggle(selected, token) }, "alla elva går att välja")
        assertEquals(listOf("sleep"), body.ordered(listOf("sleep")))
    }

    @Test
    fun `Ingenting särskilt avmarkerar övriga, och ett annat val avmarkerar Ingenting särskilt`() {
        val nothing = BodySensationCatalog.NOTHING
        assertEquals(listOf(nothing), body.toggle(listOf("headache", "tense_jaw"), nothing))
        assertEquals(listOf("headache"), body.toggle(listOf(nothing), "headache"))
        assertEquals(listOf("tense_jaw", "headache"), body.toggle(listOf("headache", nothing), "tense_jaw"), "en trasig lista med båda rättas")
        assertEquals(emptyList(), body.toggle(listOf(nothing), nothing), "Ingenting särskilt avmarkeras som vanligt")
        assertEquals(listOf("tense_jaw", "heart_racing"), body.toggle(listOf("heart_racing"), "tense_jaw"))
        assertEquals(listOf("tense_jaw"), body.toggle(listOf("tense_jaw", "heart_racing"), "heart_racing"))
    }

    @Test
    fun `okända tokens (nyare app) står kvar sist, men fler än taket väljs inte`() {
        assertEquals(listOf("sleep", "work", "framtida_ord"), context.toggle(listOf("framtida_ord", "work"), "sleep"))
        assertEquals(listOf("work"), context.toggle(listOf("framtida_ord", "work"), "framtida_ord"))
        assertEquals(listOf("headache", "framtida_ord"), body.toggle(listOf("framtida_ord"), "headache"))
        assertEquals(listOf(BodySensationCatalog.NOTHING), body.toggle(listOf("framtida_ord", "headache"), BodySensationCatalog.NOTHING), "Ingenting särskilt utesluter också okända")
        val full = context.tokens.drop(1) + "framtida_ord"
        assertSame(full, context.toggle(full, "sleep"), "ett tolfte val görs inte")
        assertEquals(context.tokens.drop(2) + "framtida_ord", context.toggle(full, "family"), "avmarkering går alltid")
    }
}
