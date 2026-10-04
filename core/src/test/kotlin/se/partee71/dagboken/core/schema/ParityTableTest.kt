package se.partee71.dagboken.core.schema

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Paritetstabellen i ARKITEKTUR.md → Datamodell → "Fältparitet 3.x → 4.0" är kontrollerad
 * (ADR-001, beslut 11; OMB-3): inget 3.x-fält saknar rad, inget 4.0-fält i tabellen saknas i sin
 * codec, inget codec-fält saknas i tabellen, och bara de två beslutade fälten är utelämnade.
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
    fun `exakt två fält är utelämnade - dynamicColor och sheetsConfig`() {
        assertEquals(
            listOf("BackupJson.sheetsConfig", "SettingsBackup.dynamicColor"),
            rows.filter { "utelämnas" in it.markers }.map { it.legacy }.sorted(),
        )
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
         * Varje fält i 3.x `BackupJson` och dess klasser (branchen `legacy`,
         * `data/migration/BackupJson.kt`). Ersätts av reflektion över konverterarens egna
         * 3.x-klasser när de finns i `:core` (etapp 2, PR 2).
         */
        val LEGACY_FIELDS: List<String> = mapOf(
            "BackupJson" to "version createdAt aktiviteter mediciner medicinRecipes medicinFavoriter aktiviteterOptions symptomOptions " +
                "aktiviteterOptionsV2 symptomOptionsV2 sjukdomsepisoder sjukdomsIncheckningar handelser notes screeningEventConfigs " +
                "medNotificationConfigs sheetsConfig handelseTypOptions periodReminderTime settings",
            "SettingsBackup" to "medsNotificationsEnabled themeMode themeLightStart themeDarkStart isDarkTheme dynamicColor birthYear sex",
            "AktivitetJson" to "id timestamp datum tid aktivitet energy stress somatiska symptom aterhamtande energitjuv type spentTime",
            "MedicinJson" to "id timestamp datum tid namn dos enhet tidpunkt tagen anteckning receptId skipped tagenTid",
            "ReceptJson" to "id namn dos enhet tidpunkter tidpunkt upprepning dagar intervalDagar anteckning aktiv skapad startDatum " +
                "slutDatum dosperioder",
            "DosperiodJson" to "id startDatum slutDatum dos enhet",
            "FavoritJson" to "id namn dos enhet tidpunkt anteckning minTidMellan dispenseringsTid maxDoserPerDag isFavorite",
            "SjukdomsEpisodJson" to "id typ startDatum slutDatum anteckning timestamp",
            "SjukdomsIncheckningJson" to "id episodId datum tid svarighetsgrad symptom somatiska anteckning timestamp",
            "HandelseJson" to "id timestamp datum tid typ svarighetsgrad varaktighetMinuter triggers atgarder anteckning",
            "NoteJson" to "target entityId text",
            "ScreeningEventConfigJson" to "enabled time",
            "MedNotificationConfigJson" to "tidpunkt enabled time",
            "SymptomOptionBackup" to "name isFavorite",
        ).flatMap { (type, fields) -> fields.split(' ').map { "$type.$it" } }
    }
}
