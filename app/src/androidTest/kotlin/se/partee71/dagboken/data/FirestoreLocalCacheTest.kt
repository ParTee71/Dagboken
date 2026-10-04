package se.partee71.dagboken.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.firestore.Paths

/** Utloggningens tömning av cachen (AUTH-6) mot riktig Firestore i emulatorn. */
@RunWith(AndroidJUnit4::class)
class FirestoreLocalCacheTest {

    private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    @Test
    fun tomd_cache_saknar_lokala_dokument_och_nya_instansen_fungerar_mot_servern() = test {
        val uid = FirebaseEmulator.newUser()
        val doc = FirebaseEmulator.db.collection(Paths.options(uid)).document("a")
        doc.set(mapOf("name" to "Promenad")).await()
        assertEquals("Promenad", doc.get(Source.CACHE).await().getString("name"))

        FirebaseEmulator.firestore.clear().getOrThrow()

        val fresh = FirebaseEmulator.db.collection(Paths.options(uid)).document("a")
        assertFailsWith<FirebaseFirestoreException> { fresh.get(Source.CACHE).await() }
        assertEquals("Promenad", fresh.get(Source.SERVER).await().getString("name"))
    }

    @Test
    fun osynkad_skrivning_halls_kvar_tills_den_natt_servern() = test {
        val uid = FirebaseEmulator.newUser()
        val firestore = FirebaseEmulator.firestore
        firestore.db.disableNetwork().await()
        try {
            firestore.db.collection(Paths.options(uid)).document("b").set(mapOf("name" to "Yoga"))
            assertFalse(firestore.awaitPendingWrites(1.seconds))
        } finally {
            firestore.db.enableNetwork().await()
        }
        assertTrue(firestore.awaitPendingWrites(10.seconds))
    }
}
