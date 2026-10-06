package se.partee71.dagboken.core.engine.health

import kotlin.time.Duration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.engine.REGULARITY_WINDOW_NIGHTS
import se.partee71.dagboken.core.engine.rollingMidpointSdMinutes
import se.partee71.dagboken.core.model.SleepStages

// Sömnen (HLS-8, HLS-12, HLS-13) – portad från 3.x `HealthConnectRepository.kt` (`summarizeSleepStages`,
// `longestNightPerDay`, `nightlyMidpoints`, `midpointOf`) utan SDK-typer. Spridningen i mittpunkterna räknas
// av `SleepQuality.kt` (`sleepMidpointSdMinutes`, `rollingMidpointSdMinutes`); här tas bara mittpunkterna fram.

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
 * Regelbundenheten **per natt** (HLS-13): spridningen i mittpunkten över de [window] nätter som slutar med
 * natten, räknad av [rollingMidpointSdMinutes]. `null` för en natt vars fönster har för få nätter – då faller
 * komponenten bort och vikterna normaliseras om (HLS-10).
 */
fun nightlyMidpointSdMinutes(
    sessions: List<SleepSession>,
    zone: TimeZone,
    window: Int = REGULARITY_WINDOW_NIGHTS,
): Map<LocalDate, Double?> {
    val midpoints = nightlyMidpoints(sessions, zone)
    return midpoints.keys.zip(rollingMidpointSdMinutes(midpoints.values.toList(), window)).toMap()
}
