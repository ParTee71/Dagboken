package se.partee71.dagboken.data.export

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.core.importing.ImportBatches
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.FirebaseEmulator
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.UserFile
import se.partee71.dagboken.data.firestore.FirestoreRawDocuments
import se.partee71.dagboken.data.firestore.FirestoreRawWriter
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.importing.ImportRead
import se.partee71.dagboken.data.importing.ImportUseCase
import se.partee71.dagboken.data.importing.ImportWriteOutcome
import se.partee71.dagboken.testing.StuckTestTimeout

/**
 * Importen och exporten mot riktig Firestore i emulatorn med rules (BCK-6, BCK-13, BCK-16): 4.0-fixturen (varje fält
 * satt) som en export från ett annat konto skrivs av ägaren genom rules och ger exakt filens dokument, exporten är
 * `tools/db export`-formatet, och export → radera → import → export ger en identisk fil. SAF är en fil i minnet.
 * Syntetisk data (`tools/db/test/fixtures/user.json`).
 */
@RunWith(AndroidJUnit4::class)
class ExportImportRoundTripTest {

    @get:Rule(order = StuckTestTimeout.OUTERMOST)
    val timeout = StuckTestTimeout.rule()

    @get:Rule
    val emulator = FirebaseEmulator()

    private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(60_000) { block() } }

    private fun asset(name: String) = InstrumentationRegistry.getInstrumentation().context.assets.open(name).bufferedReader().readText()

    /** Dokumentväljarens fil i minnet. */
    private class MemoryFile : UserFile {
        val files = mutableMapOf<Uri, String>()

        override suspend fun write(uri: Uri, text: String) {
            files[uri] = text
        }

        override suspend fun read(uri: Uri): String = files.getValue(uri)

        override suspend fun displayName(uri: Uri): String? = uri.lastPathSegment
    }

    @Test
    fun exporten_av_ett_annat_konto_ger_filens_dokument_och_export_radera_import_ger_identisk_fil() = test {
        val user = emulator.newUser()
        val raw = FirestoreRawDocuments(user.firestore)
        val writer = FirestoreRawWriter(user.firestore)
        val files = MemoryFile()
        val scope = TestUserScope(user.uid)
        val importer = ImportUseCase(files, writer, raw, scope, Dispatchers.Default)
        val exporter = ExportUseCase(raw, files, scope, FixedClock(), Dispatchers.Default)

        // Fixturen utan incheckningen vars episod saknar dokument (den kan inte skrivas genom rules).
        val fixture = ExportFormat.decode(asset("user.json")).filterNot { "/utan-dokument/" in it.path }
        val source = Uri.parse("content://test/annat-konto.json")
        files.write(source, ExportFormat.encode(Instant.parse("2026-10-07T19:14:00Z"), Schema.CURRENT_VERSION, fixture))

        val plan = assertIs<ImportRead.Ready>(importer.readFile(source)).plan
        assertEquals(Schema.CURRENT_VERSION, plan.schemaVersion)
        assertIs<ImportWriteOutcome.Verified>(importer.write(plan))

        val first = Uri.parse("content://test/forsta.json")
        exporter.export(first).getOrThrow()
        val exported = ExportFormat.decode(files.read(first)).filterNot { it.path == Paths.user(user.uid) }
        val expected = fixture.filterNot { it.path == Paths.user(FIXTURE_UID) }
            .associate { it.path.replaceFirst(Paths.user(FIXTURE_UID), Paths.user(user.uid)) to ExportFormat.toJson(it.data) }
        assertEquals(expected, exported.associate { it.path to ExportFormat.toJson(it.data) }, "samma dokument som i filen, okända fält med")

        // Radera allt utom användardokumentet, incheckningarna före sina episoder.
        val paths = exported.map { it.path }.sortedByDescending { it.count { c -> c == '/' } }
        for (batch in paths.chunked(ImportBatches.MAX_WRITES)) writer.deleteBatch(batch).getOrThrow()
        assertEquals(emptyMap(), raw.documents(paths))

        val back = assertIs<ImportRead.Ready>(importer.readFile(first)).plan
        assertEquals(1, back.schemaVersion)
        assertIs<ImportWriteOutcome.Verified>(importer.write(back))

        val second = Uri.parse("content://test/andra.json")
        exporter.export(second).getOrThrow()
        assertEquals(files.read(first), files.read(second))
    }

    private companion object {
        /** Kontot i fixturen. */
        const val FIXTURE_UID = "uid-test"
    }
}
