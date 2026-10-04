package se.partee71.dagboken.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.auth.AuthRepository
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.data.auth.SignOutUseCase
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.UserVersion
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.data.user.EnsureUserUseCase

/**
 * Vad appen visar (AUTH-1, AUTH-5): inloggning, "Uppdatera appen" eller innehållet.
 * [NeedsUser] = inloggad men `users/{uid}` finns inte (än) på servern eller går inte att läsa.
 */
enum class AuthGate { Loading, SignedOut, NeedsUser, UpdateRequired, Ready }

data class AuthUiState(
    val gate: AuthGate = AuthGate.Loading,
    /** Inloggning eller förberedelse av användaren pågår. */
    val busy: Boolean = false,
    val error: DataError? = null,
)

sealed interface AuthEvent {
    /** Knappen "Logga in med Google" – loggar in, eller försöker igen med användardokumentet om användaren redan är inloggad. */
    data class SignIn(val activityContext: Context) : AuthEvent

    /** "Logga ut" i inställningsarket (AUTH-2): loggar ut och tömmer den lokala cachen (AUTH-6). */
    data object SignOut : AuthEvent

    data object ErrorShown : AuthEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val session: UserScope,
    private val ensureUser: EnsureUserUseCase,
    private val signOutUser: SignOutUseCase,
) : ViewModel() {

    private val user: StateFlow<AuthUser?> = auth.authState.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val signingIn = MutableStateFlow(false)
    private val ensuring = MutableStateFlow(false)
    private val error = MutableStateFlow<DataError?>(null)
    private var ensureJob: Job? = null
    private var ensuringUid: String? = null

    private val gate = combine(auth.authState, session.schemaVersion, session.unreadable, ::gateOf)

    val state: StateFlow<AuthUiState> =
        combine(gate, signingIn, ensuring, error) { gate, signingIn, ensuring, error ->
            AuthUiState(gate, busy = signingIn || ensuring, error = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AuthUiState())

    init {
        // Utloggad avbryter en pågående förberedelse, så att nästa användare aldrig får den förras fel.
        viewModelScope.launch {
            auth.authState.filter { it == null }.collect { ensureJob?.cancel() }
        }
        // En inloggad användare vars dokument inte finns får det skapat – en gång per användare;
        // efter ett fel försöker knappen igen.
        viewModelScope.launch {
            combine(auth.authState, session.schemaVersion) { user, version ->
                user?.takeIf { version?.uid == it.uid && !version.exists }
            }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { ensure(it) }
        }
    }

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.SignIn -> user.value?.let(::ensure) ?: signIn(event.activityContext)
            AuthEvent.SignOut -> signOut()
            AuthEvent.ErrorShown -> error.value = null
        }
    }

    /**
     * Kontoväljaren kräver aktivitetens kontext, som hålls bara medan väljaren är öppen
     * (skill firebase-auth); roteras skärmen under tiden frigörs den när väljaren stängts.
     */
    private fun signIn(activityContext: Context) {
        if (signingIn.value) return
        viewModelScope.launch {
            signingIn.value = true
            error.value = null
            val result = auth.signInWithGoogle(activityContext)
            signingIn.value = false
            // Avbruten inloggning är inget fel (AUTH-2).
            result.dataError()?.takeIf { it != DataError.Cancelled }?.let { error.value = it }
        }
    }

    private fun signOut() {
        viewModelScope.launch { signOutUser().dataError()?.let { error.value = it } }
    }

    /**
     * Ser till att `users/{uid}` finns för [user]. En pågående förberedelse för en annan användare
     * avbryts, så att den nya användaren aldrig får den förras fel.
     */
    private fun ensure(user: AuthUser) {
        if (ensureJob?.isActive == true && ensuringUid == user.uid) return
        ensureJob?.cancel()
        ensuringUid = user.uid
        // Startas först när jobbet är sparat, så att jämförelsen i finally alltid gäller rätt jobb.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            ensuring.value = true
            error.value = null
            try {
                ensureUser(user.uid).dataError()
                    ?.takeIf { this@AuthViewModel.user.value?.uid == user.uid }
                    ?.let { error.value = it }
            } finally {
                // Ett avbrutet jobb lämnar flaggan åt det som ersatte det.
                if (ensureJob == coroutineContext[Job]) ensuring.value = false
            }
        }
        ensureJob = job
        job.start()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        fun gateOf(user: AuthUser?, version: UserVersion?, unreadable: String?): AuthGate = when {
            user == null -> AuthGate.SignedOut
            // Rules nekar läsningen: inloggningsskärmen visar felet och knappen försöker igen.
            unreadable == user.uid -> AuthGate.NeedsUser
            // Versionen avgör om appen får visas – vänta på den i stället för att blinka förbi.
            version?.uid != user.uid -> AuthGate.Loading
            !version.exists -> AuthGate.NeedsUser
            Schema.isNewerThanApp(version.version) -> AuthGate.UpdateRequired
            else -> AuthGate.Ready
        }
    }
}
