package se.partee71.dagboken.core.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import se.partee71.dagboken.core.time.datesBetween

// Trenders periodval (TRD-3) – portat från 3.x `TrenderRange` i `TrenderViewModel`; här räknas även
// periodens dagar, så att ViewModeln bara läser och visar.

/** Periodvalet i ett diagramkort (TRD-3): [days] dagar till och med idag, eller `null` för "Allt". */
enum class TrendRange(val days: Int?) {
    SEVEN_DAYS(7),
    FOURTEEN_DAYS(14),
    MONTH(30),
    THREE_MONTHS(90),
    ALL(null),
    ;

    /** Periodens första dag; för "Allt" [ALL_FROM] – ingen nedre datumgräns, det räknas ur cachen. */
    fun from(today: LocalDate): LocalDate = days?.let { today.minus(it - 1, DateTimeUnit.DAY) } ?: ALL_FROM

    /**
     * Periodens dagar, äldst först, till och med [today] (x-axeln): en fast period är alltid lika många
     * dagar oavsett data; "Allt" går från [earliest] – den första dagen med en post – till idag, och är tom
     * utan poster. En [earliest] efter idag ger bara idag.
     */
    fun days(today: LocalDate, earliest: LocalDate? = null): List<LocalDate> =
        if (days != null) daysEnding(today, days) else earliest?.let { datesBetween(minOf(it, today), today) }.orEmpty()

    companion object {
        /** Standardperioden i varje kort (TRD-3). */
        val DEFAULT = MONTH

        /** Läsningens nedre gräns för "Allt" – före alla poster i en dagbok (3.x startade 2024). */
        val ALL_FROM = LocalDate(2000, 1, 1)
    }
}

/** Den första dagen bland [dates] (poster utan dag räknas inte), eller `null` utan någon. */
fun earliestDate(dates: Iterable<LocalDate?>): LocalDate? = dates.filterNotNull().minOrNull()
