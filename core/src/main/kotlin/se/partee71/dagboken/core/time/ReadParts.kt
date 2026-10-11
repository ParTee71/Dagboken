package se.partee71.dagboken.core.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// Läsbudgeten (NFR-24): en period läses med lyssnare bara på det som visas eller nyss hänt, resten ur historikläget.
// Uppdelningen räknas här, så att datalagret bara läser delarna.

/** En del av en läst period: dagarna [days], och om delen lyssnas på ([live]) eller läses i historikläget. */
data class ReadPart(val days: ClosedRange<LocalDate>, val live: Boolean)

/**
 * Delar dagarna [from]…[to] (båda inräknade) i sammanhängande delar, äldst först: dagar inom något av [live] blir
 * lyssnade delar, övriga historikdelar. Överlappande eller angränsande intervall i [live] slås ihop, delar utanför
 * [from]…[to] kapas bort, och varje dag hamnar i exakt en del. Tom när [to] ligger före [from].
 */
fun readParts(from: LocalDate, to: LocalDate, live: List<ClosedRange<LocalDate>>): List<ReadPart> {
    if (to < from) return emptyList()
    val parts = mutableListOf<ReadPart>()
    var next = from
    for (range in mergeDays(live)) {
        val start = maxOf(range.start, from)
        val end = minOf(range.endInclusive, to)
        if (start > end) continue
        if (start > next) parts += ReadPart(next..start.minus(1, DateTimeUnit.DAY), live = false)
        parts += ReadPart(start..end, live = true)
        next = end.plus(1, DateTimeUnit.DAY)
    }
    if (next <= to) parts += ReadPart(next..to, live = false)
    return parts
}

/** [ranges] sorterade och ihopslagna där de överlappar eller möts; tomma intervall (slut före start) tas bort. */
fun mergeDays(ranges: List<ClosedRange<LocalDate>>): List<ClosedRange<LocalDate>> {
    val merged = mutableListOf<ClosedRange<LocalDate>>()
    for (range in ranges.filter { it.start <= it.endInclusive }.sortedBy { it.start }) {
        val last = merged.lastOrNull()
        if (last != null && range.start <= last.endInclusive.plus(1, DateTimeUnit.DAY)) {
            merged[merged.lastIndex] = last.start..maxOf(last.endInclusive, range.endInclusive)
        } else {
            merged += range
        }
    }
    return merged
}

/**
 * De senaste [RECENT_DAYS] dagarna till och med [today] – det Dagbok och Trender lyssnar på (NFR-24); äldre dagar läses i
 * historikläget. Samma intervall i båda flikarna, så att Firestore delar lyssnaren när båda är öppna.
 */
fun recentDays(today: LocalDate): ClosedRange<LocalDate> = today.minus(RECENT_DAYS - 1, DateTimeUnit.DAY)..today

/** Så många dagar till och med idag lyssnar Dagbok och Trender på (NFR-24) – Trenders period Månad (TRD-3). */
const val RECENT_DAYS = 30
