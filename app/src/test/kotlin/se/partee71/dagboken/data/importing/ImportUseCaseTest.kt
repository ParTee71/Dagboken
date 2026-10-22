package se.partee71.dagboken.data.importing

import android.net.Uri
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.FakeStore
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.firestore.Paths

/**
 * Importen (BCK-6) mot fejkar: en 4.0-export från fil ger exakt filens dokument under kontot, skrivs i batchar och
 * verifieras, en omkörning skriver inget nytt, samma id ersätts utan att något tas bort, ett batchfel kan göras om, och
 * stopp, en 3.x-backup, fel fil eller en fil som inte går att öppna skriver ingenting. Syntetisk data.
 */
@RunWith(RobolectricTestRunner::class)
class ImportUseCaseTest {
    private val uid = "uid-import"
    private val store = FakeStore().apply { set(Paths.USERS, uid, mapOf("schemaVersion" to Schema.CURRENT_VERSION), merge = false) }
    private val firestore = FakeRawFirestore(store)
    private val files = FakeUserFile()
    private val scope = TestUserScope(uid)
    private val dispatcher = StandardTestDispatcher()
    private val importer = ImportUseCase(files, firestore, firestore, scope, dispatcher)
    private val uri: Uri = Uri.parse("content://test/dagboken-export.json")
    private val expected = ImportFixtures.imported(uid)

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private suspend fun planFromFile(text: String = ImportFixtures.exportText): ImportPlan {
        files.files[uri] = text
        return assertIs<ImportRead.Ready>(importer.readFile(uri)).plan
    }

    /** Det som ligger i fejken under användaren, som JSON per sökväg (heltal som Long, tidsstämplar som `__ts`). */
    private fun stored(): Map<String, Any?> = store.documents.value.filterKeys { it != Paths.USERS }
        .flatMap { (path, docs) -> docs.map { (id, doc) -> "$path/$id" to ExportFormat.toJson(doc) } }.toMap()

    private fun asJson(documents: List<ExportFormat.Document>) = documents.associate { it.path to ExportFormat.toJson(it.data) }

    @Test
    fun `en 4_0-export ger exakt filens dokument under kontot, i batchar och verifierade`() = test {
        val plan = planFromFile()
        assertEquals(Schema.CURRENT_VERSION, plan.schemaVersion)
        assertEquals(CollectionNames.countsOf(expected.map { it.path }), plan.counts)
        assertEquals(expected.size, plan.total)

        val outcome = assertIs<ImportWriteOutcome.Verified>(importer.write(plan))
        assertEquals(plan.counts, outcome.before)
        assertEquals(plan.counts, outcome.after)
        assertEquals(asJson(expected), stored(), "samma dokument som i filen, okända fält med")
    }

    @Test
    fun `en omkörning skriver inget nytt, och samma id ersätts utan att fält bara på servern tas bort`() = test {
        val plan = planFromFile()
        importer.write(plan)
        val written = firestore.batches.size

        assertIs<ImportWriteOutcome.Verified>(importer.write(plan))
        assertEquals(written, firestore.batches.size, "allt var redan lika – ingen batch")

        val dose = plan.documents.first { CollectionNames.collectionOf(it.path) == CollectionNames.DOSES }
        val collection = dose.path.substringBeforeLast('/')
        val id = dose.path.substringAfterLast('/')
        store.set(collection, id, store.read(collection, id)!! + mapOf("note" to "Ändrad i appen", "framtidaFält" to 7L), merge = false)

        assertEquals(1, planFromFile().replaced, "granskningen räknar dokumentet som ersätts")
        assertEquals(0, plan.replaced, "i ett tomt konto ersätts ingenting")

        val outcome = assertIs<ImportWriteOutcome.Verified>(importer.write(plan))
        assertEquals(plan.counts, outcome.after)
        assertEquals(written + 1, firestore.batches.size)
        assertEquals(listOf(dose.path), firestore.batches.last().map { it.path }, "bara dokumentet som skilde sig skrivs")
        val after = store.read(collection, id)!!
        assertEquals(ExportFormat.toJson(dose.data["note"]), ExportFormat.toJson(after["note"]), "filens värde ersätter det ändrade")
        assertEquals(7L, after["framtidaFält"], "importen tar aldrig bort något")
    }

    @Test
    fun `ett batchfel stannar med felkoden och Försök igen skriver resten utan dubbletter`() = test {
        val plan = planFromFile()
        firestore.failAt = 0
        val failed = assertIs<ImportWriteOutcome.Failed>(importer.write(plan))
        assertEquals("PERMISSION_DENIED", failed.code)
        assertEquals(0, failed.batch)
        assertTrue(stored().isEmpty())

        firestore.failAt = null
        assertIs<ImportWriteOutcome.Verified>(importer.write(plan))
        assertEquals(asJson(expected), stored())
    }

    @Test
    fun `något som inte landar på servern ger en avvikelse per samling`() = test {
        val plan = planFromFile()
        val dose = plan.documents.first { CollectionNames.collectionOf(it.path) == CollectionNames.DOSES }
        firestore.tamper = { store.delete(dose.path.substringBeforeLast('/'), dose.path.substringAfterLast('/')) }
        val mismatch = assertIs<ImportWriteOutcome.Mismatch>(importer.write(plan))
        assertEquals(mapOf(CollectionNames.DOSES to 1), mismatch.mismatched)
    }

    @Test
    fun `stopp, en 3x-backup, en fil som inte är någon export och en fil som inte går att öppna skriver ingenting`() = test {
        files.files[uri] = """{"documents":[{"path":"users/annan/okand/x","data":{}}]}"""
        val stopped = assertIs<ImportRead.Stopped>(importer.readFile(uri))
        assertEquals(listOf("path"), stopped.problems.map { it.field })

        files.files[uri] = """{"version":2,"createdAt":"2026-01-15T21:00:00","aktiviteter":[],"handelser":[]}"""
        assertSame(ImportRead.NotABackup, importer.readFile(uri), "3.x-backuper importeras inte sedan 4.9.0")

        files.files[uri] = "inte en backup"
        assertSame(ImportRead.NotABackup, importer.readFile(uri))

        assertEquals(ImportRead.Failed(null), importer.readFile(Uri.parse("content://test/saknas.json")))
        assertTrue(firestore.batches.isEmpty())
    }

    @Test
    fun `köade skrivningar som inte når servern - granskningen och skrivningen stannar med Offline utan att skriva`() = test {
        val plan = planFromFile()
        firestore.offline = true
        assertEquals(ImportRead.Failed(DataError.Offline), importer.readFile(uri))
        assertEquals("UNAVAILABLE", assertIs<ImportWriteOutcome.Failed>(importer.write(plan)).code)
        assertTrue(firestore.batches.isEmpty())
    }

    @Test
    fun `utloggad, ett konto i ett annat format eller utan nät skrivs ingenting`() = test {
        val plan = planFromFile()
        scope.setVersion(Schema.CURRENT_VERSION + 1)
        assertEquals("UPDATE_REQUIRED", assertIs<ImportWriteOutcome.Failed>(importer.write(plan)).code)
        scope.setVersion(Schema.CURRENT_VERSION)

        firestore.offline = true
        assertEquals("UNAVAILABLE", assertIs<ImportWriteOutcome.Failed>(importer.write(plan)).code)
        firestore.offline = false

        scope.uid.value = null
        assertEquals("UNAUTHENTICATED", assertIs<ImportWriteOutcome.Failed>(importer.write(plan)).code)
        assertEquals(ImportRead.Failed(DataError.NotSignedIn), importer.readFile(uri))
        assertTrue(firestore.batches.isEmpty())
    }
}
