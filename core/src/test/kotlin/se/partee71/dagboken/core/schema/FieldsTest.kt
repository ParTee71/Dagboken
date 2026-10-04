package se.partee71.dagboken.core.schema

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import se.partee71.dagboken.core.model.WireEnum

/** Ett enum som i en modell, med lagrat namn; `spruta` finns bara i en "nyare app". */
private enum class ItemUnit(override val wire: String) : WireEnum { ST("st"), KAPSEL("kapsel") }

/** Fälthjälparna testas grundligt här, en gång – codec-testerna förutsätter dem. */
class FieldsTest {

    private val doc: Doc = mapOf(
        "text" to "hej", "long" to 42L, "double" to 2.9, "bool" to true, "nothing" to null,
        "unit" to "kapsel", "futureUnit" to "spruta", "kotlinName" to "KAPSEL",
        "units" to listOf("st", "spruta", 3, "kapsel"), "date" to "2026-07-04", "badDate" to "4/7",
        "instant" to Instant.fromEpochSeconds(10), "list" to listOf("a", 1, "b", null),
        "days" to listOf(1L, 7, 0, 8, "3"), "nested" to mapOf("x" to 1, 2 to "icke-sträng-nyckel"),
    )

    @Test
    fun `strängar`() {
        assertEquals("hej", doc.string("text"))
        assertEquals("", doc.string("saknas"))
        assertEquals("", doc.string("nothing"))
        assertEquals("standard", doc.string("long", "standard"))
        assertNull(doc.stringOrNull("nothing"))
    }

    @Test
    fun `tal - Firestore ger Long, allt numeriskt accepteras`() {
        assertEquals(42, doc.int("long"))
        assertEquals(2, doc.int("double"))
        assertEquals(5, doc.int("text", 5))
        assertEquals(0, doc.int("saknas"))
        assertNull(doc.intOrNull("nothing"))
    }

    @Test
    fun `tal utanför Int begränsas i stället för att slå runt`() {
        val big = mapOf("över" to 4_294_967_301L, "under" to -4_294_967_301L, "dagar" to listOf(4_294_967_297L))
        assertEquals(Int.MAX_VALUE, big.int("över"))
        assertEquals(Int.MIN_VALUE, big.int("under"))
        assertEquals(emptySet<DayOfWeek>(), big.weekdays("dagar"), "slår inte runt till måndag")
    }

    @Test
    fun `booleska värden`() {
        assertEquals(true, doc.bool("bool"))
        assertEquals(true, doc.bool("saknas", default = true))
        assertEquals(false, doc.bool("text"))
        assertEquals(true, doc.boolOrNull("bool"))
        assertNull(doc.boolOrNull("text"))
        assertNull(doc.boolOrNull("saknas"))
    }

    @Test
    fun `enum - lagrat namn, okänt värde från en nyare app ger default`() {
        assertEquals(ItemUnit.KAPSEL, doc.wire("unit", ItemUnit.ST))
        assertEquals(ItemUnit.ST, doc.wire("futureUnit", ItemUnit.ST))
        assertEquals(ItemUnit.ST, doc.wire("kotlinName", ItemUnit.ST), "Kotlin-namnet är inte det lagrade namnet")
        assertEquals(ItemUnit.ST, doc.wire("saknas", ItemUnit.ST))
        assertNull(doc.wireOrNull<ItemUnit>("futureUnit"))
        assertEquals(listOf(ItemUnit.ST, ItemUnit.KAPSEL), doc.wireList<ItemUnit>("units"))
        assertEquals("kapsel", ItemUnit.KAPSEL.encodeWire())
        assertEquals(listOf("kapsel", "st"), listOf(ItemUnit.KAPSEL, ItemUnit.ST).encodeWires())
    }

    @Test
    fun `datum och tidpunkter`() {
        assertEquals(LocalDate(2026, 7, 4), doc.localDate("date"))
        assertNull(doc.localDate("badDate"))
        assertNull(doc.localDate("saknas"))
        assertEquals("2026-07-04", LocalDate(2026, 7, 4).encodeDate())
        assertEquals(Instant.fromEpochSeconds(10), doc.instant("instant"))
        assertNull(doc.instant("text"))
    }

    @Test
    fun `listor och veckodagar`() {
        assertEquals(listOf("a", "b"), doc.stringList("list"))
        assertEquals(emptyList(), doc.stringList("text"))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), doc.weekdays("days"))
        assertEquals(listOf(1, 3, 7), setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY).encodeWeekdays())
    }

    @Test
    fun `nästlade objekt - bara strängnycklar`() {
        assertEquals(mapOf("x" to 1), asDoc(doc["nested"]))
        assertEquals(emptyMap(), asDoc("inte en map"))
    }
}
