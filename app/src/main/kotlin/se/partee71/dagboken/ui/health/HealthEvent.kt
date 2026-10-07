package se.partee71.dagboken.ui.health

import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.data.health.HealthPermissions
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.dagboken.ui.common.minutes

/** Det skärmarna med klockdata kan be om (HLS-3, HLS-4, HLS-14) – porten [HealthPermissions] gör resten. */
sealed interface HealthEvent {
    /** "Ge åtkomst": samtyckesdialogen för hela behörighetsuppsättningen (HLS-3, HLS-14). */
    data object GrantAccess : HealthEvent

    /** "Installera"/"Uppdatera": Health Connect i Play Butik (HLS-4). */
    data object OpenHealthConnect : HealthEvent
}

/** En händelse till porten – samma för Idag och Klocka. */
fun HealthPermissions.handle(event: HealthEvent) = when (event) {
    HealthEvent.GrantAccess -> requestAccess()
    HealthEvent.OpenHealthConnect -> openHealthConnect()
}

/**
 * En läsning av klockan: dygnet [date], och för idag minuten [minute] – idag läses om varje minut medan skärmen
 * följer den (stegen räknas hela dagen), en tidigare dag bara när den väljs. [extra] är något mer som ska ge en ny
 * läsning, t.ex. behörigheterna (HLS-14).
 */
internal data class HealthRead(val date: LocalDate, val minute: LocalDateTime? = null, val extra: Any? = null)

/**
 * Tiden för klockans skärmar (HEM-15, HLS-6), en gång för Idag och Klocka: minutklockan som `TodayViewModel`
 * (`Clock.minutes`), dagens datum ur den, och läsnycklar som ger idag en ny läsning högst en gång per minut.
 */
internal class HealthTime(clock: Clock, zone: Provider<TimeZone>, scope: CoroutineScope) {
    private val now: Flow<LocalDateTime> = clock.minutes { zone.get() }.shareIn(scope, healthSharing, replay = 1)

    /** Dagens datum, igen vid midnatt. */
    val today: Flow<LocalDate> = now.map { it.date }.distinctUntilChanged()

    /** Läsnycklarna för den visade dagen [date] (och [extra]): idag en per minut, en tidigare dag en. */
    fun reads(date: Flow<LocalDate>, extra: Flow<Any?> = flowOfNull): Flow<HealthRead> =
        combine(date, now, extra) { d, now, x -> HealthRead(d, now.takeIf { d == now.date }, x) }.distinctUntilChanged()

    private companion object {
        val flowOfNull: Flow<Any?> = flowOf(null)
    }
}

/**
 * En live-läsning ur klockan per nyckel (HLS-5: aldrig sparad). Byts dygnet ([HealthRead.date]) kommer först `null` –
 * medan det läses, så att ett nytt datum aldrig visar förra dagens värden – sedan [read]s svar; en omläsning av
 * samma dygn (ny minut, ändrade behörigheter) byter värdet utan `null` emellan. En ny nyckel avbryter en pågående läsning.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T : Any> Flow<HealthRead>.readEach(read: suspend (LocalDate) -> T): Flow<T?> = flow {
    var shown: LocalDate? = null
    flatMapLatest { key ->
        flow {
            if (key.date != shown) {
                shown = key.date
                emit(null)
            }
            emit(read(key.date))
        }
    }.collect { emit(it) }
}

/** Skärmarnas klockläsningar följer skärmen och glömmer sitt senaste värde när de stoppats (en ny dag kan ha börjat). */
internal val healthSharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0)
