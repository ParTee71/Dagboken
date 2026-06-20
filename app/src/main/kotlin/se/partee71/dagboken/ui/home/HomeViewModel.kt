package se.partee71.dagboken.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.tidpunktSortIndex
import se.partee71.dagboken.domain.model.tidpunktToHour
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import javax.inject.Inject

data class HomeUiState(
    val todayMediciner: List<Medicin> = emptyList(),
    val screeningPoints: List<Float> = emptyList(),
    val screeningLabels: List<String> = emptyList(),
    val overdueMediciner: List<Medicin> = emptyList(),
    val overdueScreeningTimes: List<String> = emptyList(),
    val lastAktivitet: Aktivitet? = null,
    val tagenCount: Int = 0,
    val googleEmail: String? = null,
    val googlePhotoUrl: String? = null,
    val googleDisplayName: String? = null,
    val isSigningIn: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val aktiviteterRepo: AktiviteterRepository,
    private val medicinerRepo: MedicinerRepository,
    private val authRepo: FirebaseAuthRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _isSigningIn = MutableStateFlow(false)

    private val activeScreeningTimes = prefs.screeningEventConfigs
        .map { configs: List<ScreeningEventConfig> -> configs.filter { it.enabled }.map { it.time } }

    init {
        viewModelScope.launch { medicinerRepo.ensureTodayEntries() }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        medicinerRepo.todayFlow(),
        authRepo.authStateFlow,
        _isSigningIn,
        activeScreeningTimes,
    ) { today, user, signingIn, activeTimes ->
        val screeningsToday = aktiviteterRepo.getScreeningToday()
        val screeningDailyAvg = aktiviteterRepo.screeningFromDate(7).first()
            .groupBy { it.datum }
            .entries
            .sortedBy { it.key }
            .map { (datum, entries) -> datum to entries.map { it.energy.toFloat() }.average().toFloat() }
        val nowTime        = LocalTime.now()

        val overdueMediciner = today
            .filter { med ->
                !med.tagen && !med.skipped &&
                tidpunktToHour(med.tidpunkt)?.let { h -> nowTime.hour >= h } == true
            }
            .sortedBy { tidpunktSortIndex(it.tidpunkt) }

        val overdueScreeningTimes = activeTimes.filter { timeStr ->
            val h = timeStr.substringBefore(":").toIntOrNull() ?: return@filter false
            val m = timeStr.substringAfter(":").toIntOrNull() ?: 0
            val reminderTime = LocalTime.of(h, m)
            nowTime.isAfter(reminderTime) && screeningsToday.none { s ->
                try { !LocalTime.parse(s.tid).isBefore(reminderTime) } catch (_: Exception) { false }
            }
        }

        HomeUiState(
            todayMediciner        = today.sortedBy { tidpunktSortIndex(it.tidpunkt) },
            screeningPoints       = screeningDailyAvg.map { it.second },
            screeningLabels       = screeningDailyAvg.map { dayLabel(it.first) },
            overdueMediciner      = overdueMediciner,
            overdueScreeningTimes = overdueScreeningTimes,
            lastAktivitet         = null,
            tagenCount            = today.count { it.tagen },
            googleEmail           = user?.email,
            googlePhotoUrl        = user?.photoUrl?.toString(),
            googleDisplayName     = user?.displayName,
            isSigningIn           = signingIn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun toggleMedicinTagen(medicin: Medicin) {
        viewModelScope.launch { medicinerRepo.toggleTagen(medicin.id, !medicin.tagen) }
    }

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _isSigningIn.value = true
            try {
                authRepo.signInWithGoogle(activityContext)
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepo.clearCredentialState()
            authRepo.signOut()
        }
    }

    private fun dayLabel(datum: String): String {
        return try {
            when (LocalDate.parse(datum).dayOfWeek) {
                DayOfWeek.MONDAY    -> "Mån"
                DayOfWeek.TUESDAY   -> "Tis"
                DayOfWeek.WEDNESDAY -> "Ons"
                DayOfWeek.THURSDAY  -> "Tor"
                DayOfWeek.FRIDAY    -> "Fre"
                DayOfWeek.SATURDAY  -> "Lör"
                DayOfWeek.SUNDAY    -> "Sön"
                else                -> ""
            }
        } catch (_: Exception) { "" }
    }

}

fun greeting(): String {
    return when (java.time.LocalTime.now().hour) {
        in 0..4   -> "God natt"
        in 5..11  -> "God morgon"
        in 12..16 -> "God eftermiddag"
        in 17..20 -> "God kväll"
        else      -> "God natt"
    }
}

fun formattedDate(): String {
    val date = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale("sv", "SE"))
    val weekNum = date.get(WeekFields.ISO.weekOfWeekBasedYear())
    return "${date.format(formatter).replaceFirstChar { it.uppercase() }} · Vecka $weekNum"
}
