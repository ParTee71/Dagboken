package se.partee71.dagboken.data.repository

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import se.partee71.dagboken.domain.model.DailyRestingHeartRate
import se.partee71.dagboken.domain.model.DailySteps
import se.partee71.dagboken.domain.model.HealthData
import se.partee71.dagboken.domain.model.WeeklyHealth
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * Läsyta mot **Health Connect** (§19 HLS, epic #54). Galaxy Watch synkar via
 * Samsung Health till Health Connect; appen läser den aggregerade datan read-only.
 *
 * Interface så att [se.partee71.dagboken.ui.health.HealthViewModel] kan testas
 * med en fake (regel 2) utan Android-/SDK-beroenden.
 */
interface HealthConnectRepository {
    /** Läsbehörigheterna appen behöver (steg, puls, sömn) — begärs som runtime-samtycke. */
    val permissions: Set<String>

    /** Om Health Connect finns/kan användas på enheten. */
    fun availability(): HealthAvailability

    /** True om samtliga [permissions] är beviljade. */
    suspend fun hasAllPermissions(): Boolean

    /** Läser dagens datapunkter. Kastar vid I/O- eller behörighetsfel (mappas i ViewModel). */
    suspend fun readToday(): HealthData

    /** Steg- och vilopulstrend (7 dagar) + senaste vilopuls för Idag-kortet (HLS-7). Kastar vid fel. */
    suspend fun readWeeklyHealth(): WeeklyHealth
}

/** Health Connect-tillgänglighet, mappad från [HealthConnectClient.getSdkStatus]. */
enum class HealthAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

class HealthConnectRepositoryImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : HealthConnectRepository {

    override val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    // getOrCreate kastar om Health Connect saknas — skapa lazy och först efter
    // att availability() bekräftat AVAILABLE.
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override fun availability(): HealthAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
            else -> HealthAvailability.NOT_INSTALLED
        }

    override suspend fun hasAllPermissions(): Boolean = withContext(ioDispatcher) {
        client.permissionController.getGrantedPermissions().containsAll(permissions)
    }

    override suspend fun readToday(): HealthData = withContext(ioDispatcher) {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val dayRange = TimeRangeFilter.between(startOfDay, now)

        val steps = client.aggregateSteps(dayRange)

        val bpm = client
            .readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = dayRange))
            .records
            .flatMap { it.samples }
            .map { it.beatsPerMinute }
        val heartRateAvg = if (bpm.isEmpty()) null else bpm.average().roundToLong()

        // Sömn: titta 24h bakåt för att fånga senaste natten.
        val sleepRange = TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now)
        val sleepDuration = client
            .readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = sleepRange))
            .records
            .fold(Duration.ZERO) { acc, r -> acc.plus(Duration.between(r.startTime, r.endTime)) }
            .takeIf { !it.isZero }

        HealthData(
            steps = steps.takeIf { it > 0 },
            heartRateAvg = heartRateAvg,
            sleepDuration = sleepDuration,
        )
    }

    override suspend fun readWeeklyHealth(): WeeklyHealth = withContext(ioDispatcher) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = Instant.now()

        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant()
        val weekRange = TimeRangeFilter.between(weekStart, now)
        val restingHrRecords = client
            .readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, timeRangeFilter = weekRange))
            .records

        val daily = (6L downTo 0L).map { back ->
            val day = today.minusDays(back)
            val start = day.atStartOfDay(zone).toInstant()
            val rawEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
            val end = if (rawEnd.isAfter(now)) now else rawEnd
            val dayRange = TimeRangeFilter.between(start, end)

            val steps = client.aggregateSteps(dayRange)

            // Vilopuls för dagen till trenddiagrammet: senaste registrerade
            // RestingHeartRateRecord den dagen, annars skattad från dagens egna
            // HeartRateRecord-prover (samma fallback-princip som veckovärdet nedan,
            // fast per dag — grövre med få prover men tillräckligt för en trendlinje).
            val dayRestingHr = restingHrRecords
                .filter { !it.time.isBefore(start) && it.time.isBefore(end) }
                .maxByOrNull { it.time }
                ?.beatsPerMinute
                ?: estimateRestingHeartRate(
                    client
                        .readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = dayRange))
                        .records
                        .flatMap { it.samples }
                        .map { it.beatsPerMinute },
                )

            DailySteps(day, steps) to DailyRestingHeartRate(day, dayRestingHr)
        }

        // Vilopuls senaste 7 dagarna (kortets StatPill) — det senaste registrerade
        // värdet, eller en skattning från hela veckans pulsprover om posten saknas
        // (fler prover ger en säkrare percentil än en enskild dags).
        val restingHr = restingHrRecords.maxByOrNull { it.time }?.beatsPerMinute
            ?: estimateRestingHeartRate(
                client
                    .readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = weekRange))
                    .records
                    .flatMap { it.samples }
                    .map { it.beatsPerMinute },
            )

        WeeklyHealth(
            dailySteps = daily.map { it.first },
            dailyRestingHeartRate = daily.map { it.second },
            restingHeartRate = restingHr,
        )
    }
}

/** Summerar stegen i [range] via Health Connects aggregeringsmotor. */
private suspend fun HealthConnectClient.aggregateSteps(range: TimeRangeFilter): Long =
    aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = range))[StepsRecord.COUNT_TOTAL] ?: 0L

/**
 * Skattar vilopuls från en samling pulsprover när Health Connect saknar en egen
 * [RestingHeartRateRecord]. Vilopulsen ligger nära den lägsta ihållande pulsen,
 * så vi tar den 5:e percentilen — det fångar vilan utan att fastna på ett enstaka
 * artefaktlågt prov. Med få prover (heltalspercentil = 0) faller den tillbaka på
 * det lägsta värdet. Returnerar null om inga prover finns.
 *
 * Ren funktion (inga SDK-beroenden) för enhetstestning (regel 2).
 */
internal fun estimateRestingHeartRate(bpmSamples: List<Long>): Long? {
    if (bpmSamples.isEmpty()) return null
    val sorted = bpmSamples.sorted()
    val index = ((sorted.size - 1) * 5) / 100
    return sorted[index]
}
