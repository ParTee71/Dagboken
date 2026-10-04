package se.partee71.dagboken.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.runner.RunWith
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.firestore.fromFirestore
import se.partee71.dagboken.data.firestore.toFirestore

/**
 * `CollectionContract` mot riktig `FirestoreCollection` och Firebase-emulatorn, med rules och
 * anonym inloggning i auth-emulatorn. Körs bara när berörd kod ändrats (skill ci-budget).
 */
@RunWith(AndroidJUnit4::class)
class FirestoreCollectionContractTest : CollectionContract() {

    override fun createEnvironment(): Environment = runBlocking {
        val uid = FirebaseEmulator.newUser()
        val scope = TestUserScope(uid)
        val clock = FixedClock()
        val db = FirebaseEmulator.db
        object : Environment {
            override val uid = uid
            override val now = clock.instant

            override fun <T : Identified> collection(codec: DocCodec<T>, name: String, path: (uid: String?) -> String) =
                FirebaseEmulator.collection(scope, codec, name, path, clock)

            override suspend fun writeRaw(path: String, id: String, doc: Doc) {
                db.collection(path).document(id).set(toFirestore(doc)).await()
            }

            override suspend fun readRaw(path: String, id: String): Doc? =
                db.collection(path).document(id).get().await().data?.let(::fromFirestore)

            override fun makeUserNewerThanApp() = scope.setVersion(Schema.CURRENT_VERSION + 1)

            override fun signOut() {
                scope.uid.value = null
            }
        }
    }
}
