package se.partee71.dagboken.data.repository

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
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
import kotlin.reflect.KClass

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

    /** Steg- och vilopulstrend för [days] dagar bakåt — Trender-diagrammen (TRD-11). Kastar vid fel. */
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

    /**
     * Läser *alla* poster i intervallet genom att följa Health Connects `pageToken`.
     * `readRecords` returnerar en sida i taget (1000 poster som standard); utan den här
     * loopen tappades allt utöver första sidan tyst, vilket slog hårdast mot pulsprover
     * över långa perioder i Trender (TRD-11).
     */
    private suspend fun <T : Record> readAllRecords(
        recordType: KClass<T>,
        range: TimeRangeFilter,
    ): List<T> {
        val all = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(recordType, timeRangeFilter = range, pageToken = token),
            )
            all += response.records
            token = response.pageToken
        } while (token != null)
        return all
    }

    /** Läser periodens steg och väljer den mest kompletta källan. */
    private suspend fun stepsForRange(range: TimeRangeFilter): Long {
        val records = readAllRecords(StepsRecord::class, range)
        return mostCompleteStepSum(records.map { OriginSteps(it.metadata.dataOrigin.packageName, it.count) })
    }

    /** Läser periodens pulsprover med tidsstämpel — underlag för vilopulsskattningen. */
    private suspend fun timedBpmForRange(range: TimeRangeFilter): List<TimedBpm> =
        readAllRecords(HeartRateRecord::class, range)
            .flatMap { record -> record.samples.map { TimedBpm(it.time, it.beatsPerMinute) } }

    override suspend fun readToday(): HealthData = withContext(ioDispatcher) {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val dayRange = TimeRangeFilter.between(startOfDay, now)

        val steps = stepsForRange(dayRange)

        val bpm = readAllRecords(HeartRateRecord::class, dayRange)
            .flatMap { it.samples }
            .map { it.beatsPerMinute }
        val heartRateAvg = if (bpm.isEmpty()) null else bpm.average().roundToLong()

        // Sömn: titta 24h bakåt för att fånga senaste natten.
        val sleepRange = TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now)
        val sleepDuration = readAllRecords(SleepSessionRecord::class, sleepRange)
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
        val restingHrRecords = readAllRecords(RestingHeartRateRecord::class, fullRange)

        // Sömnfönstren för hela perioden läses en gång och används för att sålla bort
        // nattens pulsprover ur vilopulsskattningen (se estimateRestingHeartRate).
        // Startgränsen backas ett dygn så att en session som börjar kvällen före
        // periodens start — eller korsar midnatt inne i perioden — täcker båda dygnen.
        val sleepWindows = readAllRecords(
            SleepSessionRecord::class,
            TimeRangeFilter.between(rangeStart.minus(Duration.ofHours(24)), now),
        ).map { SleepWindow(it.startTime, it.endTime) }

        val daily = ((days - 1).toLong() downTo 0L).map { back ->
            val day = today.minusDays(back)
            val start = day.atStartOfDay(zone).toInstant()
            val rawEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
            val end = if (rawEnd.isAfter(now)) now else rawEnd
            val dayRange = TimeRangeFilter.between(start, end)

            val steps = stepsForRange(dayRange)

            // Vilopuls för dagen till trenddiagrammet: senaste registrerade
            // RestingHeartRateRecord den dagen, annars skattad från dagens egna
            // HeartRateRecord-prover (samma fallback-princip som periodvärdet nedan,
            // fast per dag — grövre med få prover men tillräckligt för en trendlinje).
            val dayRestingHr = restingHrRecords
                .filter { !it.time.isBefore(start) && it.time.isBefore(end) }
                .maxByOrNull { it.time }
                ?.beatsPerMinute
                ?: estimateRestingHeartRate(
                    timedBpmForRange(dayRange),
                    sleepWindows,
                )

            DailySteps(day, steps) to DailyRestingHeartRate(day, dayRestingHr)
        }

        // Senaste vilopuls i perioden (Idag-kortets StatPill) — det senaste registrerade
        // värdet, eller en skattning från periodens pulsprover om posten saknas
        // (fler prover ger en säkrare percentil än en enskild dags).
        val restingHr = restingHrRecords.maxByOrNull { it.time }?.beatsPerMinute
            ?: estimateRestingHeartRate(timedBpmForRange(fullRange), sleepWindows)

        WeeklyHealth(
            dailySteps = daily.map { it.first },
            dailyRestingHeartRate = daily.map { it.second },
            restingHeartRate = restingHr,
        )
    }
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

/** Ett pulsprov med sin tidpunkt — underlag för vilopulsskattningen. */
internal data class TimedBpm(val time: Instant, val bpm: Long)

/** Ett sömnfönster (halvöppet `[start, end)`) från en [SleepSessionRecord]. */
internal data class SleepWindow(val start: Instant, val end: Instant) {
    /** True om [time] ligger i fönstret. Halvöppet så angränsande sessioner inte överlappar. */
    fun contains(time: Instant): Boolean = !time.isBefore(start) && time.isBefore(end)
}

/**
 * Skattar vilopuls från en samling pulsprover när Health Connect saknar en egen
 * [RestingHeartRateRecord] (t.ex. Galaxy Watch via Samsung Health, som inte skriver
 * posten).
 *
 * Prover som ligger inom ett [sleepWindows]-fönster sållas bort först: sömnpulsen —
 * särskilt under djupsömn — ligger klart under den verkliga vilopulsen, och när
 * klockan bärs på natten består annars hela lågänden av nattprover. Skattningen blev
 * då flera slag lägre än Health Connects egen vilopuls.
 *
 * På de vakna proverna gäller samma princip som tidigare: vilopulsen ≈ den lägsta
 * ihållande pulsen, så vi tar medelvärdet av den lägsta 5-percentilen. Det fångar den
 * vilande (låga) änden utan att fastna på ett enda artefaktlågt prov (medelvärdet
 * jämnar ut det). Minst ett prov används alltid.
 *
 * Saknas vakna prover helt (t.ex. ett dygn där klockan bara bars under natten) faller
 * vi tillbaka på hela provmängden — en grov skattning är bättre än "—".
 * Returnerar null endast om [samples] är tom.
 *
 * Ren funktion (inga SDK-beroenden) för enhetstestning (regel 2).
 */
internal fun estimateRestingHeartRate(
    samples: List<TimedBpm>,
    sleepWindows: List<SleepWindow> = emptyList(),
): Long? {
    if (samples.isEmpty()) return null
    val awake = samples.filterNot { sample -> sleepWindows.any { it.contains(sample.time) } }
    val sorted = awake.ifEmpty { samples }.map { it.bpm }.sorted()
    val count = (sorted.size / 20).coerceAtLeast(1)
    return sorted.take(count).average().roundToLong()
}
