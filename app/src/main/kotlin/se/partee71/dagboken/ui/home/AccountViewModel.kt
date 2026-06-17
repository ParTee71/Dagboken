package se.partee71.dagboken.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import javax.inject.Inject

data class AccountUiState(
    val googleEmail: String? = null,
    val googlePhotoUrl: String? = null,
    val googleDisplayName: String? = null,
    val isSigningIn: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepo: FirebaseAuthRepository,
) : ViewModel() {

    private val _isSigningIn = MutableStateFlow(false)

    val uiState: StateFlow<AccountUiState> = combine(
        authRepo.authStateFlow,
        _isSigningIn,
    ) { user, signingIn ->
        AccountUiState(
            googleEmail       = user?.email,
            googlePhotoUrl    = user?.photoUrl?.toString(),
            googleDisplayName = user?.displayName,
            isSigningIn       = signingIn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountUiState())

    fun signIn(context: Context) {
        viewModelScope.launch {
            _isSigningIn.value = true
            try { authRepo.signInWithGoogle(context) }
            finally { _isSigningIn.value = false }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepo.clearCredentialState()
            authRepo.signOut()
        }
    }
}
