package se.partee71.dagboken.core.time

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Varje datum från [from] till och med [to], äldst först – perioders x-axlar (Trender) och
 * hälsohistorikens dygn (HLS-12). Tom när [to] ligger före [from]. Räknas i datum, inte klockslag, så en
 * sommartidsomställning varken tappar eller dubblerar en dag.
 */
fun datesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
    val count = from.daysUntil(to) + 1
    if (count <= 0) return emptyList()
    return List(count) { from.plus(it, DateTimeUnit.DAY) }
}

/**
 * Dagen som visas (HEM-14): den valda [chosen], eller [today] när ingen är vald (`null`) – aldrig en dag efter idag,
 * så att ett val står kvar som idag över midnatt. En gång för Idag och klockan på Idag.
 */
fun shownDate(chosen: LocalDate?, today: LocalDate): LocalDate = chosen?.takeIf { it <= today } ?: today

/** Måndagen i veckan som innehåller datumet – veckan börjar på måndag i datumremsan, kalendern och Idag. */
fun LocalDate.weekMonday(): LocalDate = minus(DatePeriod(days = dayOfWeek.ordinal))

/** Veckans sju dagar, måndag först, för veckan som börjar [monday]. */
fun weekDays(monday: LocalDate): List<LocalDate> = List(DAYS_PER_WEEK) { monday.plus(it, DateTimeUnit.DAY) }

/** Samma ögonblick avrundat nedåt till hela minuten – sekunder och bråkdelar bort. */
fun LocalDateTime.truncatedToMinute(): LocalDateTime = LocalDateTime(date, LocalTime(hour, minute))
