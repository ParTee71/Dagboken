package se.partee71.dagboken.core.schema

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.legacy.LegacyFixtures

/**
 * Paritetstabellen i ARKITEKTUR.md → Datamodell → "Fältparitet 3.x → 4.0" är kontrollerad
 * (ADR-001, beslut 11; OMB-3): inget 3.x-fält saknar rad, inget 4.0-fält i tabellen saknas i sin
 * codec, inget codec-fält saknas i tabellen, och inget fält är utelämnat. *metadata* är reserverat för
 * backupfilens egna fält (`version`, `createdAt`), som beskriver filen och inte användarens data.
 */
class ParityTableTest {

    private class Row(val legacy: String, val targets: List<String>, val markers: List<String>)

    private val section: String = File("../ARKITEKTUR.md").readText()
        .substringAfter("### Fältparitet 3.x → 4.0").substringBefore("\n## ")

    private val rows: List<Row> = section.lines().filter { it.startsWith("| `") }.map { line ->
        val cells = line.split('|').map { it.trim() }
        Row(
            legacy = TOKEN.find(cells[1])!!.groupValues[1],
            targets = TOKEN.findAll(cells[2]).map { it.groupValues[1] }.toList(),
            markers = MARKER.findAll(cells[2]).map { it.groupValues[1] }.toList(),
        )
    }

    /** Fältvägarna per samling, ur provens kodade dokument. */
    private val paths: Map<String, Set<String>> = Samples.all.associate { it.collection to fieldPaths(it.encoded()) }

    @Test
    fun `varje 3x-fält har en rad, och ingen rad är för ett fält som inte finns`() {
        assertEquals(LEGACY_FIELDS.sorted(), rows.map { it.legacy }.sorted())
    }

    @Test
    fun `varje rad pekar på ett 4_0-fält eller säger hur värdet bevaras`() {
        for (row in rows) {
            assertTrue(row.targets.isNotEmpty() || row.markers.isNotEmpty(), "${row.legacy} saknar plats i 4.0")
            assertTrue(row.markers.all { it in MARKERS }, "${row.legacy}: okänd markering ${row.markers}")
        }
    }

    @Test
    fun `inget 3x-fält är utelämnat`() {
        assertEquals(emptyList(), rows.filter { "utelämnas" in it.markers }.map { it.legacy })
    }

    @Test
    fun `bara backupfilens egna fält är metadata`() {
        assertEquals(listOf("BackupJson.createdAt", "BackupJson.version"), rows.filter { "metadata" in it.markers }.map { it.legacy }.sorted())
    }

    @Test
    fun `varje 4_0-fält i tabellen finns i samlingens codec`() {
        for (row in rows) {
            for (target in row.targets) {
                val collection = target.substringBefore('.')
                assertTrue(collection in paths, "${row.legacy}: okänd samling i `$target`")
                if ('.' in target && target.substringAfter('.') != "id") {
                    assertTrue(target.substringAfter('.') in paths.getValue(collection), "${row.legacy}: `$target` finns inte i codecen")
                }
            }
        }
    }

    @Test
    fun `varje fält i codecarna finns i tabellen eller bland de nya fälten`() {
        val documented = rows.flatMap { it.targets }.toSet() + newFields()
        for ((collection, fields) in paths) {
            for (leaf in fields.filter { p -> fields.none { it.startsWith("$p.") || it.startsWith("$p[].") } }) {
                assertTrue("$collection.$leaf" in documented, "`$collection.$leaf` saknas i paritetstabellen")
            }
        }
    }

    private fun newFields(): Set<String> =
        TOKEN.findAll(section.substringAfter("Nya fält i 4.0 utan 3.x-motsvarighet:").lineSequence().first())
            .map { it.groupValues[1] }.toSet()

    private companion object {
        val TOKEN = Regex("`([^`]+)`")
        val MARKER = Regex("\\*([^*`]+)\\*")
        val MARKERS = setOf("utelämnas", "beräknas", "sökväg", "metadata")

        /**
         * Varje fält i 3.x `BackupJson` och dess klasser, ur konverterarens egna 3.x-klasser i `:core`
         * (`legacy/BackupJson.kt`, som speglar branchen `legacy`) – ett nytt fält där kräver en rad här.
         */
        val LEGACY_FIELDS: List<String> = LegacyFixtures.BACKUP_FIELDS
    }
}
