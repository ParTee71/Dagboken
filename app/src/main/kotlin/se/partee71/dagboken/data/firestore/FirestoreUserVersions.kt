package se.partee71.dagboken.data.firestore

import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.snapshots
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.UserVersionSource
import se.partee71.dagboken.data.common.retryWithBackoff
import se.partee71.dagboken.data.user.EnsureUserUseCase

/**
 * Läser `schemaVersion` direkt ur `users/{uid}` (utan codec – används av sessionen). Ett dokument
 * som inte finns ger `null`; ett utan fältet, eller med ett värde under det första formatet, får
 * [Schema.FIRST_VERSION] (Schema.versionOf).
 * Nekar servern läsningen avslutas flödet med [DataError.PermissionDenied]. Bara
 * `PERMISSION_DENIED` räknas: ett tillfälligt `UNAUTHENTICATED` (token som förnyas) och alla andra
 * fel försöker igen, så att versionen aldrig blir okänd för gott.
 */
class FirestoreUserVersions @Inject constructor(private val firestore: FirestoreInstance) : UserVersionSource {
    override fun schemaVersion(uid: String): Flow<Int?> =
        firestore.db.document(Paths.user(uid)).snapshots()
            .map { doc -> if (doc.exists()) Schema.versionOf(doc.get(EnsureUserUseCase.SCHEMA_VERSION)) else null }
            .catch { error ->
                throw if (error.firestoreCode() == Code.PERMISSION_DENIED) DataError.PermissionDenied else error
            }
            .retryWithBackoff(first = 2.seconds, max = 60.seconds, retryOn = { it != DataError.PermissionDenied })

    /** `update`, inte `set`: bara versionen ändras, och ett dokument som inte finns skapas inte. */
    override fun stamp(uid: String, version: Int) {
        firestore.db.document(Paths.user(uid)).update(EnsureUserUseCase.SCHEMA_VERSION, version)
    }
}
