package se.partee71.dagboken.data.firestore

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.dagboken.data.common.LocalCacheCleaner
import se.partee71.dagboken.data.common.suspendRunCatching

/**
 * Appens Firestore-instans. En avslutad instans (`terminate`) går inte att använda igen, så
 * klasserna i `data/firestore` hämtar [db] här vid varje anrop i stället för att hålla en egen
 * referens – efter [clear] får de den nya instansen. [create] bygger och konfigurerar en instans
 * (cache, emulator i tester) och anropas igen efter varje [clear].
 */
class FirestoreInstance(private val create: () -> FirebaseFirestore) : LocalCacheCleaner {
    private val lock = Any()
    private var current: FirebaseFirestore? = null

    val db: FirebaseFirestore
        get() = synchronized(lock) { current ?: create().also { current = it } }

    override suspend fun awaitPendingWrites(timeout: Duration): Boolean =
        // await() ger null även när det lyckas (Task<Void>) – därför ett eget kvitto.
        withTimeoutOrNull(timeout) {
            db.waitForPendingWrites().await()
            true
        } ?: false

    /**
     * `clearPersistence` kräver en avslutad (eller oanvänd) instans, och filerna får inte vara
     * öppnade av en ny instans medan de raderas – annars skriver den nya instansen till filer som
     * strax försvinner, och osynkade skrivningar kan gå förlorade (regel 1). Låset hålls därför
     * hela vägen, och [db] väntar under tiden. Varje steg har en tidsgräns ([TASK_TIMEOUT_SECONDS]):
     * en Firestore som aldrig svarar ger ett fel (utloggningen rapporterar det) i stället för att
     * låsa [db] – och allt som hämtar instansen – för alltid.
     */
    override suspend fun clear(): Result<Unit> = suspendRunCatching(::firestoreError) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val old = current ?: create()
                current = null
                Tasks.await(old.terminate(), TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                Tasks.await(old.clearPersistence(), TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        }
    }

    private companion object {
        /** Båda stegen tar normalt millisekunder; tiotals sekunder betyder att något hänger. */
        const val TASK_TIMEOUT_SECONDS = 10L
    }
}
