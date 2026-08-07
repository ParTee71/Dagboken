package se.partee71.dagboken.ui.hantera

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.DEFAULT_MED_NOTIFICATIONS
import se.partee71.dagboken.data.datastore.DEFAULT_PERIOD_REMINDER_TIME
import se.partee71.dagboken.data.datastore.DEFAULT_SCREENING_EVENTS
import se.partee71.dagboken.data.datastore.MedNotificationConfig
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.BIRTH_YEAR_RANGE
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Sex
import se.partee71.dagboken.notifications.AlarmScheduler
import javax.inject.Inject

data class HanteraUiState(
    val isDarkTheme: Boolean = true,
    val isDynamicColor: Boolean = true,
    val themeMode: String = "auto",           // "light"|"dark"|"auto"
    val themeLightStart: Int = 7,
    val themeDarkStart: Int = 21,
    val medsNotificationsEnabled: Boolean = false,
    /** Tid och på/av per medicintidpunkt (NOT-18). */
    val medNotificationConfigs: List<MedNotificationConfig> = DEFAULT_MED_NOTIFICATIONS,
    val screeningEventConfigs: List<ScreeningEventConfig> = DEFAULT_SCREENING_EVENTS,
    val periodReminderTime: String = DEFAULT_PERIOD_REMINDER_TIME,
    val aktivitetOptions: List<SymptomOption> = emptyList(),
    val symptomOptions: List<SymptomOption> = emptyList(),
    val handelseTypOptions: List<SymptomOption> = emptyList(),
    val newAktivitetOption: String = "",
    val newSymptomOption: String = "",
    val newHandelseTypOption: String = "",
    /** Notiser är avstängda i systeminställningarna — påminnelser kan inte visas (NOT-16). */
    val notificationsBlocked: Boolean = false,
    /** Exakta larm är inte tillåtna — påminnelser kan komma försenade (NOT-16). */
    val exactAlarmsBlocked: Boolean = false,
    val googleAccountEmail: String? = null,
    val googleAccountPhotoUrl: String? = null,
    val signInFailed: Boolean = false,
    val isSigningIn: Boolean = false,
    /** Profil (HLS-11) — styr åldersnormerna för sömnkvaliteten. Null = inte angivet. */
    val birthYear: Int? = null,
    val sex: Sex = Sex.EJ_ANGIVET,
)

