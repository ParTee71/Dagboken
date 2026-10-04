package se.partee71.dagboken.data.firestore

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
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
     * `clearPersistence` kräver en avslutad (eller oanvänd) instans. Låset hålls hela vägen, så att
     * ingen hinner öppna den nya instansen mot samma filer innan de är raderade.
     */
    override suspend fun clear(): Result<Unit> = suspendRunCatching(::firestoreError) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val old = current ?: create()
                current = null
                Tasks.await(old.terminate())
                Tasks.await(old.clearPersistence())
            }
        }
    }
}
