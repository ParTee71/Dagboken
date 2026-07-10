package se.partee71.dagboken.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SCREENING_EVENT_LABELS
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.datastore.ScreeningTime
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.tidpunktSortIndex
import se.partee71.dagboken.domain.model.tidpunktToHour
import se.partee71.dagboken.ui.formatDayDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.WeekFields
import javax.inject.Inject

data class ScreeningEventStatus(
    val label: String,
    val time: String,
    val logged: Boolean,
    val overdue: Boolean,
)

enum class EnergyTrend { UP, DOWN, FLAT }

/** Veckosammanfattning som visas på Idag i början av veckan (sön/mån). */
data class WeekSummary(
    val energyTrend: EnergyTrend,
    val dosesTakenPercent: Int,
)

/**
 * Beräknar veckosammanfattningen från befintliga poster — ingen ny persisterad
 * data. Energitrenden jämför senaste 7 dagarnas genomsnittliga screeningenergi
 * mot de 7 dagarna dessförinnan; doskvoten är andelen tagna av veckans
 * schemalagda (ej skippade) doser. Returnerar null om det saknas underlag.
 * Ren funktion (tar [today] som parameter) för enkel enhetstestning.
 */
internal fun computeWeekSummary(
    today: LocalDate,
    screenings: List<Aktivitet>,
    meds: List<Medicin>,
): WeekSummary? {
    val weekStart = today.minusDays(6)
    val prevStart = today.minusDays(13)
    val prevEnd   = today.minusDays(7)

    fun parse(datum: String): LocalDate? = runCatching { LocalDate.parse(datum) }.getOrNull()
    fun avgEnergy(from: LocalDate, to: LocalDate): Double? {
        val values = screenings.mapNotNull { s ->
            parse(s.datum)?.takeIf { !it.isBefore(from) && !it.isAfter(to) }?.let { s.energy }
        }
        return if (values.isEmpty()) null else values.average()
    }

    val thisAvg = avgEnergy(weekStart, today)
    val prevAvg = avgEnergy(prevStart, prevEnd)
    val trend = when {
        thisAvg == null || prevAvg == null -> EnergyTrend.FLAT
        thisAvg > prevAvg + 0.5            -> EnergyTrend.UP
        thisAvg < prevAvg - 0.5            -> EnergyTrend.DOWN
        else                               -> EnergyTrend.FLAT
    }

    val weekMeds = meds.filter { med ->
        val d = parse(med.datum)
        d != null && !d.isBefore(weekStart) && !d.isAfter(today) &&
            !med.skipped && tidpunktToHour(med.tidpunkt) != null
    }
    val dosesPercent = if (weekMeds.isEmpty()) 0
                       else (weekMeds.count { it.tagen } * 100) / weekMeds.size

    val hasData = thisAvg != null || weekMeds.isNotEmpty()
    return if (hasData) WeekSummary(trend, dosesPercent) else null
}

data class HomeUiState(
    val todayMediciner: List<Medicin> = emptyList(),
    val screeningPoints: List<Float> = emptyList(),
    val screeningLabels: List<String> = emptyList(),
    val overdueMediciner: List<Medicin> = emptyList(),
    val screeningEvents: List<ScreeningEventStatus> = emptyList(),
    val lastAktivitet: Aktivitet? = null,
    val tagenCount: Int = 0,
    val googleEmail: String? = null,
    val googlePhotoUrl: String? = null,
    val googleDisplayName: String? = null,
    val isSigningIn: Boolean = false,
    val pagaendeSjukdom: SjukdomsEpisod? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val aktiviteterRepo: AktiviteterRepository,
    private val medicinerRepo: MedicinerRepository,
    private val authRepo: FirebaseAuthRepository,
    private val prefs: PreferencesRepository,
    private val sjukdomarRepo: SjukdomarRepository,
) : ViewModel() {

    private val _isSigningIn = MutableStateFlow(false)

    private val activeScreeningEvents = prefs.screeningEventConfigs
        .map { configs: List<ScreeningEventConfig> ->
            configs.mapIndexedNotNull { i, c ->
                if (c.enabled) SCREENING_EVENT_LABELS.getOrNull(i)?.let { label -> label to c.time } else null
            }
        }

    init {
        viewModelScope.launch { medicinerRepo.ensureTodayEntries() }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        medicinerRepo.todayFlow(),
        authRepo.authStateFlow,
        _isSigningIn,
        activeScreeningEvents,
        combine(
            aktiviteterRepo.screeningFromDate(7),
            sjukdomarRepo.pagaende,
        ) { screenings, pagaende -> screenings to pagaende },
    ) { today, user, signingIn, activeEvents, (recentScreenings, pagaendeSjukdom) ->
        val todayStr = LocalDate.now().toString()
        val screeningsToday = recentScreenings.filter { it.datum == todayStr }
        val screeningDailyAvg = recentScreenings
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

        val screeningEvents = activeEvents.map { (label, timeStr) ->
            val st = ScreeningTime.parse(timeStr)
            val reminderTime = st?.let { LocalTime.of(it.hour, it.min) }
            val logged = screeningsToday.any { it.aktivitet == label }
            val overdue = !logged && reminderTime != null && nowTime.isAfter(reminderTime)
            ScreeningEventStatus(label = label, time = timeStr, logged = logged, overdue = overdue)
        }

        HomeUiState(
            todayMediciner        = today.sortedBy { tidpunktSortIndex(it.tidpunkt) },
            screeningPoints       = screeningDailyAvg.map { it.second },
            screeningLabels       = screeningDailyAvg.map { dayLabel(it.first) },
            overdueMediciner      = overdueMediciner,
            screeningEvents       = screeningEvents,
            lastAktivitet         = null,
            tagenCount            = today.count { it.tagen },
            googleEmail           = user?.email,
            googlePhotoUrl        = user?.photoUrl?.toString(),
            googleDisplayName     = user?.displayName,
            isSigningIn           = signingIn,
            pagaendeSjukdom       = pagaendeSjukdom,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    // Visas bara i början av veckan (sön/mån), och bara när det finns underlag.
    val weekSummary: StateFlow<WeekSummary?> = combine(
        aktiviteterRepo.screeningFromDate(14),
        medicinerRepo.allMediciner,
    ) { screenings, meds ->
        val today = LocalDate.now()
        if (today.dayOfWeek != DayOfWeek.SUNDAY && today.dayOfWeek != DayOfWeek.MONDAY) {
            null
        } else {
            computeWeekSummary(today, screenings, meds)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
    val weekNum = date.get(WeekFields.ISO.weekOfWeekBasedYear())
    return "${formatDayDate(date)} · Vecka $weekNum"
}
