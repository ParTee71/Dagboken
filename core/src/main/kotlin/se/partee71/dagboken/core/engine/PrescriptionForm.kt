package se.partee71.dagboken.core.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule

// Receptformulärets val (REC-2…REC-4, REC-7, MEDF-5): hur upprepningen och perioden visas som ett val
// var, och vad ett byte av val gör med receptet – så att `:app` bara visar valen.

/** Upprepningen som ett val i formuläret: Varje dag · Veckodagar · Var X:e dag (REC-2). */
enum class RepeatChoice { EVERY_DAY, WEEKDAYS, INTERVAL }

/** Måndag–fredag – [Repeat.WEEKDAYS]. */
val WORKWEEK: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

/** Valet som upprepningen visas som: vardagar, helger och anpassade dagar är alla Veckodagar. */
fun Schedule.Repeating.choice(): RepeatChoice = when (repeat) {
    Repeat.DAILY -> RepeatChoice.EVERY_DAY
    Repeat.WEEKDAYS, Repeat.WEEKENDS, Repeat.CUSTOM -> RepeatChoice.WEEKDAYS
    Repeat.INTERVAL -> RepeatChoice.INTERVAL
}

/**
 * Dagarna som är valda under Veckodagar: mån–fre för vardagar, lör–sön för helger, annars de lagrade
 * dagarna (också när ett annat val är aktivt – de bevaras, som i 3.x).
 */
fun Schedule.Repeating.chosenDays(): Set<DayOfWeek> = when (repeat) {
    Repeat.WEEKDAYS -> WORKWEEK
    Repeat.WEEKENDS -> WEEKEND
    else -> days
}

/**
 * REC-2, REC-3: veckodagarna [days] som upprepning – mån–fre sparas som vardagar och lör–sön som
 * helger, så att det ser ut som 3.x-data; övriga kombinationer (också ingen dag) som anpassad.
 */
fun Schedule.Repeating.withDays(days: Set<DayOfWeek>): Schedule.Repeating = copy(
    repeat = when (days) {
        WORKWEEK -> Repeat.WEEKDAYS
        WEEKEND -> Repeat.WEEKENDS
        else -> Repeat.CUSTOM
    },
    days = days,
)

/**
 * Byte av val. De lagrade dagarna och intervallet följer med oförändrade (ett byte och tillbaka tappar
 * dem inte); Var X:e dag är minst varannan dag ([MIN_INTERVAL_DAYS]) – ett intervall på 1 är dagligen.
 */
fun Schedule.Repeating.withChoice(choice: RepeatChoice): Schedule.Repeating = when {
    choice == choice() -> this
    choice == RepeatChoice.EVERY_DAY -> copy(repeat = Repeat.DAILY)
    choice == RepeatChoice.WEEKDAYS -> withDays(chosenDays())
    else -> copy(repeat = Repeat.INTERVAL, intervalDays = intervalDays.coerceAtLeast(MIN_INTERVAL_DAYS))
}

/** Minsta intervallet under Var X:e dag. */
const val MIN_INTERVAL_DAYS = 2

/** REC-4: de första [count] dosdagarna för var [intervalDays]:e dag från [start], startdagen inräknad. */
fun intervalDaysFrom(start: LocalDate, intervalDays: Int, count: Int = 3): List<LocalDate> =
    List(count) { n -> start.plus(n * intervalDays.coerceAtLeast(1), DateTimeUnit.DAY) }

/** Perioden som ett val i formuläret: Tills vidare · Längd · T.o.m. (REC-7). */
enum class PeriodChoice { UNTIL_FURTHER_NOTICE, LENGTH, END_DATE }

/** Valet en lagrad period visas med: utan slut tills vidare, annars T.o.m. (som 3.x). */
fun Period.choice(): PeriodChoice = if (end == null) PeriodChoice.UNTIL_FURTHER_NOTICE else PeriodChoice.END_DATE

/** Antal dagar i perioden, start- och slutdagen inräknade; `null` utan start eller slut. */
fun Period.lengthDays(): Int? {
    val start = start ?: return null
    val end = end ?: return null
    return start.daysUntil(end) + 1
}

/** Perioden med [days] dagar från starten (minst en); utan start oförändrad. */
fun Period.withLength(days: Int): Period = start?.let { copy(end = it.plus(days.coerceAtLeast(1) - 1, DateTimeUnit.DAY)) } ?: this

/**
 * Byte av val: Tills vidare tar bort slutet; Längd och T.o.m. behåller ett slut och ger annars
 * [DEFAULT_PERIOD_DAYS] dagar från starten (som 3.x). Längd och T.o.m. är samma period – bara visad
 * olika – så ett byte mellan dem ändrar inget.
 */
fun Period.withChoice(choice: PeriodChoice): Period = when {
    choice == PeriodChoice.UNTIL_FURTHER_NOTICE -> copy(end = null)
    end != null -> this
    else -> withLength(DEFAULT_PERIOD_DAYS)
}

/**
 * Ny start. Med [keepLength] (valet Längd) flyttas slutet med, så att antalet dagar står kvar; annars
 * (T.o.m.) står slutet kvar.
 */
fun Period.withStart(start: LocalDate, keepLength: Boolean): Period {
    val length = lengthDays()
    val moved = copy(start = start)
    return if (keepLength && length != null) moved.withLength(length) else moved
}

/** Längden en ny period får när ett slut väljs, och en förlängning utan känd längd (3.x: 14). */
const val DEFAULT_PERIOD_DAYS = 14

/**
 * REC-4, REC-7: receptet som formuläret visar det – alltid med startdatum. Saknas det (migrerade recept
 * från före periodstödet) förväljs dagen intervallet redan räknas från ([intervalAnchor], skapandedagen),
 * så att dosdagarna inte flyttas; finns ingen sådan, [today].
 */
fun Prescription.withFormStart(today: LocalDate): Prescription =
    if (period.start != null) this else copy(period = period.copy(start = intervalAnchor() ?: today))

/**
 * MEDF-5, REC-8: "Förläng och aktivera" – en ny period från [today] lika lång som den förra
 * ([DEFAULT_PERIOD_DAYS] när den inte går att räkna ut), och receptet aktivt. Daterade doshöjningar
 * flyttas lika mycket som perioden, så att kuren upprepas med samma höjningar och de ryms i den nya
 * perioden (en höjning utanför perioden går inte att spara, REC-9); en höjning utan start lämnas orörd.
 */
fun Prescription.extendedFrom(today: LocalDate): Prescription {
    val length = period.lengthDays()?.takeIf { it >= 1 } ?: DEFAULT_PERIOD_DAYS
    val shift = period.start?.daysUntil(today)
    val boosts = if (shift == null) {
        boosts
    } else {
        boosts.map { boost ->
            if (boost.start == null) boost else boost.copy(start = boost.start.plus(shift, DateTimeUnit.DAY), end = boost.end?.plus(shift, DateTimeUnit.DAY))
        }
    }
    return copy(period = Period(today).withLength(length), boosts = boosts, active = true)
}
