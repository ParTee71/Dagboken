package se.partee71.dagboken.core.engine.health

import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.engine.REGULARITY_WINDOW_NIGHTS
import se.partee71.dagboken.core.engine.circularSdMinutes
import se.partee71.dagboken.core.engine.dayAngle
import se.partee71.dagboken.core.model.SleepStages

// Sömnen (HLS-8, HLS-12, HLS-13) – portad från 3.x `HealthConnectRepository.kt` (`summarizeSleepStages`,
// `longestNightPerDay`, `nightlyMidpoints`, `midpointOf`) utan SDK-typer. Spridningen räknas cirkulärt i
// `SleepQuality.kt` (`circularSdMinutes`); här det glidande fönstret per natt (`midpointSdByNight`).

/**
 * Nattens stadier per kategori (HLS-8): djup, REM, lätt och vaken. [SleepStageType.SLEEPING] (ospecificerad
 * sömn) räknas som lätt – annars ser en natt utan indelning tommare ut än den var – och
 * [SleepStageType.AWAKE_IN_BED]/[SleepStageType.OUT_OF_BED] som vaken; [SleepStageType.UNKNOWN] ignoreras.
 * En kategori utan tid är `null`, inte noll, så att skärmen visar "—".
 */
fun summarizeSleepStages(slices: List<SleepStageSlice>): SleepStages {
    fun sum(vararg types: SleepStageType): Duration? = slices
        .filter { it.type in types }
        .fold(Duration.ZERO) { acc, slice -> acc + slice.duration }
        .takeIf { it.isPositive() }

    return SleepStages(
        deep = sum(SleepStageType.DEEP),
        rem = sum(SleepStageType.REM),
        light = sum(SleepStageType.LIGHT, SleepStageType.SLEEPING),
        awake = sum(SleepStageType.AWAKE, SleepStageType.AWAKE_IN_BED, SleepStageType.OUT_OF_BED),
    )
}

/**
 * En natt per dygn (HLS-12), daterad efter sessionens **slut** – en session över midnatt hör till morgonens
 * datum. Flera sessioner samma natt (Samsung delar ibland en avbruten sömn i två) reduceras till den längsta,
 * så att natten inte räknas som två kortare. Resultatet är i datumordning. Som i 3.x blir en tupplur dygnets
 * "natt" när dygnet saknar nattsömn – och förlorar mot nattsömnen när den är längre.
 */
fun longestNightPerDay(sessions: List<SleepSession>, zone: TimeZone): Map<LocalDate, SleepSession> =
    sessions.perDay(zone, { it.end }) { night -> night.maxBy { it.duration } }.toSortedMap()

/** Fönstrets mittpunkt som klockslag i [zone] – mätt i verklig tid, så en natt över sommartidsbytet blir rätt. */
fun midpointOf(span: TimeSpan, zone: TimeZone): LocalTime = (span.start + span.duration / 2).toLocalDateTime(zone).time

/**
 * Sömnens mittpunkt per natt (HLS-10, HLS-13), en per dygn enligt [longestNightPerDay] och i datumordning –
 * underlaget till regelbundenheten. Två sessioner samma natt skulle annars se ut som två läggtider och blåsa
 * upp spridningen.
 */
fun nightlyMidpoints(sessions: List<SleepSession>, zone: TimeZone): Map<LocalDate, LocalTime> =
    longestNightPerDay(sessions, zone).mapValues { (_, night) -> midpointOf(night, zone) }

/**
 * Regelbundenheten per natt (HLS-10, HLS-13): varje natt i [midpoints] får spridningen i mittpunkten över nätterna
 * inom de [days] **dygn** som slutar med den – ett glidande fönster, linjärt i antalet nätter. Fönstret räknas i dygn
 * och inte i antal nätter, så att samma natt får samma värde oavsett hur långt bakåt historiken lästs (Hälsa idag och
 * Trenders perioder läser alla [days] − 1 dygn sömn före sin första dag, se [healthReadWindows]). För få nätter i
 * fönstret ger `null` – då faller komponenten bort och vikterna normaliseras om.
 */
fun midpointSdByNight(midpoints: Map<LocalDate, LocalTime>, days: Int = REGULARITY_WINDOW_NIGHTS): Map<LocalDate, Double?> {
    val nights = midpoints.entries.sortedBy { it.key }.map { (date, time) -> date to time.dayAngle() }
    val result = LinkedHashMap<LocalDate, Double?>(nights.size)
    var first = 0
    var sumSin = 0.0
    var sumCos = 0.0
    for ((last, night) in nights.withIndex()) {
        val (date, angle) = night
        sumSin += sin(angle)
        sumCos += cos(angle)
        val windowStart = date.minus(days - 1, DateTimeUnit.DAY)
        while (nights[first].first < windowStart) {
            sumSin -= sin(nights[first].second)
            sumCos -= cos(nights[first].second)
            first++
        }
        result[date] = circularSdMinutes(sumSin, sumCos, last - first + 1)
    }
    return result
}
