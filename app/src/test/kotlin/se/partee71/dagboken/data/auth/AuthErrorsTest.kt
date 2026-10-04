package se.partee71.dagboken.data.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import java.io.IOException
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.data.common.DataError

/** Robolectric: Firebase-undantagens konstruktorer anropar Android-API:er. */
@RunWith(RobolectricTestRunner::class)
class AuthErrorsTest {

    @Test
    fun `avbruten kontoväljare är Cancelled`() {
        assertEquals(DataError.Cancelled, authError(GetCredentialCancellationException()))
    }

    @Test
    fun `nätverksfel är Offline`() {
        assertEquals(DataError.Offline, authError(FirebaseNetworkException("nät")))
        assertEquals(DataError.Offline, authError(IOException()))
    }

    @Test
    fun `Firebase nekar token är SignInRejected`() {
        assertEquals(DataError.SignInRejected, authError(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "ogiltig")))
    }

    @Test
    fun `inget Google-konto och okända fel är Unknown, redan mappade fel behålls`() {
        assertEquals(DataError.Unknown, authError(NoCredentialException()))
        assertEquals(DataError.Unknown, authError(IllegalStateException()))
        assertEquals(DataError.Offline, authError(DataError.Offline))
    }
}
