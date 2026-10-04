package se.partee71.dagboken.data.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import java.io.IOException
import se.partee71.dagboken.data.common.DataError

/** Inloggningens fel → [DataError], en gång (skill firebase-auth → "Error Categories"). */
fun authError(error: Throwable): DataError = when (error) {
    is DataError -> error
    is GetCredentialCancellationException -> DataError.Cancelled
    is FirebaseNetworkException, is IOException -> DataError.Offline
    is FirebaseAuthException -> DataError.SignInRejected
    else -> DataError.Unknown
}
