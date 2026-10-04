package se.partee71.dagboken.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.user.EnsureUserUseCase

/** Offline först, användarens version och första inloggningen, mot riktig Firestore i emulatorn (NFR-1). */
@RunWith(AndroidJUnit4::class)
class FirestoreOfflineTest {

    private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    @Test
    fun skrivning_offline_lyckas_direkt_syns_lokalt_och_synkas_nar_natet_kommer_tillbaka() = test {
        val uid = FirebaseEmulator.newUser()
        val sync = FirebaseEmulator.sync()
        val items = FirebaseEmulator.collection(TestUserScope(uid), ContractItemCodec, Paths.OPTIONS, { Paths.options(it!!) }, sync = sync)
        FirebaseEmulator.db.disableNetwork().await()
        try {
            assertTrue(items.upsert(ContractItem("a", "Promenad")).isSuccess)
            assertEquals("Promenad", items.observe("a").first { it != null }?.name)
            assertTrue(sync.syncing.first { it })
        } finally {
            FirebaseEmulator.db.enableNetwork().await()
        }
        sync.syncing.first { !it }
        val server = FirebaseEmulator.db.collection(Paths.options(uid)).document("a").get(Source.SERVER).await()
        assertEquals("Promenad", server.getString("name"))
    }

    @Test
    fun anvandarens_version_lases_ur_dokumentet_och_saknat_dokument_ger_null() = test {
        val versions = FirebaseEmulator.versions()
        val current = FirebaseEmulator.newUser()
        assertEquals(Schema.CURRENT_VERSION, versions.schemaVersion(current).first())
        val newer = FirebaseEmulator.newUser(version = Schema.CURRENT_VERSION + 1)
        assertEquals(Schema.CURRENT_VERSION + 1, versions.schemaVersion(newer).first())
        val fresh = FirebaseEmulator.newUid()
        assertNull(versions.schemaVersion(fresh).first())
    }

    @Test
    fun forsta_inloggningen_skapar_dokumentet_en_gang_och_skriver_aldrig_over() = test {
        val uid = FirebaseEmulator.newUid()
        val ensure = EnsureUserUseCase(FirebaseEmulator.directory(), FixedClock())
        ensure(uid).getOrThrow()
        val created = FirebaseEmulator.db.document(Paths.user(uid)).get(Source.SERVER).await()
        assertEquals(Schema.CURRENT_VERSION.toLong(), created.getLong("schemaVersion"))

        EnsureUserUseCase(FirebaseEmulator.directory(), FixedClock(kotlin.time.Instant.fromEpochSeconds(1))).invoke(uid).getOrThrow()
        val again = FirebaseEmulator.db.document(Paths.user(uid)).get(Source.SERVER).await()
        assertEquals(created.getTimestamp("createdAt"), again.getTimestamp("createdAt"), "ett befintligt dokument skrivs inte över")
    }

    @Test
    fun forsta_inloggningen_utan_nat_ger_Offline() = test {
        val uid = FirebaseEmulator.newUid()
        FirebaseEmulator.db.disableNetwork().await()
        try {
            assertEquals(DataError.Offline, EnsureUserUseCase(FirebaseEmulator.directory(), FixedClock())(uid).dataError())
        } finally {
            FirebaseEmulator.db.enableNetwork().await()
        }
    }
}
