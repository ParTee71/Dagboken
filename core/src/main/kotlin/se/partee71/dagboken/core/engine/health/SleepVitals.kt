package se.partee71.dagboken.core.engine.health

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

// Nattens puls och syremättnad (HLS-10, HLS-13) – underlaget för sömnkvalitetens varningsrader. Portat från 3.x
// `readSleepMeasurements` och `readSleepMeasurementsHistory` i `HealthConnectRepository.kt` utan SDK-typer. Värdena
// persisteras aldrig (HLS-5); de räknas om vid varje läsning.

/**
 * En natts mätvärden vid sidan av sömnlängden och stadierna: [heartRate] är snittpulsen i sömnfönstret (sovpulsen),
 * [baselineHeartRate] den vakna baslinjen den jämförs mot och [oxygenSaturation] snittet av syremättnaden i
 * sömnfönstret. `null` där inget mättes.
 */
data class SleepVitals(val heartRate: Long?, val baselineHeartRate: Long?, val oxygenSaturation: Double?)

/**
 * Nattens [SleepVitals] för varje natt i [nights] (en session per datum, se [longestNightPerDay]) – dagshistorikens
 * underlag för varningsraderna ([healthHistory]).
 *
 * - Sovpulsen och syremättnaden är snitten av proven inom nattens fönster `[start, end)`, som i 3.x.
 * - Baslinjen är [awakeHeartRateBaseline] över alla lästa pulsprov utanför [sleep] – den vakna pulsen för den lästa
 *   perioden, gemensam för periodens nätter (3.x `readSleepMeasurements`). Utan vakna prov är den `null`: en baslinje
 *   ur sömnprov skulle ge en falsk varning.
 *
 * [heartRate] ska vara **sorterad efter tid** (dagshistorikens delade lista); varje natt slås upp med tvådelning, så ett
 * år av nätter kostar inte nätter × prov. [sleep] är dagshistorikens delade index.
 */
fun sleepVitalsByNight(
    heartRate: List<HeartRateSample>,
    oxygen: List<OxygenSample>,
    sleep: SleepWindows,
    nights: Map<LocalDate, TimeSpan>,
): Map<LocalDate, SleepVitals> {
    if (nights.isEmpty()) return emptyMap()
    val baseline = awakeHeartRateBaseline(heartRate, sleep)
    val sortedOxygen = oxygen.sortedBy { it.time }
    return nights.mapValues { (_, night) ->
        SleepVitals(
            heartRate = averageBpm(heartRate.during(night) { it.time }),
            baselineHeartRate = baseline,
            oxygenSaturation = averageOxygen(sortedOxygen.during(night) { it.time }),
        )
    }
}

/** Proven i [span] (halvöppet, som [TimeSpan]) ur en lista sorterad efter [time]. */
private inline fun <T> List<T>.during(span: TimeSpan, time: (T) -> Instant): List<T> {
    // Första provet som inte ligger före fönstret.
    var low = 0
    var high = size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (time(this[mid]) < span.start) low = mid + 1 else high = mid
    }
    var end = low
    while (end < size && time(this[end]) < span.end) end++
    return subList(low, end)
}
