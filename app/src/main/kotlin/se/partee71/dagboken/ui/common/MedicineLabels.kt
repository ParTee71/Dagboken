package se.partee71.dagboken.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.doseSlots
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule

// Medicinernas texter i UI:t – enda stället (regel 4), för Mediciner nu och Idag och påminnelserna sen.

/** "Levaxin 100 µg" – namn, dos och enhet; det som saknas utelämnas. */
fun medicineTitle(name: String, dose: String, unit: String): String =
    listOf(name, dose, unit).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

/** "75 mg" – en dos med enhet. */
fun doseText(dose: String, unit: String): String = medicineTitle("", dose, unit)

/** "29 sep – 12 okt", "från 29 sep", "t.o.m. 12 okt" eller "tills vidare" – en period eller höjning. */
@Composable
@ReadOnlyComposable
fun periodText(start: LocalDate?, end: LocalDate?): String = when {
    start != null && end != null -> stringResource(R.string.date_range_format, DateFormat.short(start), DateFormat.short(end))
    end != null -> stringResource(R.string.date_until_format, DateFormat.short(end))
    start != null -> stringResource(R.string.date_from_format, DateFormat.short(start))
    else -> stringResource(R.string.until_further_notice)
}

/** "dagligen", "vardagar", "mån, ons, fre", "var 3:e dag" – receptets upprepning (REC-2…REC-4). */
@Composable
@ReadOnlyComposable
fun repeatText(schedule: Schedule): String = when (schedule) {
    is Schedule.Unknown -> stringResource(R.string.repeat_unknown)
    is Schedule.Repeating -> when (schedule.repeat) {
        Repeat.DAILY -> stringResource(R.string.repeat_daily)
        Repeat.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
        Repeat.WEEKENDS -> stringResource(R.string.repeat_weekends)
        Repeat.CUSTOM -> schedule.days.sorted().joinToString(", ") { DateFormat.weekdayShort(it) }
        Repeat.INTERVAL ->
            if (schedule.intervalDays <= 1) {
                stringResource(R.string.repeat_daily)
            } else {
                stringResource(if (ordinalTakesA(schedule.intervalDays)) R.string.repeat_interval_a_format else R.string.repeat_interval_format, schedule.intervalDays)
            }
    }
}

/** "Måndag, onsdag och fredag" – veckodagarna i veckans ordning (REC-3); inga dagar ger en tom text. */
@Composable
@ReadOnlyComposable
fun weekdaysText(days: Set<DayOfWeek>): String =
    andList(days.sorted().map(DateFormat::weekdayLong)).replaceFirstChar { it.titlecase() }

/** "6, 9, 12 okt" – datum i en uppräkning; månaden skrivs ut när den byts och sist ("30 okt, 2 nov"). */
fun datesText(dates: List<LocalDate>): String =
    dates.mapIndexed { i, date ->
        val next = dates.getOrNull(i + 1)
        if (next != null && next.month == date.month && next.year == date.year) DateFormat.dayOfMonth(date) else DateFormat.short(date)
    }.joinToString(", ")

/** "a, b och c" – en svensk uppräkning. */
@Composable
@ReadOnlyComposable
private fun andList(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> stringResource(R.string.list_and_format, items.dropLast(1).joinToString(", "), items.last())
}

/** Svenska ordningstal: `:a` efter 1 och 2 (2:a, 21:a, 102:a) men inte 11 och 12 (11:e, 12:e); annars `:e`. */
fun ordinalTakesA(n: Int): Boolean = n % 10 in 1..2 && n % 100 !in 11..12

/**
 * Receptkortets undertext (MEDF-1): tidpunkterna, upprepningen och – för ett recept tills vidare –
 * "tills vidare" ("Morgon · Kväll · dagligen · tills vidare"). En period med slut visas som pill.
 * Inga tidpunkter är Morgon, som i dosgenereringen (MED-4).
 */
@Composable
@ReadOnlyComposable
fun prescriptionSubtitle(prescription: Prescription): String {
    val slots = prescription.slots.ifEmpty { prescription.doseSlots() }.distinct().map { stringResource(it.label()) }
    val period = if (prescription.period.end == null) listOf(stringResource(R.string.until_further_notice)) else emptyList()
    return (slots + repeatText(prescription.schedule) + period).joinToString(" · ")
}

/** "Minst 4 h mellan · högst 8 per dag", "Högst 2 per dag" eller "Ingen gräns" – kylperiod och dagsgräns (FAV-4, FAV-5). */
@Composable
@ReadOnlyComposable
fun prnLimits(medicine: PrnMedicine): String {
    val hours = medicine.minHoursBetween
    val max = medicine.maxPerDay
    return when {
        hours > 0 && max > 0 -> stringResource(R.string.prn_both_limits_format, hours, max)
        hours > 0 -> stringResource(R.string.prn_min_hours_format, hours)
        max > 0 -> stringResource(R.string.prn_max_per_day_format, max)
        else -> stringResource(R.string.prn_no_limit)
    }
}
