package se.partee71.dagboken.ui.common

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.time.SECONDS_PER_HOUR
import se.partee71.dagboken.core.time.SECONDS_PER_MINUTE
import se.partee71.dagboken.core.time.truncatedToMinute

// Tiden som flöden – en gång för alla ViewModels som räknar med "nu" (regel 4). Tidszonen läses vid
// varje omräkning, så att ett byte av tidszon slår igenom vid nästa tick.

/** Timmen just nu, och igen vid varje ny hel timme (temat, SET-2). */
fun Clock.hours(zone: () -> TimeZone): Flow<Int> = ticks(zone, { it.hour }) { now, _ ->
    (SECONDS_PER_HOUR - now.minute * SECONDS_PER_MINUTE - now.second).seconds
}

/**
 * Klockslaget just nu i hela minuter, och igen vid varje ny minut (Idag: "Snart" och "Försenat",
 * hälsningen och dagbytet vid midnatt kommer ur samma flöde).
 */
fun Clock.minutes(zone: () -> TimeZone): Flow<LocalDateTime> = ticks(zone, LocalDateTime::truncatedToMinute) { now, _ ->
    (SECONDS_PER_MINUTE - now.second).seconds
}

/** Dagen idag, och igen vid varje midnatt (Mediciner: dagens dos, periodslut, avslutade). */
fun Clock.days(zone: () -> TimeZone): Flow<LocalDate> = ticks(zone, { it.date }) { now, z ->
    now.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(z) - now()
}

private fun <T> Clock.ticks(zone: () -> TimeZone, value: (LocalDateTime) -> T, untilNext: (LocalDateTime, TimeZone) -> Duration): Flow<T> = flow {
    while (true) {
        val z = zone()
        val now = now().toLocalDateTime(z)
        emit(value(now))
        // Aldrig kortare än en sekund – en klocka precis på gränsen får inte snurra.
        delay(untilNext(now, z).coerceAtLeast(1.seconds))
    }
}.distinctUntilChanged()

/**
 * Nu i [zone] i hela minuter – dagen och klockslaget en ny post får som förval (NAV-10, HEM-14), ur **en** läsning
 * av klockan. En gång för alla formulär (aktivitet, händelse, mående, dos, episod, incheckning).
 */
fun Clock.nowInMinutes(zone: TimeZone): LocalDateTime = now().toLocalDateTime(zone).truncatedToMinute()

/**
 * Dagen och klockslaget en ny post loggas mot: [date] (den dag skärmen visar, `null` = idag) kl. nu i hela minuter i
 * [zone] ([nowInMinutes]) – en gång för postformulären (aktivitet, händelse, dos, episod, incheckning; NAV-10, HEM-14).
 */
fun Clock.dayAndTime(zone: TimeZone, date: LocalDate?): LocalDateTime {
    val now = nowInMinutes(zone)
    return if (date == null) now else LocalDateTime(date, now.time)
}
