package se.partee71.dagboken.core.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
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
