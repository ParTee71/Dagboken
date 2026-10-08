package se.partee71.dagboken.data.export

import android.net.Uri
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.legacy.ImportFormat
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.data.FakeStore
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.legacy.FakeCopyFile
import se.partee71.dagboken.data.legacy.FakeDriveBackups
import se.partee71.dagboken.data.legacy.FakeRawFirestore
import se.partee71.dagboken.data.legacy.ImportRead
import se.partee71.dagboken.data.legacy.LegacyImportUseCase
import se.partee71.dagboken.data.legacy.LegacyMigrationPause
import se.partee71.dagboken.data.legacy.LegacyWriteOutcome

/**
 * Manuell export (BCK-13, HLS-5) och rundturen export → radera → import → export (BCK-6, BCK-16) mot fejkarna: hela
 * kontot rått i `tools/db export`-formatet (okända fält med), i `walk.mjs`-ordning, inget utanför `Paths`, köade
 * skrivningar väntas in först. Testdata = `tools/db/test/fixtures/user.json` (syntetisk), flyttad till testkontot.
 */
@RunWith(RobolectricTestRunner::class)
class ExportUseCaseTest {
    private val uid = "uid-export"
    private val repoRoot = File("").absoluteFile.let { if (it.name == "app") it.parentFile else it }
    private val clock = FixedClock(Instant.parse("2026-10-07T19:14:00Z"))

    /** Fixturen under [uid], utan incheckningen vars episod saknar dokument (klienten kan inte hitta den – se ARKITEKTUR.md → Migrering). */
    private val fixture = ExportFormat.decode(File(repoRoot, "tools/db/test/fixtures/user.json").readText())
        .filterNot { "/utan-dokument/" in it.path }
        .map { ExportFormat.Document(it.path.replace("users/uid-test", Paths.user(uid)), it.data) }

    private val store = FakeStore().apply {
        for (document in fixture) set(document.path.substringBeforeLast('/'), document.path.substringAfterLast('/'), document.data, merge = false)
        // En samling utanför Paths (som Health Connect-data aldrig får bli, HLS-5) följer inte med.
        set("${Paths.user(uid)}/halsa", "idag", mapOf("puls" to 61L), merge = false)
    }
    private val firestore = FakeRawFirestore(store)
    private val files = FakeCopyFile()
    private val scope = TestUserScope(uid)
    private val dispatcher = StandardTestDispatcher()
    private val exporter = ExportUseCase(firestore, files, scope, clock, dispatcher)
    private val importer = LegacyImportUseCase(FakeDriveBackups(), files, firestore, firestore, LegacyMigrationPause(), scope, dispatcher)
    private val uri: Uri = Uri.parse("content://test/dagboken-export-2026-10-07.json")

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    /** Fixturen i `walk.mjs`-ordning: användaren, samlingarna i Datamodell-ordning med id sorterade, incheckningar efter sin episod. */
    private val walkOrder: List<String> = buildList {
        add(Paths.user(uid))
        for (name in Paths.USER_COLLECTIONS) {
            for (document in fixture.filter { it.path.split('/').size == 4 && it.path.split('/')[2] == name }.sortedBy { it.path }) {
                add(document.path)
                fixture.filter { it.path.startsWith("${document.path}/") }.sortedBy { it.path }.forEach { add(it.path) }
            }
        }
    }

    @Test
    fun `exporten är hela kontot rått i tools-db-formatet, i walk-ordning, och inget utanför Paths`() = test {
        val summary = exporter.export(uri).getOrThrow()
        val text = files.files.getValue(uri)
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals("2026-10-07T19:14:00.000Z", root.getValue("exportedAt").jsonPrimitive.content)
        assertEquals(1, root.getValue("schemaVersion").jsonPrimitive.content.toInt())
        val documents = ExportFormat.decode(text)
        assertEquals(walkOrder, documents.map { it.path })
        assertEquals(fixture.associate { it.path to ExportFormat.toJson(it.data) }, documents.associate { it.path to ExportFormat.toJson(it.data) }, "rått: okända fält och tidsstämplar som de ligger")
        assertTrue(documents.none { "/halsa/" in it.path })
        assertEquals(CollectionNames.countsOf(fixture.map { it.path }), summary.counts)
        assertEquals(fixture.size - 1, summary.total, "användardokumentet räknas inte som en post")
    }

    @Test
    fun `rundtur - export, radera, import och export igen ger identisk fil`() = test {
        exporter.export(uri).getOrThrow()
        val first = files.files.getValue(uri)

        // Radera allt utom användardokumentet (det finns sedan inloggningen och skrivs aldrig av importen).
        store.documents.value = store.documents.value.filterKeys { it == Paths.USERS }
        val plan = assertIs<ImportRead.Ready>(importer.readFile(uri)).plan
        assertEquals(ImportFormat.Export(1), plan.format)
        assertEquals(fixture.size - 1, plan.total)
        assertIs<LegacyWriteOutcome.Verified>(importer.write(plan))

        val again = Uri.parse("content://test/igen.json")
        exporter.export(again).getOrThrow()
        assertEquals(first, files.files.getValue(again))
    }

    @Test
    fun `köade skrivningar som inte når servern ger Offline och ingen fil, en fil som inte går att skriva Unknown`() = test {
        firestore.offline = true
        assertEquals(DataError.Offline, exporter.export(uri).exceptionOrNull())
        assertTrue(files.files.isEmpty())

        firestore.offline = false
        files.writeFails = true
        assertEquals(DataError.Unknown, exporter.export(uri).exceptionOrNull())

        scope.uid.value = null
        assertEquals(DataError.NotSignedIn, exporter.export(uri).exceptionOrNull())
    }

    @Test
    fun `filnamnet är dagboken-export med dagens datum`() {
        assertEquals("dagboken-export-2026-10-07.json", exporter.fileName(TimeZone.of("Europe/Stockholm")))
    }
}
