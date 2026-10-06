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

// Tiden som flöden – en gång för alla ViewModels som räknar med "nu" (regel 4). Tidszonen läses vid
// varje omräkning, så att ett byte av tidszon slår igenom vid nästa tick.

/** Timmen just nu, och igen vid varje ny hel timme (temat, SET-2). */
fun Clock.hours(zone: () -> TimeZone): Flow<Int> = ticks(zone, { it.hour }) { now, _ ->
    (SECONDS_PER_HOUR - now.minute * SECONDS_PER_MINUTE - now.second).seconds
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

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
