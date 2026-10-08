package se.partee71.dagboken.data.legacy

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import se.partee71.dagboken.core.legacy.ImportFormat
import se.partee71.dagboken.core.model.LegacySource
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.FakeStore
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.firestore.Paths

/**
 * Legacyimporten (BCK-6, BCK-14, OMB-5) mot fejkar: en 3.x-backup v1/v2 från fil eller Drive ger exakt grindens
 * dokument (OMB-4), skrivs i batchar och verifieras, en omkörning skriver inget nytt, samma id ersätts utan att något
 * tas bort, ett batchfel kan göras om, och stopp, fel fil, Drive utan backup eller utan samtycke skriver ingenting.
 * Syntetisk data.
 */
@RunWith(RobolectricTestRunner::class)
class LegacyImportUseCaseTest {
    private val uid = "uid-legacy"
    private val repoRoot = File("").absoluteFile.let { if (it.name == "app") it.parentFile else it }

    private fun fixture(name: String) = File(repoRoot, "tools/db/test/fixtures/legacy/$name").readText()

    /** Grindens förväntade dokument (OMB-4) utan användardokumentet. */
    private fun expected(name: String) = ExportFormat.decode(fixture("$name.expected.json")).filterNot { CollectionNames.collectionOf(it.path) == CollectionNames.USERS }

    private val store = FakeStore().apply { set(Paths.USERS, uid, mapOf("schemaVersion" to Schema.CURRENT_VERSION), merge = false) }
    private val firestore = FakeRawFirestore(store)
    private val drive = FakeDriveBackups()
    private val files = FakeCopyFile()
    private val pause = LegacyMigrationPause()
    private val scope = TestUserScope(uid)
    private val dispatcher = StandardTestDispatcher()
    private val importer = LegacyImportUseCase(drive, files, firestore, firestore, pause, scope, dispatcher)
    private val uri: Uri = Uri.parse("content://test/backup.json")

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private suspend fun planFromFile(text: String): ImportPlan {
        files.files[uri] = text
        return assertIs<ImportRead.Ready>(importer.readFile(uri)).plan
    }

    /** Det som ligger i fejken under användaren, som JSON per sökväg (heltal som Long, tidsstämplar som `__ts`). */
    private fun stored(): Map<String, Any?> = store.documents.value.filterKeys { it != Paths.USERS }
        .flatMap { (path, docs) -> docs.map { (id, doc) -> "$path/$id" to ExportFormat.toJson(doc) } }.toMap()

    private fun asJson(documents: List<ExportFormat.Document>) = documents.associate { it.path to ExportFormat.toJson(it.data) }

    private suspend fun importsLikeTheGate(name: String, version: Int) {
        val plan = planFromFile(fixture("$name.json"))
        val expected = expected(name)
        assertEquals(LegacySource.JSON, plan.source)
        assertEquals(ImportFormat.Legacy(version), plan.format)
        assertEquals(CollectionNames.countsOf(expected.map { it.path }), plan.counts)
        assertEquals(expected.size, plan.total)

        val outcome = assertIs<LegacyWriteOutcome.Verified>(importer.write(plan))
        assertEquals(plan.counts, outcome.before)
        assertEquals(plan.counts, outcome.after)
        assertEquals(asJson(expected), stored(), "samma dokument som grindens körning")
        assertFalse(pause.paused.value, "pausen släpps efter skrivningen")
    }

    @Test
    fun `en lokal 3x-backup v2 ger exakt grindens dokument, i batchar och verifierade`() = test {
        importsLikeTheGate("backup-v2", 2)
    }

    @Test
    fun `en lokal 3x-backup v1 ger exakt grindens dokument`() = test {
        importsLikeTheGate("backup-v1", 1)
    }

    @Test
    fun `påminnelserna pausas medan importen skriver`() = test {
        val plan = planFromFile(fixture("backup-v2.json"))
        var pausedDuring = false
        importer.write(plan) { pausedDuring = pausedDuring || pause.paused.value }
        assertTrue(pausedDuring)
        assertFalse(pause.paused.value)
    }

    @Test
    fun `en omkörning skriver inget nytt, och samma id ersätts utan att fält bara på servern tas bort`() = test {
        val plan = planFromFile(fixture("backup-v2.json"))
        importer.write(plan)
        val written = firestore.batches.size

        assertIs<LegacyWriteOutcome.Verified>(importer.write(plan))
        assertEquals(written, firestore.batches.size, "allt var redan lika – ingen batch")

        val dose = plan.documents.first { CollectionNames.collectionOf(it.path) == CollectionNames.DOSES }
        val collection = dose.path.substringBeforeLast('/')
        val id = dose.path.substringAfterLast('/')
        store.set(collection, id, store.read(collection, id)!! + mapOf("note" to "Ändrad i 4.0", "framtidaFält" to 7L), merge = false)

        assertEquals(1, planFromFile(fixture("backup-v2.json")).replaced, "granskningen räknar dokumentet som ersätts")
        assertEquals(0, plan.replaced, "i ett tomt konto ersätts ingenting")

        val outcome = assertIs<LegacyWriteOutcome.Verified>(importer.write(plan))
        assertEquals(plan.counts, outcome.after)
        assertEquals(written + 1, firestore.batches.size)
        assertEquals(listOf(dose.path), firestore.batches.last().map { it.path }, "bara dokumentet som skilde sig skrivs")
        val after = store.read(collection, id)!!
        assertEquals(dose.data["note"], after["note"], "filens värde ersätter det ändrade")
        assertEquals(7L, after["framtidaFält"], "importen tar aldrig bort något")
    }

