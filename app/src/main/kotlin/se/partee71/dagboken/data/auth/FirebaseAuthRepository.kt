package se.partee71.dagboken.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import se.partee71.dagboken.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context)

    val currentUser: FirebaseUser? get() = auth.currentUser

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> {
        return try {
            val webClientId = activityContext.getString(R.string.default_web_client_id)

            val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val idToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val user = auth.signInWithCredential(firebaseCredential).await().user
                ?: error("Firebase returnerade null user efter lyckad inloggning")
            Result.success(user)
        } catch (e: NoCredentialException) {
            Result.failure(e)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun clearCredentialState() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }
}
