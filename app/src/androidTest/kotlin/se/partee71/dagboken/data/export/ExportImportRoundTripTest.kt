package se.partee71.dagboken.data.export

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.core.legacy.ImportFormat
import se.partee71.dagboken.core.legacy.MigrationBatches
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.data.FirebaseEmulator
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.UserFile
import se.partee71.dagboken.data.firestore.FirestoreRawDocuments
import se.partee71.dagboken.data.firestore.FirestoreRawWriter
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.legacy.DriveBackupFile
import se.partee71.dagboken.data.legacy.DriveBackups
import se.partee71.dagboken.data.legacy.DriveRead
import se.partee71.dagboken.data.legacy.ImportRead
import se.partee71.dagboken.data.legacy.LegacyImportUseCase
import se.partee71.dagboken.data.legacy.LegacyMigrationPause
import se.partee71.dagboken.data.legacy.LegacyWriteOutcome
import se.partee71.dagboken.testing.StuckTestTimeout

/**
 * Legacyimporten och exporten mot riktig Firestore i emulatorn med rules (BCK-6, BCK-13, BCK-14, BCK-16, OMB-5): en
 * 3.x-backup från "Drive" (fejk – inget konto i emulatorn) skrivs av ägaren genom rules och ger grindens dokument,
 * exporten är `tools/db export`-formatet, och export → radera → import → export ger en identisk fil. SAF är en fil i
 * minnet. Syntetisk data (`tools/db/test/fixtures/legacy`).
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

    private class FakeDrive(private val text: String) : DriveBackups {
        override suspend fun downloadLatestBackup(): DriveRead = DriveRead.Found(DriveBackupFile("id", "dagboken-backup-20260115-2100.json", ""), text)
    }

    @Test
    fun drive_backupen_ger_grindens_dokument_och_export_radera_import_ger_identisk_fil() = test {
        val user = emulator.newUser()
        val raw = FirestoreRawDocuments(user.firestore)
        val writer = FirestoreRawWriter(user.firestore)
        val files = MemoryFile()
        val scope = TestUserScope(user.uid)
        val importer = LegacyImportUseCase(FakeDrive(asset("backup-v2.json")), files, writer, raw, LegacyMigrationPause(), scope, Dispatchers.Default)
        val exporter = ExportUseCase(raw, files, scope, FixedClock(), Dispatchers.Default)

        val plan = assertIs<ImportRead.Ready>(importer.readDrive()).plan
        assertEquals(ImportFormat.Legacy(2), plan.format)
        assertIs<LegacyWriteOutcome.Verified>(importer.write(plan))

        val first = Uri.parse("content://test/forsta.json")
        exporter.export(first).getOrThrow()
        val exported = ExportFormat.decode(files.read(first)).filterNot { it.path == Paths.user(user.uid) }
        val gate = ExportFormat.decode(asset("backup-v2.expected.json"))
            .filterNot { CollectionNames.collectionOf(it.path) == CollectionNames.USERS }
            .associate { it.path.replaceFirst("users/uid-legacy", Paths.user(user.uid)) to ExportFormat.toJson(it.data) }
        assertEquals(gate, exported.associate { it.path to ExportFormat.toJson(it.data) }, "samma resultat som grindens körning (OMB-4)")

        // Radera allt utom användardokumentet, incheckningarna före sina episoder.
        val paths = exported.map { it.path }.sortedByDescending { it.count { c -> c == '/' } }
        for (batch in paths.chunked(MigrationBatches.MAX_WRITES)) writer.deleteBatch(batch).getOrThrow()
        assertEquals(emptyMap(), raw.documents(paths))

        val back = assertIs<ImportRead.Ready>(importer.readFile(first)).plan
        assertEquals(ImportFormat.Export(1), back.format)
        assertIs<LegacyWriteOutcome.Verified>(importer.write(back))

        val second = Uri.parse("content://test/andra.json")
        exporter.export(second).getOrThrow()
        assertEquals(files.read(first), files.read(second))
    }
}
