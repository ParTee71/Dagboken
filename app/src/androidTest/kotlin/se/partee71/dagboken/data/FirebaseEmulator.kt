package se.partee71.dagboken.data

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.rules.ExternalResource
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
 * Firebase-emulatorerna på värddatorn (10.0.2.2 från Android-emulatorn), projekt `demo-dagboken`,
 * som JUnit-regel. Rör aldrig den riktiga databasen.
 *
 * **Varje testanvändare har en egen [FirebaseApp]** – egen `FirebaseAuth`, egen Firestore-instans
 * och egen lokal cache (persistensnyckeln följer appnamnet) – och är inloggad innan dess
 * Firestore-instans skapas. Ett användarbyte sker därför aldrig under en levande Firestore-instans.
 * Firestore får ett byte asynkront via auth-lyssnaren; en skrivning direkt efter `signOut` +
 * `signInAnonymously` på samma app kunde hamna i förra användarens kö och aldrig kvitteras, och
 * testet hängde tills CI-jobbets gräns. Behöver ett test två användare skapas två. [after]
 * avslutar testets Firestore-instanser och loggar ut deras användare (apparna lever kvar, se [after]).
 *
 * ```
 * @get:Rule val emulator = FirebaseEmulator()
 * … val user = emulator.newUser()
 * ```
 *
 * Varje steg mot emulatorerna har en tidsgräns ([STEP_TIMEOUT]) med ett felmeddelande som säger
 * vilket steg som inte svarade.
 */
class FirebaseEmulator : ExternalResource() {
    private val apps = CopyOnWriteArrayList<FirebaseApp>()

    @Volatile
    private var active = false

    override fun before() {
        active = true
    }

    override fun after() {
        active = false
        // Apparna raderas inte: Firebase Auths interna register pekar på den app som skapade den
        // första FirebaseAuth-instansen, och `FirebaseApp.delete()` ger därefter
        // "FirebaseApp was deleted" i `useEmulator` för alla senare appar i samma process (CI-run
        // 37217695837). Firestore avslutas och användaren loggas ut; apparna är unika per körning
        // och processen avslutas efter sviten, så de kostar bara lite minne.
        val closed = apps.map { app ->
            runCatching {
                Tasks.await(FirebaseFirestore.getInstance(app).terminate(), 10, TimeUnit.SECONDS)
                FirebaseAuth.getInstance(app).signOut()
            }
        }
        apps.clear()
        closed.firstOrNull { it.isFailure }?.getOrThrow()
    }

    /** En ny anonym användare, inloggad i auth-emulatorn, utan `users/{uid}`. */
    suspend fun newSignedInUser(): EmulatorUser {
        check(active) { "FirebaseEmulator används som @get:Rule – annars stängs apparna aldrig" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, OPTIONS, "emulator-$RUN-${counter.incrementAndGet()}")
        apps += app
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
        val uid = step("Anonym inloggning i auth-emulatorn (:$AUTH_PORT)") {
            checkNotNull(auth.signInAnonymously().await().user) { "Auth-emulatorn gav ingen användare" }.uid
        }
        return EmulatorUser(uid, firestoreOf(app))
    }

    /**
     * En ny inloggad användare med `users/{uid}` i första formatet, skrivet med SDK:t som appen
     * (rules tillåter bara version 1 vid create).
     */
    suspend fun newUser(): EmulatorUser {
        val user = newSignedInUser()
        return step("Skrivningen av users/{uid} till firestore-emulatorn (:$FIRESTORE_PORT)") {
            user.db.document(Paths.user(user.uid)).set(mapOf("schemaVersion" to Schema.FIRST_VERSION.toLong())).await()
            user
        }
    }

    companion object {
        private const val HOST = "10.0.2.2"
        private const val PROJECT = "demo-dagboken"
        private const val FIRESTORE_PORT = 8080
        private const val AUTH_PORT = 9099
        private const val HTTP_TIMEOUT_MS = 5_000

        /** Ett steg mot emulatorn tar normalt under en sekund. */
        val STEP_TIMEOUT = 20.seconds

        /** Appnamnen är unika per körning, så att ingen inloggning eller cache från en tidigare körning återanvänds. */
        private val RUN = UUID.randomUUID().toString().take(8)
        private val counter = AtomicInteger()

        private val OPTIONS: FirebaseOptions = FirebaseOptions.Builder()
            .setProjectId(PROJECT)
            .setApplicationId("1:1:android:1")
            .setApiKey("demo-nyckel")
            .build()

        /** Som appens: en ny instans mot emulatorn även efter att cachen tömts (AUTH-6). */
        private fun firestoreOf(app: FirebaseApp) = FirestoreInstance {
            FirebaseFirestore.getInstance(app).apply {
                useEmulator(HOST, FIRESTORE_PORT)
                check(firestoreSettings.host == "$HOST:$FIRESTORE_PORT") { "Testerna får bara köras mot emulatorn" }
            }
        }

        private suspend fun <T : Any> step(what: String, block: suspend () -> T): T =
            withTimeoutOrNull(STEP_TIMEOUT) { block() }
                ?: throw AssertionError("$what svarade inte inom $STEP_TIMEOUT – körs Firebase-emulatorerna (firestore, auth) med projektet $PROJECT?")

        /**
         * Skriver `users/{uid}` med [version] förbi rules – som en nyare app eller verktygen gjort – via
         * emulatorns REST-API med `Authorization: Bearer owner` (bara emulatorn godtar det). Behövs för
         * en version över `maxSchemaVersion()` i rules, som ingen klient får skriva. Dokumentet skapas,
         * så [uid] ska vara ny ([newSignedInUser]); läsningen i testet går sedan via SDK:t som vanligt.
         */
        suspend fun seedUserBypassingRules(uid: String, version: Int): Unit = withContext(Dispatchers.IO) {
            val url = URL("http://$HOST:$FIRESTORE_PORT/v1/projects/$PROJECT/databases/(default)/documents/users?documentId=$uid")
            val body = """{"fields":{"schemaVersion":{"integerValue":"$version"},"createdAt":{"timestampValue":"${FixedClock().now()}"}}}"""
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = HTTP_TIMEOUT_MS
                connection.readTimeout = HTTP_TIMEOUT_MS
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

        fun sync() = FirestoreSyncStatus(CoroutineScope(Dispatchers.Default))
    }
}

/** En inloggad testanvändare med sin egen Firestore-instans ([FirebaseEmulator]). */
class EmulatorUser(val uid: String, val firestore: FirestoreInstance) {
    val db: FirebaseFirestore get() = firestore.db

    fun <T : Identified> collection(
        scope: TestUserScope,
        codec: DocCodec<T>,
        name: String,
        path: (uid: String?) -> String,
        clock: FixedClock = FixedClock(),
        sync: FirestoreSyncStatus = FirebaseEmulator.sync(),
    ): EntityCollection<T> = FirestoreCollection(firestore, scope, sync, clock, codec, name, path)

    fun versions() = FirestoreUserVersions(firestore)

    fun directory() = FirestoreUserDirectory(firestore)
}
