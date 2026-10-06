package se.partee71.dagboken.core.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

// Tillvalet "Föregående period" (TRD-18): de lika många dagarna omedelbart före perioden, på samma
// x-index som den nuvarande (dag 1 mot dag 1) – det är formerna som jämförs, inte datumen.

/**
 * Föregående periods dagar, äldst först – lika många som [TrendRange.days] och slutar dagen före
 * periodens första dag. `null` för "Allt": utan nedre gräns finns ingen föregående period, och
 * tillvalet visas inte alls (TRD-18).
 */
fun TrendRange.previousDays(today: LocalDate): List<LocalDate>? {
    val length = days ?: return null
    return daysEnding(from(today).minus(1, DateTimeUnit.DAY), length)
}

/** Om tillvalet finns för perioden: alla utom "Allt". */
val TrendRange.hasPreviousPeriod: Boolean get() = days != null

/**
 * Periodens läsning: posterna från [from] till och med [today] täcker både den nuvarande perioden och –
 * när [withPrevious] – den föregående, i **en** läsning (TRD-18). "Allt" läser från [TrendRange.ALL_FROM].
 */
fun TrendRange.readFrom(today: LocalDate, withPrevious: Boolean): LocalDate =
    if (withPrevious) previousDays(today)?.first() ?: from(today) else from(today)
