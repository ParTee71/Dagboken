package se.partee71.dagboken.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.DEFAULT_SCREENING_EVENTS
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.notifications.AlarmScheduler
import javax.inject.Inject

data class SettingsUiState(
    val isDarkTheme: Boolean = true,
    val isDynamicColor: Boolean = true,
    val themeMode: String = "auto",           // "light"|"dark"|"auto"
    val themeLightStart: Int = 7,
    val themeDarkStart: Int = 21,
    val medsNotificationsEnabled: Boolean = false,
    val screeningEventConfigs: List<ScreeningEventConfig> = DEFAULT_SCREENING_EVENTS,
    val aktivitetOptions: List<String> = emptyList(),
    val symptomOptions: List<String> = emptyList(),
    val newAktivitetOption: String = "",
    val newSymptomOption: String = "",
    val googleAccountEmail: String? = null,
    val googleAccountPhotoUrl: String? = null,
    val signInError: String? = null,
    val isSigningIn: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val authRepo: FirebaseAuthRepository,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

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
            prefs.themeMode.collectLatest { mode ->
                _state.value = _state.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            prefs.themeLightStart.collectLatest { hour ->
                _state.value = _state.value.copy(themeLightStart = hour)
            }
        }
        viewModelScope.launch {
            prefs.themeDarkStart.collectLatest { hour ->
                _state.value = _state.value.copy(themeDarkStart = hour)
            }
        }
        viewModelScope.launch {
            prefs.medsNotificationsEnabled.collectLatest { enabled ->
                _state.value = _state.value.copy(medsNotificationsEnabled = enabled)
            }
        }
        viewModelScope.launch {
            prefs.screeningEventConfigs.collectLatest { configs ->
                _state.value = _state.value.copy(screeningEventConfigs = configs)
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
        viewModelScope.launch {
            authRepo.authStateFlow.collectLatest { user ->
                _state.value = _state.value.copy(
                    googleAccountEmail    = user?.email,
                    googleAccountPhotoUrl = user?.photoUrl?.toString(),
                )
            }
        }
    }

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSigningIn = true, signInError = null)
            val result = authRepo.signInWithGoogle(activityContext)
            _state.value = _state.value.copy(isSigningIn = false)
            result.onFailure { e ->
                if (e.message?.contains("cancel", ignoreCase = true) != true) {
                    _state.value = _state.value.copy(signInError = e.message ?: "Inloggning misslyckades")
                }
            }
        }
    }

    fun clearSignInError() { _state.value = _state.value.copy(signInError = null) }

    fun signOut() {
        viewModelScope.launch {
            authRepo.clearCredentialState()
            authRepo.signOut()
        }
    }

    fun toggleTheme() {
        viewModelScope.launch { prefs.setDarkTheme(!_state.value.isDarkTheme) }
    }

    fun toggleDynamicColor() {
        viewModelScope.launch { prefs.setDynamicColor(!_state.value.isDynamicColor) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setThemeLightStart(hour: Int) {
        val clamped = hour.coerceIn(0, (_state.value.themeDarkStart - 1).coerceAtLeast(0))
        viewModelScope.launch { prefs.setThemeLightStart(clamped) }
    }

    fun setThemeDarkStart(hour: Int) {
        val clamped = hour.coerceIn((_state.value.themeLightStart + 1).coerceAtMost(23), 23)
        viewModelScope.launch { prefs.setThemeDarkStart(clamped) }
    }

    fun toggleMedsNotifications() {
        viewModelScope.launch {
            prefs.setMedsNotificationsEnabled(!_state.value.medsNotificationsEnabled)
            alarmScheduler.rescheduleAll()
        }
    }

    fun toggleScreeningEvent(index: Int) {
        val updated = _state.value.screeningEventConfigs.toMutableList()
            .also { it[index] = it[index].copy(enabled = !it[index].enabled) }
        viewModelScope.launch {
            prefs.setScreeningEventConfigs(updated)
            alarmScheduler.rescheduleAll()
        }
    }

    fun setScreeningEventTime(index: Int, time: String) {
        val updated = _state.value.screeningEventConfigs.toMutableList()
            .also { it[index] = it[index].copy(time = time) }
        viewModelScope.launch {
            prefs.setScreeningEventConfigs(updated)
            if (updated[index].enabled) alarmScheduler.rescheduleAll()
        }
    }

    fun setNewAktivitetOption(v: String) { _state.value = _state.value.copy(newAktivitetOption = v) }
    fun setNewSymptomOption(v: String)   { _state.value = _state.value.copy(newSymptomOption = v) }

    fun addAktivitetOption() {
        val new = _state.value.newAktivitetOption.trim()
        if (new.isBlank() || _state.value.aktivitetOptions.contains(new)) return
        viewModelScope.launch {
            prefs.setAktivitetOptions(_state.value.aktivitetOptions + new)
            _state.value = _state.value.copy(newAktivitetOption = "")
        }
    }

    fun removeAktivitetOption(opt: String) {
        viewModelScope.launch { prefs.setAktivitetOptions(_state.value.aktivitetOptions - opt) }
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
        viewModelScope.launch { prefs.setSymptomOptions(_state.value.symptomOptions - opt) }
    }
}
