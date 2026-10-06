package se.partee71.dagboken.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Rule
import org.junit.runner.RunWith
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.firestore.fromFirestore
import se.partee71.dagboken.data.firestore.toFirestore

/**
 * `CollectionContract` mot riktig `FirestoreCollection` och Firebase-emulatorn, med rules och
 * anonym inloggning i auth-emulatorn – en ny användare med egen Firestore-instans per test.
 * Körs bara när berörd kod ändrats (skill ci-budget).
 */
@RunWith(AndroidJUnit4::class)
class FirestoreCollectionContractTest : CollectionContract() {

    @get:Rule
    val emulator = FirebaseEmulator()

    /** Varje steg i `newUser` har en egen tidsgräns och säger vilket som inte svarade. */
    override fun createEnvironment(): Environment = runBlocking {
        val user = emulator.newUser()
        val scope = TestUserScope(user.uid)
        val clock = FixedClock()
        object : Environment {
            override val uid = user.uid
            override val now = clock.instant

            override fun <T : Identified> collection(codec: DocCodec<T>, name: String, path: (uid: String?) -> String) =
                user.collection(scope, codec, name, path, clock)

            override suspend fun writeRaw(path: String, id: String, doc: Doc) {
                user.db.collection(path).document(id).set(toFirestore(doc)).await()
            }

            override suspend fun readRaw(path: String, id: String): Doc? =
                user.db.collection(path).document(id).get().await().data?.let(::fromFirestore)

            override fun makeUserNewerThanApp() = scope.setVersion(Schema.CURRENT_VERSION + 1)

            override fun signOut() {
                scope.uid.value = null
            }

            override fun signInAgain() {
                scope.uid.value = user.uid
            }
        }
    }
}