    @Test
    fun `ett batchfel stannar med felkoden och Försök igen skriver resten utan dubbletter`() = test {
        val plan = planFromFile(fixture("backup-v2.json"))
        firestore.failAt = 0
        val failed = assertIs<LegacyWriteOutcome.Failed>(importer.write(plan))
        assertEquals("PERMISSION_DENIED", failed.code)
        assertEquals(0, failed.batch)
        assertTrue(stored().isEmpty())
        assertFalse(pause.paused.value)

        firestore.failAt = null
        assertIs<LegacyWriteOutcome.Verified>(importer.write(plan))
        assertEquals(asJson(expected("backup-v2")), stored())
    }

    @Test
    fun `något som inte landar på servern ger en avvikelse per samling`() = test {
        val plan = planFromFile(fixture("backup-v2.json"))
        val dose = plan.documents.first { CollectionNames.collectionOf(it.path) == CollectionNames.DOSES }
        firestore.tamper = { store.delete(dose.path.substringBeforeLast('/'), dose.path.substringAfterLast('/')) }
        val mismatch = assertIs<LegacyWriteOutcome.Mismatch>(importer.write(plan))
        assertEquals(mapOf(CollectionNames.DOSES to 1), mismatch.mismatched)
    }

    @Test
    fun `stopp, en fil som inte är någon backup och en fil som inte går att öppna skriver ingenting`() = test {
        files.files[uri] = """{"documents":[{"path":"users/annan/okand/x","data":{}}]}"""
        val stopped = assertIs<ImportRead.Stopped>(importer.readFile(uri))
        assertEquals(listOf("path"), stopped.report.problems.map { it.field })

        files.files[uri] = "inte en backup"
        assertSame(ImportRead.NotABackup, importer.readFile(uri))

        assertEquals(ImportRead.Failed(null), importer.readFile(Uri.parse("content://test/saknas.json")))
        assertTrue(firestore.batches.isEmpty())
    }

    @Test
    fun `Drive - backupen läses som filen, ingen backup, samtycke och fel går vidare utan att något skrivs`() = test {
        drive.read = DriveRead.Found(DriveBackupFile("id-1", "dagboken-backup-20260115-2100.json", "2026-01-15T20:00:00Z"), fixture("backup-v2.json"))
        val plan = assertIs<ImportRead.Ready>(importer.readDrive()).plan
        assertEquals(LegacySource.DRIVE, plan.source)
        assertEquals(ImportFormat.Legacy(2), plan.format)

        drive.read = DriveRead.NoBackup
        assertSame(ImportRead.NoDriveBackup, importer.readDrive())

        val consent = PendingIntent.getActivity(RuntimeEnvironment.getApplication(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        drive.read = DriveRead.NeedsConsent(consent)
        assertEquals(ImportRead.NeedsDriveConsent(consent), importer.readDrive())

        drive.read = DriveRead.Failed(DataError.Offline)
        assertEquals(ImportRead.Failed(DataError.Offline), importer.readDrive())
        assertTrue(firestore.batches.isEmpty())
    }

    @Test
    fun `köade skrivningar som inte når servern - granskningen och skrivningen stannar med Offline utan att skriva`() = test {
        val plan = planFromFile(fixture("backup-v2.json"))
        firestore.offline = true
        assertEquals(ImportRead.Failed(DataError.Offline), importer.readFile(uri))
        assertEquals("UNAVAILABLE", assertIs<LegacyWriteOutcome.Failed>(importer.write(plan)).code)
        assertTrue(firestore.batches.isEmpty())
    }

    @Test
    fun `utloggad, ett konto i ett annat format eller utan nät skrivs ingenting`() = test {
        val plan = planFromFile(fixture("backup-v1.json"))
        scope.setVersion(Schema.CURRENT_VERSION + 1)
        assertEquals("UPDATE_REQUIRED", assertIs<LegacyWriteOutcome.Failed>(importer.write(plan)).code)
        scope.setVersion(Schema.CURRENT_VERSION)

        firestore.offline = true
        assertEquals("UNAVAILABLE", assertIs<LegacyWriteOutcome.Failed>(importer.write(plan)).code)
        firestore.offline = false

        scope.uid.value = null
        assertEquals("UNAUTHENTICATED", assertIs<LegacyWriteOutcome.Failed>(importer.write(plan)).code)
        assertEquals(ImportRead.Failed(DataError.NotSignedIn), importer.readFile(uri))
        assertTrue(firestore.batches.isEmpty())
    }
}
