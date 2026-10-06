package se.partee71.dagboken.core.engine.health

import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

// Per-källa-principen (HLS-2, HLS-8, HLS-12) och träningspassens dedup på tidsöverlapp (HLS-8) – portade
// från 3.x `HealthConnectRepository.kt` (`mostCompleteSum`, `mostCompleteStepSum`, `mostCompleteExercise`
// och deras `…ByDay`-varianter) utan SDK-typer.

/**
 * Den mest kompletta källans summa: mängderna summeras **per källa** och den högsta summan väljs. Telefonen
 * och klockan skriver samma dygn var för sig, så en summa över källor vore dubbelräkning – och Health
 * Connects `COUNT_TOTAL` tappade steg när källorna inte överlappade (HLS-2). `null` utan poster och när summan
 * är noll – en lucka, aldrig en nolla (HLS-12).
 */
fun mostCompleteSum(samples: List<SummableSample>): Double? =
    samples.groupBy { it.origin }.values.maxOfOrNull { source -> source.sumOf { it.amount } }?.takeIf { it > 0.0 }

/** Stegen enligt [mostCompleteSum], i hela steg; `null` utan poster eller med noll steg (en lucka, HLS-12). */
fun mostCompleteSteps(samples: List<StepSample>): Long? = mostCompleteSum(samples)?.toStepCount()

/** En stegsumma i hela steg; noll steg är ingen mätning utan en lucka (HLS-12). */
internal fun Double.toStepCount(): Long? = roundToLong().takeIf { it > 0 }

/**
 * [mostCompleteSum] **per dygn** (HLS-12): en post hör till dygnet dess starttid faller på, och den mest
 * kompletta källan väljs för varje dygn för sig. Dygn utan poster saknas i kartan.
 */
fun mostCompleteSumByDay(samples: List<SummableSample>, zone: TimeZone): Map<LocalDate, Double> =
    samples.perDay(zone, { it.start }, ::mostCompleteSum)

/** Antal träningspass och deras sammanlagda tid (HLS-8). */
data class ExerciseTotals(val sessions: Int, val duration: Duration)

/**
 * Träningspassen som händelser (HLS-8): pass som **överlappar i tid** är samma händelse skriven av mer än en
 * källa och reduceras till det längsta; pass som inte överlappar räknas var för sig oavsett källa. Ett källval
 * per dygn som för steg kastade i stället bort den andra källans pass (3.x #220). Ett pass som slutar exakt
 * när nästa börjar överlappar inte. Händelserna kommer i tidsordning.
 */
fun distinctExercise(sessions: List<ExerciseSession>): List<ExerciseSession> {
    if (sessions.isEmpty()) return emptyList()
    val sorted = sessions.sortedBy { it.start }
    val events = mutableListOf<ExerciseSession>()
    var longest = sorted.first()
    var groupEnd = longest.end
    for (session in sorted.drop(1)) {
        if (session.start < groupEnd) {
            if (session.duration > longest.duration) longest = session
            if (session.end > groupEnd) groupEnd = session.end
        } else {
            events += longest
            longest = session
            groupEnd = session.end
        }
    }
    events += longest
    return events
}

/** Antal och sammanlagd tid för [distinctExercise] av [sessions]; `null` utan pass. */
fun mostCompleteExercise(sessions: List<ExerciseSession>): ExerciseTotals? =
    distinctExercise(sessions).takeIf { it.isNotEmpty() }?.let(::totalsOf)

private fun totalsOf(events: List<ExerciseSession>) =
    ExerciseTotals(sessions = events.size, duration = events.fold(Duration.ZERO) { acc, s -> acc + s.duration })

/**
 * [mostCompleteExercise] **per dygn** (HLS-12): passen dedupliceras på tidsöverlapp över **hela perioden**
 * först – samma pass från två källor på var sin sida om midnatt räknas en gång – och fördelas sedan efter
 * starttid; ett pass över midnatt hör till dygnet det började.
 */
fun mostCompleteExerciseByDay(sessions: List<ExerciseSession>, zone: TimeZone): Map<LocalDate, ExerciseTotals> =
    distinctExercise(sessions).perDay(zone, { it.start }, ::totalsOf)
