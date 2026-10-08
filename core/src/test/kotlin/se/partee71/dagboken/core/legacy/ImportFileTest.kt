package se.partee71.dagboken.core.legacy

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.junit.Test
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.core.schema.TextLimits

/**
 * Importens läsare (BCK-6, BCK-14, OMB-5): en 3.x-backup v1/v2 ger exakt konverterarens dokument (samma som grinden
 * OMB-4), en 4.0-export i `tools/db`-formatet ger sina dokument under det inloggade kontot med okända fält kvar, och
 * allt som rules inte skulle godta stoppar hela filen med en rapport utan innehåll. Syntetisk data.
 */
class ImportFileTest {
    private val uid = "uid-import"
    private val userJson = File("../tools/db/test/fixtures/user.json").readText()
    private val fixtureDocs = ExportFormat.decode(userJson)
    private val fixtureUid = "uid-test"

    private fun export(documents: List<ExportFormat.Document>, schemaVersion: Int = Schema.CURRENT_VERSION) =
        ExportFormat.encode(Instant.parse("2026-10-07T19:14:00Z"), schemaVersion, documents)

    /** Fixturen utan incheckningen vars episod saknar dokument (den kan inte skrivas genom rules). */
    private val withoutOrphan = fixtureDocs.filterNot { "/utan-dokument/" in it.path }

    @Test
    fun `en 3x-backup v2 och v1 ger exakt konverterarens dokument utan användardokumentet`() {
        for ((name, version) in listOf("backup-v2" to 2, "backup-v1" to 1)) {
            val ready = assertIs<ImportFileResult.Ready>(ImportFile.read(LegacyFixtures.text("$name.json"), LegacyFixtures.UID))
            assertEquals(ImportFormat.Legacy(version), ready.format)
            val expected = LegacyFixtures.expected(name).filterNot { CollectionNames.collectionOf(it.path) == CollectionNames.USERS }
            // Som JSON: konverteraren ger Int där filen läses som Long.
            assertEquals(expected.map(ExportFormat::document), ready.documents.map(ExportFormat::document), name)
            assertEquals(CollectionNames.countsOf(expected.map { it.path }), ready.counts)
            assertTrue(CollectionNames.USERS !in ready.counts)
        }
    }

    @Test
    fun `en 3x-backup utan version (v1 skrev inte standardvärdet) känns igen på sina andra nycklar`() {
        val ready = assertIs<ImportFileResult.Ready>(ImportFile.read("""{"createdAt":"2026-01-15T21:00:00","aktiviteter":[]}""", uid))
        assertEquals(ImportFormat.Legacy(1), ready.format)
    }

    @Test
    fun `en 4_0-export ger sina dokument under det inloggade kontot, okända fält kvar och användardokumentet orört`() {
        val ready = assertIs<ImportFileResult.Ready>(ImportFile.read(export(withoutOrphan), uid))
        assertEquals(ImportFormat.Export(Schema.CURRENT_VERSION), ready.format)
        val expected = withoutOrphan
            .filterNot { it.path == "users/$fixtureUid" }
            .map { ExportFormat.Document(it.path.replace("users/$fixtureUid/", "users/$uid/"), it.data) }
            .sortedBy { it.path }
        assertEquals(expected, ready.documents)
        assertEquals(1, ready.counts[CollectionNames.CHECKINS])
        assertTrue(ready.documents.none { it.path.startsWith("users/$fixtureUid") })
    }

    @Test
    fun `tools-db-exporten läses som den är och en incheckning utan sin episod stoppar hela filen`() {
        val stopped = assertIs<ImportFileResult.Stopped>(ImportFile.read(userJson, uid))
        assertEquals(
            listOf(Problem("illnessEpisodes/utan-dokument/checkins/b1c2d3e4-f5a6-4b7c-9d8e-0f1a2b3c4d5e", "episod", "episoden saknas i filen")),
            stopped.report.problems,
        )
    }

    @Test
    fun `nyare schemaVersion än appen stoppar innan något läses`() {
        val user = withoutOrphan.first { it.path == "users/$fixtureUid" }
        val newer = listOf(user.copy(data = user.data + ("schemaVersion" to (Schema.CURRENT_VERSION + 1).toLong()))) + withoutOrphan.drop(1)
        val stopped = assertIs<ImportFileResult.Stopped>(ImportFile.read(export(newer), uid))
        assertEquals(ImportFormat.Export(Schema.CURRENT_VERSION + 1), stopped.format)
        assertEquals(listOf("schemaVersion"), stopped.report.problems.map { it.field })
        assertTrue(stopped.report.counts.isEmpty())
    }

