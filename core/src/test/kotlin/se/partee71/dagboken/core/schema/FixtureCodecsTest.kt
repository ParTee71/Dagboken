package se.partee71.dagboken.core.schema

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.model.OptionIds

/**
 * tools/db:s testdata (`tools/db/test/fixtures/user.json`) – samma dokument som rundturen mot
 * emulatorn och rules-testet använder – läses och skrivs igen med appens codecs: inget fält som
 * codecen känner tappas eller ändras, och varje codec-fält är satt i minst ett dokument (regel 1).
 */
class FixtureCodecsTest {

    private val documents = ExportFormat.decode(File("../tools/db/test/fixtures/user.json").readText())

    /** Fixturens dokument per samling (sökvägens näst sista led), utan användardokumentet. */
    private val byCollection = documents.filter { it.path.count { c -> c == '/' } > 1 }
        .groupBy { it.path.split('/').let { parts -> parts[parts.size - 2] } }

    @Test
    fun `fixturen har dokument i varje samling som har en codec`() {
        assertEquals(Samples.all.map { it.collection }.sorted(), byCollection.keys.sorted())
    }

    @Test
    fun `varje dokument kommer tillbaka oförändrat genom sin codec - okända fält rörs inte`() {
        for ((collection, docs) in byCollection) {
            val entry = Samples.entry(collection)
            for (doc in docs) {
                val id = doc.path.substringAfterLast('/')
                val written = entry.reencode(id, doc.data)
                val stored = doc.data.filterKeys { it in written }
                assertEquals(json(stored), json(written), doc.path)
                assertEquals(written.keys, stored.keys, "${doc.path} saknar fält som codecen skriver")
            }
        }
    }

    @Test
    fun `varje fält i varje codec har ett värde i minst ett dokument`() {
        for ((collection, docs) in byCollection) {
            val entry = Samples.entry(collection)
            val expected = fieldPaths(entry.encoded())
            val set = docs.flatMap { fieldPaths(withoutEmpty(it.data)) }.toSet()
            assertTrue(set.containsAll(expected), "$collection: fält utan värde i fixturen: ${expected - set}")
        }
    }

    @Test
    fun `varje fält har ett icke-default-värde i minst ett dokument`() {
        for ((collection, docs) in byCollection) {
            val entry = Samples.entry(collection)
            val differing = docs.flatMap { entry.fieldsDifferingFromDefault(it.path.substringAfterLast('/'), it.data) }.toSet()
            assertEquals(entry.fieldNames(), differing, "$collection: fält som bara har default-värdet i fixturen")
        }
    }

    @Test
    fun `alternativens id följer id-regeln OptionIds (DAT-13)`() {
        for (doc in byCollection.getValue("options")) {
            val option = OptionCodec.decode(doc.path.substringAfterLast('/'), doc.data)
            assertEquals(OptionIds.of(option.kind, option.name), option.id, doc.path)
        }
    }

    private fun json(doc: Doc) = ExportFormat.toJson(doc)

    /** Dokumentet utan tomma värden (`null`, tom text, tom lista) på någon nivå. */
    private fun withoutEmpty(doc: Doc): Doc = doc.mapNotNull { (key, value) -> withoutEmpty(value)?.let { key to it } }.toMap()

    private fun withoutEmpty(value: Any?): Any? = when (value) {
        null -> null
        is String -> value.ifEmpty { null }
        is Map<*, *> -> withoutEmpty(asDoc(value))
        is List<*> -> value.mapNotNull(::withoutEmpty).ifEmpty { null }
        else -> value
    }
}
