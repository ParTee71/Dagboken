package se.partee71.dagboken.data

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
    private const val PROJECT = "demo-dagboken"
    private const val FIRESTORE_PORT = 8080

    private val app: FirebaseApp by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = FirebaseOptions.Builder()
            .setProjectId(PROJECT)
            .setApplicationId("1:1:android:1")
            .setApiKey("demo-nyckel")
            .build()
        FirebaseApp.initializeApp(context, options, "emulator")
    }

    /** Som appens: en ny instans mot emulatorn även efter att cachen tömts (AUTH-6). */
    val firestore = FirestoreInstance {
        FirebaseFirestore.getInstance(app).apply {
            useEmulator(HOST, FIRESTORE_PORT)
            check(firestoreSettings.host == "$HOST:$FIRESTORE_PORT") { "Testerna får bara köras mot emulatorn" }
        }
    }

    val db: FirebaseFirestore get() = firestore.db

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app).apply { useEmulator(HOST, 9099) } }

    /** En ny anonym testanvändare i auth-emulatorn; varje anrop ger ett nytt uid. */
    suspend fun newUid(): String {
        auth.signOut()
        return auth.signInAnonymously().await().user!!.uid
    }

    /**
     * En ny inloggad användare med `users/{uid}` i första formatet, skrivet med SDK:t som appen
     * (rules tillåter bara version 1 vid create).
     */
    suspend fun newUser(): String {
        val uid = newUid()
        db.document(Paths.user(uid)).set(mapOf("schemaVersion" to Schema.FIRST_VERSION.toLong())).await()
        return uid
    }

    /**
     * Skriver `users/{uid}` med [version] förbi rules – som en nyare app eller verktygen gjort – via
     * emulatorns REST-API med `Authorization: Bearer owner` (bara emulatorn godtar det). Behövs för
     * en version över `maxSchemaVersion()` i rules, som ingen klient får skriva. Dokumentet skapas,
     * så [uid] ska vara ny ([newUid]); läsningen i testet går sedan via SDK:t som vanligt.
     */
    suspend fun seedUserBypassingRules(uid: String, version: Int): Unit = withContext(Dispatchers.IO) {
        val url = URL("http://$HOST:$FIRESTORE_PORT/v1/projects/$PROJECT/databases/(default)/documents/users?documentId=$uid")
        val body = """{"fields":{"schemaVersion":{"integerValue":"$version"},"createdAt":{"timestampValue":"${FixedClock().now()}"}}}"""
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            check(status in 200..299) {
                "Emulatorn nekade dokumentet ($status): ${connection.errorStream?.bufferedReader()?.use { it.readText() }}"
            }
        } finally {
            connection.disconnect()
        }
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
