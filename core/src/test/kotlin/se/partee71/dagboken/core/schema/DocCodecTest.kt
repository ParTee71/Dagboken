package se.partee71.dagboken.core.schema

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Archivable
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.Sortable

/**
 * Kontraktet för en dokument-codec ([assertCodecContract]) bevisat med en provmodell, så att de
 * delade asserts som etapp 2:s codecs bygger på själva är testade. Modellen är syntetisk.
 */
class DocCodecTest {

    private enum class Kind { ACTIVITY, SYMPTOM }

    private data class Sample(
        override val id: String,
        val name: String = "",
        val kind: Kind = Kind.ACTIVITY,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val note: String? = null,
        val tags: List<String> = emptyList(),
        override val sortOrder: Int = 0,
        override val archived: Boolean = false,
        val createdAt: Instant? = null,
    ) : Identified, Sortable, Archivable

    private object SampleCodec : DocCodec<Sample> {
        override fun encode(value: Sample): Map<String, Any?> = mapOf(
            "name" to value.name,
            "kind" to value.kind.encodeEnum(),
            "date" to value.date.encodeDate(),
            "time" to value.time.encodeTime(),
            "note" to value.note,
            "tags" to value.tags,
            "sortOrder" to value.sortOrder,
            "archived" to value.archived,
            "createdAt" to value.createdAt,
        )

        override fun decode(id: String, map: Map<String, Any?>) = Sample(
            id = id,
            name = map.string("name"),
            kind = map.enum("kind", Kind.ACTIVITY),
            date = map.localDate("date"),
            time = map.localTime("time"),
            note = map.stringOrNull("note"),
            tags = map.stringList("tags"),
            sortOrder = map.int("sortOrder"),
            archived = map.bool("archived"),
            createdAt = map.instant("createdAt"),
        )
    }

    // Anonyma klasser i testfunktioner med svenska namn ger klassfilnamn med å/ä/ö, som inte
    // alla filsystemsinställningar klarar – därför egna objekt här.

    /** Glömmer fältet `note` vid skrivning. */
    private object ForgetfulCodec : DocCodec<Sample> {
        override fun encode(value: Sample) = SampleCodec.encode(value) - "note"

        override fun decode(id: String, map: Map<String, Any?>) = SampleCodec.decode(id, map)
    }

    private object RawCodec : ValueCodec<String> {
        override fun encode(value: String): Any? = value

        override fun decode(raw: Any?): String = raw as? String ?: "okänt"
    }

    private val sample = Sample(
        id = "s1",
        name = "Promenad",
        kind = Kind.SYMPTOM,
        date = LocalDate(2026, 10, 4),
        time = LocalTime(8, 30),
        note = "Lugnt tempo",
        tags = listOf("ute", "morgon"),
        sortOrder = 3,
        archived = true,
        createdAt = Instant.fromEpochSeconds(1_790_000_000),
    )

    @Test
    fun `provmodellen uppfyller hela codec-kontraktet`() {
        assertCodecContract(SampleCodec, sample, Sample("s1"))
    }

    @Test
    fun `kontraktet fäller en codec som glömmer ett fält`() {
        assertFailsWith<AssertionError> { assertCodecContract(ForgetfulCodec, sample, Sample("s1")) }
    }

    @Test
    fun `kontraktet fäller ett prov där ett fält har sitt defaultvärde`() {
        assertFailsWith<AssertionError> { assertEveryFieldDiffersFromDefault(sample.copy(note = null), Sample("s1")) }
    }

    @Test
    fun `nästlad codec får fältets råa värde`() {
        assertEquals("x", mapOf("v" to "x").nested("v", RawCodec))
        assertEquals("okänt", mapOf("v" to 1).nested("v", RawCodec))
    }
}