@HiltViewModel
class HanteraViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val authRepo: FirebaseAuthRepository,
    private val alarmScheduler: AlarmScheduler,
    private val medicinerRepo: MedicinerRepository,
    private val aktiviteterRepo: AktiviteterRepository,
    private val handelserRepo: HandelserRepository,
    private val sjukdomarRepo: SjukdomarRepository,
) : ViewModel() {

    val medicinFavoriter: StateFlow<List<Favorit>> = medicinerRepo.allFavoriter
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleMedicinFavorite(favorit: Favorit) {
        viewModelScope.launch { medicinerRepo.setFavoritFavorite(favorit.id, !favorit.isFavorite) }
    }

    private val _isSigningIn        = MutableStateFlow(false)
    // Bara "misslyckades" — inte SDK:ns råa (engelska, tekniska) meddelande. UI-lagret
    // visar en svensk strängresurs i stället.
    private val _signInError        = MutableStateFlow(false)
    private val _notificationsBlocked = MutableStateFlow(false)
    private val _exactAlarmsBlocked   = MutableStateFlow(false)
    private val _newAktivitetOption = MutableStateFlow("")
    private val _newSymptomOption   = MutableStateFlow("")
    private val _newHandelseTypOption = MutableStateFlow("")

    private data class ThemePrefs(
        val dark: Boolean, val dynamic: Boolean, val mode: String,
        val lightStart: Int, val darkStart: Int,
    )
    private data class NotifPrefs(
        val reminders: ReminderPrefs,
        val aktivitetOpts: List<SymptomOption>,
        val symptomOpts: List<SymptomOption>,
        val handelseTypOpts: List<SymptomOption>,
    )
    private data class ReminderPrefs(
        val medsEnabled: Boolean,
        val medConfigs: List<MedNotificationConfig>,
        val screeningConfigs: List<ScreeningEventConfig>,
        val periodReminderTime: String,
    )
    private data class NewOptionInputs(
        val aktivitet: String,
        val symptom: String,
        val handelseTyp: String,
    )

    val state: StateFlow<HanteraUiState> = combine(
        combine(prefs.isDarkTheme, prefs.dynamicColor, prefs.themeMode,
                prefs.themeLightStart, prefs.themeDarkStart) { dark, dynamic, mode, light, darkS ->
            ThemePrefs(dark, dynamic, mode, light, darkS)
        },
        combine(
            combine(prefs.medsNotificationsEnabled, prefs.medNotificationConfigs,
                    prefs.screeningEventConfigs, prefs.periodReminderTime) { meds, medConfigs, screening, periodTime ->
                ReminderPrefs(meds, medConfigs, screening, periodTime)
            },
            prefs.aktivitetOptions, prefs.symptomOptions, prefs.handelseTypOptions,
        ) { reminders, akt, symp, handelseTyp ->
            NotifPrefs(reminders, akt, symp, handelseTyp)
        },
        combine(authRepo.authStateFlow, _isSigningIn, _signInError,
                combine(_newAktivitetOption, _newSymptomOption, _newHandelseTypOption) { newAkt, newSymp, newHandelseTyp ->
                    NewOptionInputs(newAkt, newSymp, newHandelseTyp)
                },
                combine(_notificationsBlocked, _exactAlarmsBlocked) { notif, exact -> notif to exact },
        ) { user, signing, err, newOptions, (notifBlocked, exactBlocked) ->
            HanteraUiState(
                notificationsBlocked  = notifBlocked,
                exactAlarmsBlocked    = exactBlocked,
                googleAccountEmail    = user?.email,
                googleAccountPhotoUrl = user?.photoUrl?.toString(),
                isSigningIn           = signing,
                signInFailed          = err,
                newAktivitetOption    = newOptions.aktivitet,
                newSymptomOption      = newOptions.symptom,
                newHandelseTypOption  = newOptions.handelseTyp,
            )
        },
        combine(prefs.birthYear, prefs.sex) { year, sex -> year to sex },
    ) { theme, notif, auth, (birthYear, sex) ->
        auth.copy(
            birthYear                = birthYear,
            sex                      = sex,
            isDarkTheme              = theme.dark,
            isDynamicColor           = theme.dynamic,
            themeMode                = theme.mode,
            themeLightStart          = theme.lightStart,
            themeDarkStart           = theme.darkStart,
            medsNotificationsEnabled = notif.reminders.medsEnabled,
            medNotificationConfigs   = notif.reminders.medConfigs,
            screeningEventConfigs    = notif.reminders.screeningConfigs,
            periodReminderTime       = notif.reminders.periodReminderTime,
            aktivitetOptions         = notif.aktivitetOpts,
            symptomOptions           = notif.symptomOpts,
            handelseTypOptions       = notif.handelseTypOpts,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HanteraUiState())

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _isSigningIn.value = true
            _signInError.value = false
            val result = authRepo.signInWithGoogle(activityContext)
            _isSigningIn.value = false
            result.onFailure { e ->
                if (e.message?.contains("cancel", ignoreCase = true) != true) {
                    _signInError.value = true
                }
            }
        }
    }

    fun clearSignInError() { _signInError.value = false }

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

    /**
     * Uppdaterar bilden av vilka systembehörigheter påminnelserna faktiskt har (NOT-16).
     * Utan detta kunde reglagen stå på "på" medan notiser var blockerade, helt tyst.
     * Anropas när Hantera visas, eftersom behörigheterna kan ändras utanför appen.
     */
    fun refreshPermissionState() {
        _notificationsBlocked.value = !alarmScheduler.canPostNotifications()
        _exactAlarmsBlocked.value = !alarmScheduler.canScheduleExactAlarms()
    }

    /** På/av för medicinpåminnelsen vid en tidpunkt (NOT-18). */
    fun toggleMedNotificationTime(index: Int) {
        val updated = state.value.medNotificationConfigs.toMutableList()
            .also { it[index] = it[index].copy(enabled = !it[index].enabled) }
        viewModelScope.launch {
            prefs.setMedNotificationConfigs(updated)
            alarmScheduler.rescheduleAll()
        }
    }

    /** Klockslag för medicinpåminnelsen vid en tidpunkt (NOT-18). */
    fun setMedNotificationTime(index: Int, time: String) {
        val updated = state.value.medNotificationConfigs.toMutableList()
            .also { it[index] = it[index].copy(time = time) }
        viewModelScope.launch {
            prefs.setMedNotificationConfigs(updated)
            if (updated[index].enabled) alarmScheduler.rescheduleAll()
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

    /** Klockslag för periodpåminnelsen (NOT-13) — larmet flyttas direkt. */
    /** Sparar födelseåret; ogiltiga eller tomma värden rensar det (HLS-11). */
    fun setBirthYear(year: Int?) {
        viewModelScope.launch {
            prefs.setBirthYear(year?.takeIf { it in BIRTH_YEAR_RANGE })
        }
    }

    fun setSex(sex: Sex) {
        viewModelScope.launch { prefs.setSex(sex) }
    }

    fun setPeriodReminderTime(time: String) {
        viewModelScope.launch {
            prefs.setPeriodReminderTime(time)
            alarmScheduler.schedulePeriodReminder(time)
        }
    }

    fun setNewAktivitetOption(v: String) { _newAktivitetOption.value = v }
    fun setNewSymptomOption(v: String)   { _newSymptomOption.value = v }

    fun addAktivitetOption() {
        val new = _newAktivitetOption.value.trim()
        if (new.isBlank() || state.value.aktivitetOptions.any { it.name == new }) return
        viewModelScope.launch {
            prefs.setAktivitetOptions(prefs.aktivitetOptions.first() + SymptomOption(new))
            _newAktivitetOption.value = ""
        }
    }

    fun deleteAktivitetOption(name: String) {
        viewModelScope.launch {
            prefs.setAktivitetOptions(prefs.aktivitetOptions.first().filter { it.name != name })
        }
    }

    fun toggleAktivitetFavorite(name: String) {
        viewModelScope.launch {
            prefs.setAktivitetOptions(prefs.aktivitetOptions.first().map {
                if (it.name == name) it.copy(isFavorite = !it.isFavorite) else it
            })
        }
    }

    /**
     * Namnbytet gäller även redan loggade poster (HAN-9). Tidigare ändrades bara listan,
     * så historiken låg kvar på det gamla namnet och visades som ett alternativ utanför
     * den aktuella listan.
     */
    fun renameAktivitetOption(old: String, new: String) {
        val trimmed = new.trim()
        if (trimmed.isBlank() || trimmed == old || state.value.aktivitetOptions.any { it.name == trimmed }) return
        viewModelScope.launch {
            prefs.setAktivitetOptions(prefs.aktivitetOptions.first().map {
                if (it.name == old) it.copy(name = trimmed) else it
            })
            aktiviteterRepo.renameAktivitet(old, trimmed)
        }
    }

    fun addSymptomOption() {
        val new = _newSymptomOption.value.trim()
        if (new.isBlank() || state.value.symptomOptions.any { it.name == new }) return
        viewModelScope.launch {
            prefs.setSymptomOptions(prefs.symptomOptions.first() + SymptomOption(new))
            _newSymptomOption.value = ""
        }
    }

    fun deleteSymptomOption(name: String) {
        viewModelScope.launch {
            prefs.setSymptomOptions(prefs.symptomOptions.first().filter { it.name != name })
        }
    }

    fun toggleSymptomFavorite(name: String) {
        viewModelScope.launch {
            prefs.setSymptomOptions(prefs.symptomOptions.first().map {
                if (it.name == name) it.copy(isFavorite = !it.isFavorite) else it
            })
        }
    }

    /** Se [renameAktivitetOption] — symptomnamn ligger kodade i både aktiviteter och
     *  sjukdomsincheckningar och byts därför på båda ställena. */
    fun renameSymptomOption(old: String, new: String) {
        val trimmed = new.trim()
        if (trimmed.isBlank() || trimmed == old || state.value.symptomOptions.any { it.name == trimmed }) return
        viewModelScope.launch {
            prefs.setSymptomOptions(prefs.symptomOptions.first().map {
                if (it.name == old) it.copy(name = trimmed) else it
            })
            aktiviteterRepo.renameSymptom(old, trimmed)
            sjukdomarRepo.renameSymptom(old, trimmed)
        }
    }

    fun setNewHandelseTypOption(v: String) { _newHandelseTypOption.value = v }

    fun addHandelseTypOption() {
        val new = _newHandelseTypOption.value.trim()
        if (new.isBlank() || state.value.handelseTypOptions.any { it.name == new }) return
        viewModelScope.launch {
            prefs.setHandelseTypOptions(prefs.handelseTypOptions.first() + SymptomOption(new))
            _newHandelseTypOption.value = ""
        }
    }

    fun deleteHandelseTypOption(name: String) {
        viewModelScope.launch {
            prefs.setHandelseTypOptions(prefs.handelseTypOptions.first().filter { it.name != name })
        }
    }

    fun toggleHandelseTypFavorite(name: String) {
        viewModelScope.launch {
            prefs.setHandelseTypOptions(prefs.handelseTypOptions.first().map {
                if (it.name == name) it.copy(isFavorite = !it.isFavorite) else it
            })
        }
    }

    /** Se [renameAktivitetOption]. */
    fun renameHandelseTypOption(old: String, new: String) {
        val trimmed = new.trim()
        if (trimmed.isBlank() || trimmed == old || state.value.handelseTypOptions.any { it.name == trimmed }) return
        viewModelScope.launch {
            prefs.setHandelseTypOptions(prefs.handelseTypOptions.first().map {
                if (it.name == old) it.copy(name = trimmed) else it
            })
            handelserRepo.renameTyp(old, trimmed)
        }
    }

}
