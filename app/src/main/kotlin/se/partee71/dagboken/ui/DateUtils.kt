package se.partee71.dagboken.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatDisplayDate(datum: String): String {
    val date = LocalDate.parse(datum)
    val today = LocalDate.now()
    return when (date) {
        today -> "Idag"
        today.minusDays(1) -> "Igår"
        else -> {
            val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("sv", "SE"))
            date.format(formatter).replaceFirstChar { it.uppercase() }
        }
    }
}
