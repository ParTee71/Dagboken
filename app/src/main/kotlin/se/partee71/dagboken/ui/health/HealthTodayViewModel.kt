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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.core.engine.daysEnding
import se.partee71.dagboken.core.engine.health.trendOrNull
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.data.health.HealthPermissions
import se.partee71.dagboken.data.health.HealthRepository
import se.partee71.dagboken.data.health.HealthStatus
import se.partee71.dagboken.data.health.observedStatus
import se.partee71.dagboken.ui.common.SelectedDay

/**
 * Klockan på Idag (HEM-15, HEM-17, HLS-7) i klockans läge [status]. [day] är den valda dagens mått (`null` medan
 * dagen läses, alltså "—"); [weekDays] är de sju dagarna till och med idag – samma som energitrenden (HEM-7) – och
 * [steps]/[restingHeartRate] veckans trendrader, `null` när raden utelämnas (färre än två dagar med värde, eller
 * klockan inte kopplad).
 */
data class HealthTodayUiState(
    val status: HealthStatus,
    val date: LocalDate,
    val day: DailyHealth? = null,
    val weekDays: List<LocalDate> = emptyList(),
    val steps: List<Float?>? = null,
    val restingHeartRate: List<Float?>? = null,
)

/**
 * Hälsokortet och klockans trendrader på Idag (HEM-15, HEM-17, HLS-7): steg och vilopuls för **dagen Idag visar**
 * (`SelectedDay`, byter med datumremsan) och veckans dagshistorik – båda lästa live ur [HealthRepository] och aldrig
 * sparade (HLS-5). Läsningen är nycklad på klockans läge, så att den görs om när Health Connect blir tillgängligt
 * eller behörigheten ges; ett läsfel ger "—" och inga trendrader i stället för att fälla Idag. Fristående från
 * `TodayViewModel`, så att klockan aldrig håller tillbaka resten av Idag (HLS-7).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HealthTodayViewModel @Inject constructor(
    private val health: HealthRepository,
    private val permissions: HealthPermissions,
    selectedDay: SelectedDay,
    clock: Clock,
    zone: Provider<TimeZone>,
) : ViewModel() {
    private val time = HealthTime(clock, zone, viewModelScope)

    /** Den visade dagen (HEM-14), samma regel som Idag. */
    private val date: Flow<LocalDate> = selectedDay.shown(time.today)

    /** Den visade dagen och dess mått – idag läses om varje minut, en tidigare dag när den väljs. */
    private val day: Flow<Pair<LocalDate, DailyHealth?>> =
        time.reads(date).readEach { health.day(it).getOrNull() ?: DailyHealth(it) }.combineDate(date)

    private class Week(val days: List<LocalDate>, val history: HealthHistory?)

    /** Veckan till och med idag – läses om med idag, så att dagens punkt följer hälsokortet. */
    private val week: Flow<Week> = time.reads(time.today).readEach { today ->
        val days = daysEnding(today)
        Week(days, health.history(days.first(), today).getOrNull())
    }.combine(time.today) { week, today -> week ?: Week(daysEnding(today), null) }

    val state: StateFlow<HealthTodayUiState?> = health.observedStatus().flatMapLatest { status ->
        when (status) {
            null -> flowOf(null)
            HealthStatus.AVAILABLE -> combine(day, week) { (date, day), week ->
                HealthTodayUiState(
                    status = status,
                    date = date,
                    day = day,
                    weekDays = week.days,
                    steps = week.history?.let { trendOrNull(it.series { day -> day.steps }) },
                    restingHeartRate = week.history?.let { trendOrNull(it.series { day -> day.restingHeartRate }) },
                )
            }
            else -> date.map { HealthTodayUiState(status, it) }
        }
    }.stateIn(viewModelScope, healthSharing, null)

    fun onEvent(event: HealthEvent) = permissions.handle(event)
}

/** Måtten med dagen de gäller: medan ett nytt dygn läses (`null`) gäller de den nya dagen. */
private fun Flow<DailyHealth?>.combineDate(date: Flow<LocalDate>): Flow<Pair<LocalDate, DailyHealth?>> =
    combine(date) { day, date -> date to day?.takeIf { it.date == date } }
