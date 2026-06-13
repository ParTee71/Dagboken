package se.partee71.dagboken.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.datastore.PreferencesRepository
import javax.inject.Inject

data class SettingsUiState(
    val isDarkTheme: Boolean = true,
    val isDynamicColor: Boolean = true,
    val aktivitetOptions: List<String> = emptyList(),
    val symptomOptions: List<String> = emptyList(),
    val newAktivitetOption: String = "",
    val newSymptomOption: String = "",
    val googleAccountEmail: String? = null,
    val googleAccountPhotoUrl: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()

    init {
        viewModelScope.launch {
            prefs.isDarkTheme.collectLatest { dark ->
                _state.value = _state.value.copy(isDarkTheme = dark)
            }
        }
        viewModelScope.launch {
            prefs.dynamicColor.collectLatest { dynamic ->
                _state.value = _state.value.copy(isDynamicColor = dynamic)
            }
        }
        viewModelScope.launch {
            prefs.aktivitetOptions.collectLatest { opts ->
                _state.value = _state.value.copy(aktivitetOptions = opts)
            }
        }
        viewModelScope.launch {
            prefs.symptomOptions.collectLatest { opts ->
                _state.value = _state.value.copy(symptomOptions = opts)
            }
        }
        refreshGoogleAccount()
    }

    fun refreshGoogleAccount() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        _state.value = _state.value.copy(
            googleAccountEmail    = account?.email,
            googleAccountPhotoUrl = account?.photoUrl?.toString(),
        )
    }

    fun signOut(onComplete: () -> Unit) {
        GoogleSignIn.getClient(context, signInOptions).signOut().addOnCompleteListener {
            refreshGoogleAccount()
            onComplete()
        }
    }

    fun toggleTheme() {
        viewModelScope.launch {
            prefs.setDarkTheme(!_state.value.isDarkTheme)
        }
    }

    fun toggleDynamicColor() {
        viewModelScope.launch {
            prefs.setDynamicColor(!_state.value.isDynamicColor)
        }
    }

    fun setNewAktivitetOption(v: String) { _state.value = _state.value.copy(newAktivitetOption = v) }
    fun setNewSymptomOption(v: String) { _state.value = _state.value.copy(newSymptomOption = v) }

    fun addAktivitetOption() {
        val new = _state.value.newAktivitetOption.trim()
        if (new.isBlank() || _state.value.aktivitetOptions.contains(new)) return
        viewModelScope.launch {
            prefs.setAktivitetOptions(_state.value.aktivitetOptions + new)
            _state.value = _state.value.copy(newAktivitetOption = "")
        }
    }

    fun removeAktivitetOption(opt: String) {
        viewModelScope.launch {
            prefs.setAktivitetOptions(_state.value.aktivitetOptions - opt)
        }
    }

    fun addSymptomOption() {
        val new = _state.value.newSymptomOption.trim()
        if (new.isBlank() || _state.value.symptomOptions.contains(new)) return
        viewModelScope.launch {
            prefs.setSymptomOptions(_state.value.symptomOptions + new)
            _state.value = _state.value.copy(newSymptomOption = "")
        }
    }

    fun removeSymptomOption(opt: String) {
        viewModelScope.launch {
            prefs.setSymptomOptions(_state.value.symptomOptions - opt)
        }
    }
}