    @Test
    fun `rules-brott, okända samlingar, ogiltiga id, dubbletter och flera användare stoppar med alla fel och utan innehåll`() {
        val secret = "Hemlig anteckning ".repeat(TextLimits.LONG)
        val activity = withoutOrphan.first { CollectionNames.collectionOf(it.path) == CollectionNames.ACTIVITIES }
        val option = withoutOrphan.first { CollectionNames.collectionOf(it.path) == CollectionNames.OPTIONS }
        val broken = withoutOrphan + listOf(
            activity.copy(path = "users/$fixtureUid/activities/lang", data = activity.data + ("note" to secret)),
            option.copy(path = "users/$fixtureUid/options/symptom-hemligt", data = option.data + ("sortOrder" to "inte ett tal")),
            ExportFormat.Document("users/$fixtureUid/okand/x", mapOf("a" to 1L)),
            ExportFormat.Document("users/$fixtureUid/doses/__reserverat__", mapOf()),
            activity,
            ExportFormat.Document("users/annan/activities/y", activity.data),
        )
        val stopped = assertIs<ImportFileResult.Stopped>(ImportFile.read(export(broken), uid))
        val problems = stopped.report.problems
        assertTrue(problems.any { it.path == "users" && it.field == "uid" }, "flera användare")
        assertTrue(problems.any { it.path == "activities/lang" && it.field == "note" }, "texten ryms inte")
        assertTrue(problems.any { it.path.startsWith("options#") && it.field == "sortOrder" }, "alternativ anges som plats, inte namn")
        assertTrue(problems.any { it.field == "path" }, "okänd samling")
        assertTrue(problems.any { it.field == "id" && it.reason == "ogiltigt id" })
        assertTrue(problems.any { it.reason.startsWith("dubblett") })
        val rendered = stopped.report.render()
        assertTrue("Hemlig" !in rendered && "hemligt" !in rendered, "rapporten citerar aldrig innehåll eller alternativnamn")
    }

    @Test
    fun `exportens metadata med fel typ kraschar inte - schemaVersion och exportedAt som objekt eller lista`() {
        val ready = assertIs<ImportFileResult.Ready>(ImportFile.read("""{"schemaVersion":{"a":1},"exportedAt":[1],"documents":[]}""", uid))
        assertEquals(ImportFormat.Export(Schema.FIRST_VERSION), ready.format)
        assertEquals("", ready.report.createdAt)
        assertTrue(ready.documents.isEmpty())
        val stopped = assertIs<ImportFileResult.Stopped>(ImportFile.read("""{"schemaVersion":[2],"documents":[{"path":"users/x/okand/y","data":{}}]}""", uid))
        assertEquals(listOf("path"), stopped.report.problems.map { it.field })
    }

    @Test
    fun `3x känns igen bara på sina datalistor - version, notes och settings ensamma räcker inte`() {
        for (text in listOf("""{"version":2}""", """{"version":2,"notes":[],"settings":{}}""", """{"createdAt":"2026-01-15T21:00:00","aktiviteter":{}}""")) {
            assertSame(ImportFileResult.NotABackup, ImportFile.read(text, uid), text)
        }
        assertIs<ImportFileResult.Ready>(ImportFile.read("""{"version":2,"handelser":[]}""", uid))
        // Varje signaturnyckel är en lista i BackupJson – annars kunde en riktig backup missas.
        val descriptor = BackupJson.serializer().descriptor
        for (key in ImportFile.LEGACY_DATA_KEYS) {
            val index = descriptor.getElementIndex(key)
            assertTrue(index >= 0, key)
            assertEquals(kotlinx.serialization.descriptors.StructureKind.LIST, descriptor.getElementDescriptor(index).kind, key)
        }
    }

    @Test
    fun `en fil som inte är någon backup känns igen utan att innehållet visas`() {
        for (text in listOf("inte json alls", "[]", """{"annat":1}""", """{"documents":"fel"}""", """{"aktiviteter":"fel"}""")) {
            assertSame(ImportFileResult.NotABackup, ImportFile.read(text, uid), text)
        }
    }
}
