package se.partee71.dagboken.core.engine.health

import kotlin.math.roundToLong
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

// Puls och vilopuls (HLS-2, HLS-7, HLS-12) – portade från 3.x `HealthConnectRepository.kt`
// (`estimateRestingHeartRate`, `restingHeartRateByDay`, `averageBpmByDay`) utan SDK-typer.

/** Andelen av de lägsta vakna proverna som vilopulsen skattas ur – den lägsta 5-percentilen. */
private const val LOW_PERCENTILE_DIVISOR = 20

/**
 * Skattar vilopulsen ur pulsprover när Health Connect saknar en `RestingHeartRateRecord` (Galaxy Watch via
 * Samsung Health skriver ingen, HLS-7).
 *
 * Prover inom ett [sleepWindows]-fönster sållas bort först: sömnpulsen ligger under den verkliga vilopulsen
 * och utgjorde annars hela lågänden när klockan bars på natten (3.x #154). På de vakna proverna tas
 * medelvärdet av den lägsta 5-percentilen (minst ett prov) ≈ den lägsta ihållande pulsen; ett enstaka
 * artefaktlågt prov drar inte ner värdet. Saknas vakna prover helt (klockan bars bara på natten) används hela
 * provmängden hellre än "—" – samma fallback som 3.x `estimateRestingHeartRate` (HLS-7), behållen för paritet.
 * `null` bara utan prover.
 */
fun estimateRestingHeartRate(samples: List<HeartRateSample>, sleepWindows: List<TimeSpan> = emptyList()): Long? =
    estimateRestingHeartRate(samples, SleepWindows(sleepWindows))

/** Som ovan med ett färdigt [SleepWindows]-index, så att en period bygger indexet en gång och inte per dygn. */
fun estimateRestingHeartRate(samples: List<HeartRateSample>, sleepWindows: SleepWindows): Long? {
    if (samples.isEmpty()) return null
    val awake = samples.filterNot { it.time in sleepWindows }
    val sorted = awake.ifEmpty { samples }.map { it.bpm }.sorted()
    val count = (sorted.size / LOW_PERCENTILE_DIVISOR).coerceAtLeast(1)
    return sorted.take(count).average().roundToLong()
}

/**
 * Vilopulsen för en period (HLS-7): den senast registrerade `RestingHeartRateRecord` om det finns någon,
 * annars [estimateRestingHeartRate] ur periodens vakna pulsprover.
 */
fun restingHeartRate(
    recorded: List<RestingHeartRateSample>,
    samples: List<HeartRateSample>,
    sleepWindows: List<TimeSpan> = emptyList(),
): Long? = recorded.maxByOrNull { it.time }?.bpm ?: estimateRestingHeartRate(samples, sleepWindows)

/**
 * Vilopulsen **per dygn** (HLS-7, HLS-12): dygnets senast registrerade värde i första hand, annars en
 * skattning ur dygnets egna prover. [sleepWindows] gäller hela perioden, så en session över midnatt sållar
 * bort sina prover från båda dygnen.
 */
fun restingHeartRateByDay(
    recorded: List<RestingHeartRateSample>,
    samples: List<HeartRateSample>,
    sleepWindows: List<TimeSpan>,
    zone: TimeZone,
): Map<LocalDate, Long> {
    val windows = SleepWindows(sleepWindows)
    val estimated = samples.perDay(zone, { it.time }) { estimateRestingHeartRate(it, windows) }
    val measured = recorded.perDay(zone, { it.time }) { day -> day.maxBy { it.time }.bpm }
    // Ett registrerat värde slår alltid skattningen för samma dygn.
    return estimated + measured
}

/** Snittpulsen över [samples], avrundad till heltal; `null` utan prover. */
fun averageBpm(samples: List<HeartRateSample>): Long? = samples.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.roundToLong()

/** Dygnets snittpuls (HLS-12) per dygn. */
fun averageBpmByDay(samples: List<HeartRateSample>, zone: TimeZone): Map<LocalDate, Long> =
    samples.perDay(zone, { it.time }, ::averageBpm)
