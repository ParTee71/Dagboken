package se.partee71.dagboken.data.repository

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
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

    /** Steg- och vilopulstrend för [days] dagar bakåt — Trender-diagrammen (TRD-10). Kastar vid fel. */
    suspend fun readHealthRange(days: Int): WeeklyHealth
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

        val steps = client.stepsForRange(dayRange)

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

    override suspend fun readWeeklyHealth(): WeeklyHealth = readHealthRange(7)

    override suspend fun readHealthRange(days: Int): WeeklyHealth = withContext(ioDispatcher) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = Instant.now()

        val rangeStart = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
        val fullRange = TimeRangeFilter.between(rangeStart, now)
        val restingHrRecords = client
            .readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, timeRangeFilter = fullRange))
            .records

        val daily = ((days - 1).toLong() downTo 0L).map { back ->
            val day = today.minusDays(back)
            val start = day.atStartOfDay(zone).toInstant()
            val rawEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
            val end = if (rawEnd.isAfter(now)) now else rawEnd
            val dayRange = TimeRangeFilter.between(start, end)

            val steps = client.stepsForRange(dayRange)

            // Vilopuls för dagen till trenddiagrammet: senaste registrerade
            // RestingHeartRateRecord den dagen, annars skattad från dagens egna
            // HeartRateRecord-prover (samma fallback-princip som periodvärdet nedan,
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

        // Senaste vilopuls i perioden (Idag-kortets StatPill) — det senaste registrerade
        // värdet, eller en skattning från periodens pulsprover om posten saknas
        // (fler prover ger en säkrare percentil än en enskild dags).
        val restingHr = restingHrRecords.maxByOrNull { it.time }?.beatsPerMinute
            ?: estimateRestingHeartRate(
                client
                    .readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = fullRange))
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

/** Läser dagens/periodens steg ur Health Connect och väljer den mest kompletta källan. */
private suspend fun HealthConnectClient.stepsForRange(range: TimeRangeFilter): Long {
    val records = readRecords(ReadRecordsRequest(StepsRecord::class, timeRangeFilter = range)).records
    return mostCompleteStepSum(records.map { OriginSteps(it.metadata.dataOrigin.packageName, it.count) })
}

/** En stegpost knuten till sin källa (dataOrigin-paketnamn). */
internal data class OriginSteps(val origin: String, val count: Long)

/**
 * Väljer den mest kompletta stegsumman när flera källor skrivit steg för samma
 * period. Health Connects `COUNT_TOTAL` de-dupliderar per tidslucka och kan då
 * tappa steg när källorna (t.ex. telefonens pedometer + Galaxy Watch via Samsung
 * Health) inte överlappar perfekt — appen visade då färre steg än den bärbara
 * enheten. Vi summerar i stället per källa och tar den högsta summan; ingen
 * dubbelräkning eftersom vi aldrig summerar över källor. Returnerar 0 om inga
 * poster finns.
 *
 * Ren funktion (inga SDK-beroenden) för enhetstestning (regel 2).
 */
internal fun mostCompleteStepSum(records: List<OriginSteps>): Long =
    records.groupBy { it.origin }
        .values
        .maxOfOrNull { origin -> origin.sumOf(OriginSteps::count) }
        ?: 0L

/**
 * Skattar vilopuls från en samling pulsprover när Health Connect saknar en egen
 * [RestingHeartRateRecord]. Vilopulsen ≈ den lägsta ihållande pulsen (t.ex. under
 * djupsömn), så vi tar medelvärdet av den lägsta 5-percentilen: det fångar den
 * vilande (låga) änden utan att fastna på ett enda artefaktlågt prov (medelvärdet
 * jämnar ut det). Minst ett prov används alltid. Returnerar null om inga prover finns.
 *
 * Ren funktion (inga SDK-beroenden) för enhetstestning (regel 2).
 */
internal fun estimateRestingHeartRate(bpmSamples: List<Long>): Long? {
    if (bpmSamples.isEmpty()) return null
    val sorted = bpmSamples.sorted()
    val count = (sorted.size / 20).coerceAtLeast(1)
    return sorted.take(count).average().roundToLong()
}
