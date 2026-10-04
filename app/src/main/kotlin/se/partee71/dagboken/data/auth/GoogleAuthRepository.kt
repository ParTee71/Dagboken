package se.partee71.dagboken.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import se.partee71.dagboken.R
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.suspendRunCatching

/** Credential Manager + Firebase Auth (skill firebase-auth). Loggar aldrig uid eller e-post. */
@Singleton
class GoogleAuthRepository @Inject constructor(
    @ApplicationContext context: Context,
) : AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val credentials = CredentialManager.create(context)
    private val serverClientId = context.getString(R.string.default_web_client_id)

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser> = suspendRunCatching(::authError) {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        val credential = credentials.getCredential(activityContext, request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw DataError.Unknown
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user?.toAuthUser()
            ?: throw DataError.Unknown
    }

    override suspend fun signOut(): Result<Unit> = suspendRunCatching(::authError) {
        // Firebase först: sessionen (UserSession) följer uid:t och släpper användarens data direkt.
        auth.signOut()
        credentials.clearCredentialState(ClearCredentialStateRequest())
    }
}

private fun FirebaseUser.toAuthUser() = AuthUser(uid, displayName?.takeIf { it.isNotBlank() }, email?.takeIf { it.isNotBlank() })
