package se.partee71.dagboken.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import se.partee71.dagboken.core.engine.REGULARITY_WINDOW_NIGHTS
import se.partee71.dagboken.core.engine.ageFromBirthYear
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric
import se.partee71.dagboken.core.engine.sleepScoreOn
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.core.model.Profile
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.health.HealthPermissions
import se.partee71.dagboken.data.health.HealthRepository
import se.partee71.dagboken.data.health.HealthStatus
import se.partee71.dagboken.data.health.observedStatus
import se.partee71.dagboken.data.repository.SettingsRepository

/**
 * Klocka-gruppens topp (TRD-20, HLS-4, HLS-6, HLS-8, HLS-10, HLS-11, HLS-14): klockans läge [status] – den enda källan
 * för Klocka-sektionen –, de valfria mått som saknar åtkomst ([missing], `null` = läget kunde inte läsas, ingen rad),
 * dagens alla mått ([day], `null` medan de läses) och nattens sömnpoäng ([sleepScore]). [needsBirthYear]: profilen
 * lästes och saknar födelseår – då uppmaningen i stället för en poäng; kunde profilen inte läsas visas bara "—".
 */
data class ClockUiState(
    val status: HealthStatus,
    val missing: Set<OptionalHealthMetric>? = null,
    val day: DailyHealth? = null,
    val sleepScore: Int? = null,
    val needsBirthYear: Boolean = false,
)

/**
 * Trender → Klocka: läget, och när klockan är kopplad – och bara då – Hälsa idag, läst live ur [HealthRepository] och
 * aldrig sparad (HLS-5). Idag läses om varje minut medan Klocka visas, och igen när behörigheterna ändras (HLS-14).
 * Sömnpoängen är Trenders (`sleepScoreOn`: samma serie över de senaste [REGULARITY_WINDOW_NIGHTS] nätterna, sista
 * natten) mot profilens födelseår och kön (HLS-11). "Ge åtkomst", "Installera" och "Uppdatera" går till porten
 * [HealthPermissions].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClockViewModel @Inject constructor(
    private val health: HealthRepository,
    private val permissions: HealthPermissions,
    settings: SettingsRepository,
    clock: Clock,
    zone: Provider<TimeZone>,
) : ViewModel() {
    private val time = HealthTime(clock, zone, viewModelScope)

    /** Profilen för sömnpoängens åldersnormer (HLS-11); `null` när den inte kunde läsas – då ingen poäng och ingen uppmaning. */
    private val profile: Flow<Profile?> = settings.settings.map<_, Profile?> { it.profile }.withFallback(null).distinctUntilChanged()

    /** Kan behörighetsläget inte läsas visas ingen rad (HLS-14). */
    private val missing: Flow<Set<OptionalHealthMetric>?> = permissions.missingOptional.withFallback(null).distinctUntilChanged()

    private class Today(val day: DailyHealth, val nights: HealthHistory)

    /** Dagens mått och nätterna bakåt för sömnpoängen – en läsning per minut och per ändrat behörighetsläge. */
    private val today: Flow<Today?> = time.reads(time.today, missing).readEach { today ->
        Today(
            health.day(today).getOrNull() ?: DailyHealth(today),
            health.history(today.minus(REGULARITY_WINDOW_NIGHTS - 1, DateTimeUnit.DAY), today).getOrNull() ?: HealthHistory(),
        )
    }

    private val connected: Flow<ClockUiState> = combine(today, profile, missing, time.today) { read, profile, missing, date ->
        val age = profile?.let { ageFromBirthYear(it.birthYear, date) }
        ClockUiState(
            status = HealthStatus.AVAILABLE,
            missing = missing,
            day = read?.day,
            sleepScore = read?.let { profile?.let { p -> sleepScoreOn(it.nights, date, age, p.sex) } },
            needsBirthYear = profile != null && age == null,
        )
    }

    val state: StateFlow<ClockUiState?> = health.observedStatus().flatMapLatest { status ->
        when (status) {
            null -> flowOf(null)
            HealthStatus.AVAILABLE -> connected
            else -> flowOf(ClockUiState(status))
        }
    }.stateIn(viewModelScope, healthSharing, null)

    fun onEvent(event: HealthEvent) = permissions.handle(event)
}
