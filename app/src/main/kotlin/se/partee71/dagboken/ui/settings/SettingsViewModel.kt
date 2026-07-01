package se.partee71.dagboken.ui.settings

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
import se.partee71.dagboken.data.datastore.DEFAULT_SCREENING_EVENTS
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.model.Favorit
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
    val aktivitetOptions: List<SymptomOption> = emptyList(),
    val symptomOptions: List<SymptomOption> = emptyList(),
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
    private val medicinerRepo: MedicinerRepository,
) : ViewModel() {

    val medicinFavoriter: StateFlow<List<Favorit>> = medicinerRepo.allFavoriter
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleMedicinFavorite(favorit: Favorit) {
        viewModelScope.launch { medicinerRepo.setFavoritFavorite(favorit.id, !favorit.isFavorite) }
    }

    private val _isSigningIn        = MutableStateFlow(false)
    private val _signInError        = MutableStateFlow<String?>(null)
    private val _newAktivitetOption = MutableStateFlow("")
    private val _newSymptomOption   = MutableStateFlow("")

    private data class ThemePrefs(
        val dark: Boolean, val dynamic: Boolean, val mode: String,
        val lightStart: Int, val darkStart: Int,
    )
    private data class NotifPrefs(
        val medsEnabled: Boolean,
        val screeningConfigs: List<ScreeningEventConfig>,
        val aktivitetOpts: List<SymptomOption>,
        val symptomOpts: List<SymptomOption>,
    )

    val state: StateFlow<SettingsUiState> = combine(
        combine(prefs.isDarkTheme, prefs.dynamicColor, prefs.themeMode,
                prefs.themeLightStart, prefs.themeDarkStart) { dark, dynamic, mode, light, darkS ->
            ThemePrefs(dark, dynamic, mode, light, darkS)
        },
        combine(prefs.medsNotificationsEnabled, prefs.screeningEventConfigs,
                prefs.aktivitetOptions, prefs.symptomOptions) { meds, screening, akt, symp ->
            NotifPrefs(meds, screening, akt, symp)
        },
        combine(authRepo.authStateFlow, _isSigningIn, _signInError,
                _newAktivitetOption, _newSymptomOption) { user, signing, err, newAkt, newSymp ->
            SettingsUiState(
                googleAccountEmail    = user?.email,
                googleAccountPhotoUrl = user?.photoUrl?.toString(),
                isSigningIn           = signing,
                signInError           = err,
                newAktivitetOption    = newAkt,
                newSymptomOption      = newSymp,
            )
        },
    ) { theme, notif, auth ->
        auth.copy(
            isDarkTheme              = theme.dark,
            isDynamicColor           = theme.dynamic,
            themeMode                = theme.mode,
            themeLightStart          = theme.lightStart,
            themeDarkStart           = theme.darkStart,
            medsNotificationsEnabled = notif.medsEnabled,
            screeningEventConfigs    = notif.screeningConfigs,
            aktivitetOptions         = notif.aktivitetOpts,
            symptomOptions           = notif.symptomOpts,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _isSigningIn.value = true
            _signInError.value = null
            val result = authRepo.signInWithGoogle(activityContext)
            _isSigningIn.value = false
            result.onFailure { e ->
                if (e.message?.contains("cancel", ignoreCase = true) != true) {
                    _signInError.value = e.message ?: "Inloggning misslyckades"
                }
            }
        }
    }

    fun clearSignInError() { _signInError.value = null }

    fun signOut() {
        viewModelScope.launch {
            authRepo.clearCredentialState()
            authRepo.signOut()
        }
    }

    fun toggleTheme() {
        viewModelScope.launch { prefs.setDarkTheme(!state.value.isDarkTheme) }
    }

    fun toggleDynamicColor() {
        viewModelScope.launch { prefs.setDynamicColor(!state.value.isDynamicColor) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setThemeLightStart(hour: Int) {
        val clamped = hour.coerceIn(0, (state.value.themeDarkStart - 1).coerceAtLeast(0))
        viewModelScope.launch { prefs.setThemeLightStart(clamped) }
    }

    fun setThemeDarkStart(hour: Int) {
        val clamped = hour.coerceIn((state.value.themeLightStart + 1).coerceAtMost(23), 23)
        viewModelScope.launch { prefs.setThemeDarkStart(clamped) }
    }

    fun toggleMedsNotifications() {
        viewModelScope.launch {
            prefs.setMedsNotificationsEnabled(!state.value.medsNotificationsEnabled)
            alarmScheduler.rescheduleAll()
        }
    }

    fun toggleScreeningEvent(index: Int) {
        val updated = state.value.screeningEventConfigs.toMutableList()
            .also { it[index] = it[index].copy(enabled = !it[index].enabled) }
        viewModelScope.launch {
            prefs.setScreeningEventConfigs(updated)
            alarmScheduler.rescheduleAll()
        }
    }

    fun setScreeningEventTime(index: Int, time: String) {
        val updated = state.value.screeningEventConfigs.toMutableList()
            .also { it[index] = it[index].copy(time = time) }
        viewModelScope.launch {
            prefs.setScreeningEventConfigs(updated)
            if (updated[index].enabled) alarmScheduler.rescheduleAll()
        }
    }

    fun setNewAktivitetOption(v: String) { _newAktivitetOption.value = v }
    fun setNewSymptomOption(v: String)   { _newSymptomOption.value = v }

    fun addAktivitetOption() {
        val new = _newAktivitetOption.value.trim()
        if (new.isBlank() || state.value.aktivitetOptions.any { it.name == new }) return
        viewModelScope.launch {
            prefs.setAktivitetOptions(state.value.aktivitetOptions + SymptomOption(new))
            _newAktivitetOption.value = ""
        }
    }

    fun deleteAktivitetOption(name: String) {
        viewModelScope.launch {
            prefs.setAktivitetOptions(state.value.aktivitetOptions.filter { it.name != name })
        }
    }

    fun toggleAktivitetFavorite(name: String) {
        viewModelScope.launch {
            prefs.setAktivitetOptions(state.value.aktivitetOptions.map {
                if (it.name == name) it.copy(isFavorite = !it.isFavorite) else it
            })
        }
    }

    fun renameAktivitetOption(old: String, new: String) {
        val trimmed = new.trim()
        if (trimmed.isBlank() || trimmed == old || state.value.aktivitetOptions.any { it.name == trimmed }) return
        viewModelScope.launch {
            prefs.setAktivitetOptions(state.value.aktivitetOptions.map {
                if (it.name == old) it.copy(name = trimmed) else it
            })
        }
    }

    fun addSymptomOption() {
        val new = _newSymptomOption.value.trim()
        if (new.isBlank() || state.value.symptomOptions.any { it.name == new }) return
        viewModelScope.launch {
            prefs.setSymptomOptions(state.value.symptomOptions + SymptomOption(new))
            _newSymptomOption.value = ""
        }
    }

    fun deleteSymptomOption(name: String) {
        viewModelScope.launch {
            prefs.setSymptomOptions(state.value.symptomOptions.filter { it.name != name })
        }
    }

    fun toggleSymptomFavorite(name: String) {
        viewModelScope.launch {
            prefs.setSymptomOptions(state.value.symptomOptions.map {
                if (it.name == name) it.copy(isFavorite = !it.isFavorite) else it
            })
        }
    }

    fun renameSymptomOption(old: String, new: String) {
        val trimmed = new.trim()
        if (trimmed.isBlank() || trimmed == old || state.value.symptomOptions.any { it.name == trimmed }) return
        viewModelScope.launch {
            prefs.setSymptomOptions(state.value.symptomOptions.map {
                if (it.name == old) it.copy(name = trimmed) else it
            })
        }
    }

}
