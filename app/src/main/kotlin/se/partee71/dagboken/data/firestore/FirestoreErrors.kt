package se.partee71.dagboken.data.firestore

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import se.partee71.dagboken.data.common.DataError

/** Firestores fel → [DataError], en gång (skill firestore-data-layer). */
fun firestoreError(error: Throwable): DataError = when (error) {
    is DataError -> error
    else -> when (error.firestoreCode()) {
        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> DataError.Offline
        Code.PERMISSION_DENIED, Code.UNAUTHENTICATED -> DataError.PermissionDenied
        Code.CANCELLED -> DataError.Cancelled
        Code.NOT_FOUND -> DataError.NotFound
        Code.RESOURCE_EXHAUSTED -> DataError.QuotaExceeded
        else -> DataError.Unknown
    }
}

/**
 * Firestores felkod, även när felet är inlindat: `snapshots()` avslutar flödet med ett
 * `CancellationException` vars orsak är det egentliga [FirebaseFirestoreException].
 */
fun Throwable.firestoreCode(): Code? =
    generateSequence(this) { it.cause }.take(MAX_CAUSES).filterIsInstance<FirebaseFirestoreException>().firstOrNull()?.code

private const val MAX_CAUSES = 8
