package se.partee71.dagboken.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SWEDISH = Locale("sv", "SE")

private val timeFormatter          = DateTimeFormatter.ofPattern("HH:mm", SWEDISH)
private val dayDateFormatter       = DateTimeFormatter.ofPattern("EEEE d MMMM", SWEDISH)
private val shortDateFormatter     = DateTimeFormatter.ofPattern("d MMM", SWEDISH)
private val shortDateYearFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", SWEDISH)

/** "HH:mm", t.ex. "08:15". */
fun formatTime(time: LocalTime): String = time.format(timeFormatter)

/** "EEEE d MMMM" med stor bokstav, t.ex. "Måndag 8 juli". */
fun formatDayDate(date: LocalDate): String =
    date.format(dayDateFormatter).replaceFirstChar { it.uppercase() }

/** "d MMM", t.ex. "8 jul". */
fun formatShortDate(date: LocalDate): String = date.format(shortDateFormatter)

/** "d MMM yyyy", t.ex. "8 jul 2026". */
fun formatShortDateYear(date: LocalDate): String = date.format(shortDateYearFormatter)

/** Kort veckodagsetikett, t.ex. "Mån"/"Tis". Används som x-axeletikett i sparkline-diagram. */
fun formatWeekdayShort(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.MONDAY    -> "Mån"
    DayOfWeek.TUESDAY   -> "Tis"
    DayOfWeek.WEDNESDAY -> "Ons"
    DayOfWeek.THURSDAY  -> "Tor"
    DayOfWeek.FRIDAY    -> "Fre"
    DayOfWeek.SATURDAY  -> "Lör"
    DayOfWeek.SUNDAY    -> "Sön"
}

fun formatDisplayDate(datum: String): String {
    val date = LocalDate.parse(datum)
    val today = LocalDate.now()
    return when (date) {
        today -> "Idag"
        today.minusDays(1) -> "Igår"
        else -> formatDayDate(date)
    }
}
