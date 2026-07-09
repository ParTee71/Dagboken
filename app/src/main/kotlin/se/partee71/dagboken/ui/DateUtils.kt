package se.partee71.dagboken.ui

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

fun formatDisplayDate(datum: String): String {
    val date = LocalDate.parse(datum)
    val today = LocalDate.now()
    return when (date) {
        today -> "Idag"
        today.minusDays(1) -> "Igår"
        else -> formatDayDate(date)
    }
}
