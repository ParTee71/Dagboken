package se.partee71.dagboken.data.firestore

import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.data.user.UserDirectory

/**
 * `users/{uid}` skapas i en transaktion: den läser från servern och skriver bara om dokumentet
 * saknas, så att ett befintligt dokument aldrig skrivs över – och den kräver nät, så att ett tomt
 * svar betyder att dokumentet verkligen saknas.
 */
class FirestoreUserDirectory @Inject constructor(private val firestore: FirestoreInstance) : UserDirectory {

    override suspend fun createIfMissing(uid: String, initial: Doc): Result<Doc> = suspendRunCatching(::firestoreError) {
        val db = firestore.db
        val ref = db.document(Paths.user(uid))
        val data = toFirestore(initial)
        // Utan svar i tid räknas det som offline; nästa försök gör samma sak, så det blir aldrig två.
        withTimeoutOrNull(CREATE_TIMEOUT) {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(ref)
                if (snapshot.exists()) {
                    fromFirestore(snapshot.data.orEmpty())
                } else {
                    transaction.set(ref, data)
                    initial
                }
            }.await()
        } ?: throw DataError.Offline
    }

    private companion object {
        val CREATE_TIMEOUT = 15.seconds
    }
}
