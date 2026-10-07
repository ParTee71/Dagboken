package se.partee71.dagboken.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import se.partee71.dagboken.R

/** Det enda sättet att visa och konvertera datum och tider i UI. */
object DateFormat {
    private val swedish = Locale.forLanguageTag("sv-SE")
    private val display = DateTimeFormatter.ofPattern("EEE d MMM yyyy", swedish)
    private val short = DateTimeFormatter.ofPattern("d MMM", swedish)
    private val weekdayShortDay = DateTimeFormatter.ofPattern("EEE d MMM", swedish)
    private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", swedish)
    private val weekdayName = DateTimeFormatter.ofPattern("EEE", swedish)
    private val weekdayFull = DateTimeFormatter.ofPattern("EEEE", swedish)
    private val dayMonth = DateTimeFormatter.ofPattern("EEEE d MMMM", swedish)
    private val dayMonthYear = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", swedish)
    private const val DAY_MILLIS = 86_400_000L

    /** "lör 19 dec 2026" – utan punkterna som svensk CLDR sätter efter förkortningar. */
    fun display(date: LocalDate): String = display.format(date.toJavaLocalDate()).replace(".", "")

    /** "2 okt" – i förvalda namn ("Förkylning 2 okt"). */
    fun short(date: LocalDate): String = short.format(date.toJavaLocalDate()).replace(".", "")

    /**
     * "tis 6 okt" – en dag i episoden utan år (sjukdomsdetaljens incheckningar och senaste svårighet, SJ-5); [capitalized]
     * ger "Tis 6 okt" som rubrik på ett postkort.
     */
    fun weekdayDay(date: LocalDate, capitalized: Boolean = false): String =
        weekdayShortDay.format(date.toJavaLocalDate()).replace(".", "").let { if (capitalized) it.replaceFirstChar { c -> c.titlecase(swedish) } else it }

    /** "oktober 2026" – kalenderns rubrik. */
    fun month(date: LocalDate): String = monthYear.format(date.toJavaLocalDate())

    /** "mån" – kortnamnet på dagens veckodag, utan punkt. */
    fun weekdayShort(date: LocalDate): String = weekdayShort(date.dayOfWeek)

    /** "mån" – veckodagens kortnamn, utan punkt (t.ex. ett recepts veckodagar). */
    fun weekdayShort(day: DayOfWeek): String = weekdayName.format(java.time.DayOfWeek.of(day.isoDayNumber)).replace(".", "")

    /** "måndag" – veckodagens hela namn (receptformulärets valda dagar). */
    fun weekdayLong(day: DayOfWeek): String = weekdayFull.format(java.time.DayOfWeek.of(day.isoDayNumber))

    /**
     * "Söndag 4 oktober" – Idags rubrikrad (HEM-2) och Dagbokens dagar (HIST-1), med stor bokstav först;
     * [withYear] lägger till året ("Lördag 4 oktober 2025") för en dag ett annat år.
     */
    fun dayAndMonth(date: LocalDate, withYear: Boolean = false): String =
        weekdayDayAndMonth(date, withYear).replaceFirstChar { it.titlecase(swedish) }

    /** "söndag 4 oktober" – som [dayAndMonth] efter en etikett ("Idag · söndag 4 oktober", HIST-1). */
    fun weekdayDayAndMonth(date: LocalDate, withYear: Boolean = false): String =
        (if (withYear) dayMonthYear else dayMonth).format(date.toJavaLocalDate())

    /** ISO-veckans nummer (HEM-2): veckan börjar på måndag och vecka 1 har årets första torsdag. */
    fun isoWeek(date: LocalDate): Int = date.toJavaLocalDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** "7" – dagen i månaden (en uppräkning av datum i samma månad: "6, 9, 12 okt"). */
    fun dayOfMonth(date: LocalDate): String = date.day.toString()

    /** "07:00" – 24 timmar, alltid två siffror. */
    fun time(time: LocalTime): String = "%02d:%02d".format(Locale.ROOT, time.hour, time.minute)

    /** Midnatt UTC, som Material-datumväljaren räknar. */
    fun toEpochMillis(date: LocalDate): Long = date.toEpochDays().toLong() * DAY_MILLIS

    fun fromEpochMillis(millis: Long): LocalDate = LocalDate.fromEpochDays(Math.floorDiv(millis, DAY_MILLIS).toInt())
}

/** Tidsåtgång i text: "45 min", "2 tim", "1 tim 30 min" (AKT-7) – samma överallt. */
@Composable
@ReadOnlyComposable
fun durationText(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(R.string.duration_minutes_format, rest)
        rest == 0 -> stringResource(R.string.duration_hours_format, hours)
        else -> stringResource(R.string.duration_hours_minutes_format, hours, rest)
    }
}

private const val MINUTES_PER_HOUR = 60
