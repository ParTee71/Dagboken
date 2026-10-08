package se.partee71.dagboken.data.legacy

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.core.legacy.MigrationBatches
import se.partee71.dagboken.core.model.LegacyMigration
import se.partee71.dagboken.core.model.LegacySource
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.LegacyMigrationCodec
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.data.FirebaseEmulator
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.firestore.FirestoreRawDocuments
import se.partee71.dagboken.data.firestore.FirestoreRawWriter
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.testing.StuckTestTimeout

/**
 * Batchskrivaren och verifieringen mot riktig Firestore i emulatorn med rules (OMB-2, OMB-7): konverterarens
 * dokument skrivs av ägaren i `MigrationBatches` form (episoden med sina incheckningar), läses tillbaka från servern
 * fält för fält, skrivs igen utan dubbletter, och markören sätts en gång – en andra nekas av rules.
 */
@RunWith(AndroidJUnit4::class)
class LegacyMigrationWriterTest {

    @get:Rule(order = StuckTestTimeout.OUTERMOST)
    val timeout = StuckTestTimeout.rule()

    @get:Rule
    val emulator = FirebaseEmulator()

    private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(40_000) { block() } }

    /** Konverterarens dokument för fixturanvändaren, flyttade till [uid], utan `users/{uid}`. */
    private fun documentsFor(uid: String): List<ExportFormat.Document> {
        val text = InstrumentationRegistry.getInstrumentation().context.assets.open("backup-v2.expected.json").bufferedReader().readText()
        return ExportFormat.decode(text)
            .filterNot { CollectionNames.collectionOf(it.path) == CollectionNames.USERS }
            .map { ExportFormat.Document(it.path.replaceFirst("users/uid-legacy", Paths.user(uid)), it.data) }
    }

    private fun matches(expected: Map<String, Any?>, stored: Map<String, Any?>) =
        expected.all { (field, value) -> ExportFormat.toJson(value) == ExportFormat.toJson(stored[field]) }

    @Test
    fun dokumenten_skrivs_i_batchar_genom_rules_verifieras_fran_servern_och_skrivs_om_utan_dubbletter() = test {
        val user = emulator.newUser()
        val writer = FirestoreRawWriter(user.firestore)
        val raw = FirestoreRawDocuments(user.firestore)
        val documents = documentsFor(user.uid)
        suspend fun run() {
            for (batch in MigrationBatches.plan(documents)) writer.writeBatch(batch).getOrThrow()
        }
        run()
        val verify = suspend {
            // Som migreringen: på id, i grupper, parallellt – inte hela samlingar.
            val stored = raw.documents(documents.map { it.path })
            assertEquals(documents.size, stored.size)
            for (document in documents) assertTrue(matches(document.data, assertNotNull(stored[document.path])), document.path)
            assertEquals(emptyMap(), raw.documents(listOf("${Paths.user(user.uid)}/doses/finns-inte")), "ett id som saknas finns inte med")
        }
        verify()
        run() // avbruten körning som görs om
        verify()
    }

    @Test
    fun markoren_satts_en_gang_och_en_andra_nekas_av_rules() = test {
        val user = emulator.newUser()
        val writer = FirestoreRawWriter(user.firestore)
        val raw = FirestoreRawDocuments(user.firestore)
        val marker = LegacyMigration(source = LegacySource.ROOM, appVersion = "4.0.0", counts = mapOf("doses" to 4))
        val encoded = asDoc(LegacyMigrationCodec.encode(marker)) - LegacyMigrationCodec.COMPLETED_AT
        writer.markLegacyMigration(user.uid, encoded).getOrThrow()
        val stored = assertNotNull(LegacyMigrationCodec.decode(raw.document(Paths.user(user.uid))?.get(LegacyMigrationCodec.FIELD)))
        assertNotNull(stored.completedAt, "servern satte tiden")
        assertEquals(marker.copy(completedAt = stored.completedAt), stored)
        assertEquals(DataError.PermissionDenied, writer.markLegacyMigration(user.uid, encoded).exceptionOrNull())
    }
}
