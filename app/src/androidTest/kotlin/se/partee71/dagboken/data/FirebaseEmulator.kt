package se.partee71.dagboken.data

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.firestore.FirestoreCollection
import se.partee71.dagboken.data.firestore.FirestoreInstance
import se.partee71.dagboken.data.firestore.FirestoreSyncStatus
import se.partee71.dagboken.data.firestore.FirestoreUserDirectory
import se.partee71.dagboken.data.firestore.FirestoreUserVersions
import se.partee71.dagboken.data.firestore.Paths

/**
 * En egen FirebaseApp mot Firebase-emulatorerna på värddatorn (10.0.2.2 från Android-emulatorn),
 * projekt `demo-dagboken`. Rör aldrig den riktiga databasen.
 */
object FirebaseEmulator {
    private const val HOST = "10.0.2.2"

    private val app: FirebaseApp by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = FirebaseOptions.Builder()
            .setProjectId("demo-dagboken")
            .setApplicationId("1:1:android:1")
            .setApiKey("demo-nyckel")
            .build()
        FirebaseApp.initializeApp(context, options, "emulator")
    }

    /** Som appens: en ny instans mot emulatorn även efter att cachen tömts (AUTH-6). */
    val firestore = FirestoreInstance {
        FirebaseFirestore.getInstance(app).apply {
            useEmulator(HOST, 8080)
            check(firestoreSettings.host == "$HOST:8080") { "Testerna får bara köras mot emulatorn" }
        }
    }

    val db: FirebaseFirestore get() = firestore.db

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app).apply { useEmulator(HOST, 9099) } }

    /** En ny anonym testanvändare i auth-emulatorn; varje anrop ger ett nytt uid. */
    suspend fun newUid(): String {
        auth.signOut()
        return auth.signInAnonymously().await().user!!.uid
    }

    /** En ny inloggad användare med `users/{uid}` i [version]. */
    suspend fun newUser(version: Int = Schema.CURRENT_VERSION): String {
        val uid = newUid()
        db.document(Paths.user(uid)).set(mapOf("schemaVersion" to version.toLong())).await()
        return uid
    }

    fun <T : Identified> collection(
        scope: TestUserScope,
        codec: DocCodec<T>,
        name: String,
        path: (uid: String?) -> String,
        clock: FixedClock = FixedClock(),
        sync: FirestoreSyncStatus = sync(),
    ): EntityCollection<T> = FirestoreCollection(firestore, scope, sync, clock, codec, name, path)

    fun versions() = FirestoreUserVersions(firestore)

    fun directory() = FirestoreUserDirectory(firestore)

    fun sync() = FirestoreSyncStatus(CoroutineScope(Dispatchers.Default))
}
