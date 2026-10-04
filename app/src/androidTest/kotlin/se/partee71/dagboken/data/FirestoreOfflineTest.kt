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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.user.EnsureUserUseCase
import se.partee71.dagboken.data.user.UserStatus
import se.partee71.dagboken.testing.StuckTestTimeout

/**
 * Offline först, användarens version och första inloggningen, mot riktig Firestore i emulatorn
 * (NFR-1). Varje användare har sin egen Firestore-instans ([FirebaseEmulator]).
 */
@RunWith(AndroidJUnit4::class)
class FirestoreOfflineTest {

    @get:Rule(order = StuckTestTimeout.OUTERMOST)
    val timeout = StuckTestTimeout.rule()

    @get:Rule
    val emulator = FirebaseEmulator()

    private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    @Test
    fun skrivning_offline_lyckas_direkt_syns_lokalt_och_synkas_nar_natet_kommer_tillbaka() = test {
        val user = emulator.newUser()
        val sync = FirebaseEmulator.sync()
        val items = user.collection(TestUserScope(user.uid), ContractItemCodec, Paths.OPTIONS, { Paths.options(it!!) }, sync = sync)
        user.db.disableNetwork().await()
        try {
            assertTrue(items.upsert(ContractItem("a", "Promenad")).isSuccess)
            assertEquals("Promenad", items.observe("a").first { it != null }?.name)
            assertTrue(sync.syncing.first { it })
        } finally {
            user.db.enableNetwork().await()
        }
        sync.syncing.first { !it }
        val server = user.db.collection(Paths.options(user.uid)).document("a").get(Source.SERVER).await()
        assertEquals("Promenad", server.getString("name"))
    }

    @Test
    fun anvandarens_version_lases_ur_dokumentet_nyare_kraver_uppdatering_och_saknat_dokument_ger_null() = test {
        val current = emulator.newUser()
        assertEquals(Schema.FIRST_VERSION, current.versions().schemaVersion(current.uid).first())

        val newer = emulator.newSignedInUser()
        FirebaseEmulator.seedUserBypassingRules(newer.uid, version = Schema.CURRENT_VERSION + 1)
        assertEquals(Schema.CURRENT_VERSION + 1, newer.versions().schemaVersion(newer.uid).first())
        val status = EnsureUserUseCase(newer.directory(), FixedClock())(newer.uid).getOrThrow()
        assertEquals(UserStatus.RequiresUpdate(newer.uid), status, "en nyare version kräver uppdatering")

        val fresh = emulator.newSignedInUser()
        assertNull(fresh.versions().schemaVersion(fresh.uid).first())
    }

    @Test
    fun forsta_inloggningen_skapar_dokumentet_en_gang_och_skriver_aldrig_over() = test {
        val user = emulator.newSignedInUser()
        EnsureUserUseCase(user.directory(), FixedClock())(user.uid).getOrThrow()
        val created = user.db.document(Paths.user(user.uid)).get(Source.SERVER).await()
        assertEquals(Schema.CURRENT_VERSION.toLong(), created.getLong("schemaVersion"))

        EnsureUserUseCase(user.directory(), FixedClock(kotlin.time.Instant.fromEpochSeconds(1)))(user.uid).getOrThrow()
        val again = user.db.document(Paths.user(user.uid)).get(Source.SERVER).await()
        assertEquals(created.getTimestamp("createdAt"), again.getTimestamp("createdAt"), "ett befintligt dokument skrivs inte över")
    }

    @Test
    fun forsta_inloggningen_utan_nat_ger_Offline() = test {
        val user = emulator.newSignedInUser()
        user.db.disableNetwork().await()
        try {
            assertEquals(DataError.Offline, EnsureUserUseCase(user.directory(), FixedClock())(user.uid).dataError())
        } finally {
            user.db.enableNetwork().await()
        }
    }
}
