package se.partee71.dagboken.data.repository

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import se.partee71.dagboken.domain.model.BloodPressure
import se.partee71.dagboken.domain.model.DailyHealth
import se.partee71.dagboken.domain.model.DailyRestingHeartRate
import se.partee71.dagboken.domain.model.DailySteps
import se.partee71.dagboken.domain.model.HealthData
import se.partee71.dagboken.domain.model.HealthHistory
import se.partee71.dagboken.domain.model.NightlySleepMeasurements
import se.partee71.dagboken.domain.model.SleepMeasurements
import se.partee71.dagboken.domain.model.SleepStages
import se.partee71.dagboken.domain.model.WeeklyHealth
import se.partee71.dagboken.domain.model.rollingMidpointSdMinutes
import se.partee71.dagboken.domain.model.sleepMidpointSdMinutes
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt
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
    /**
     * Samtliga läsbehörigheter appen begär som runtime-samtycke. Utöver kärnan
     * (se [requiredPermissions]) ingår de valfria typerna från HLS-8 — sömnstadier
     * ryms i `READ_SLEEP`, medan träning, aktiva kalorier, sträcka, syremättnad och
     * blodtryck har egna behörigheter — samt historikbehörigheten (se HLS-9).
     */
    val permissions: Set<String>

    /**
     * Kärnbehörigheterna (steg, puls, vilopuls, sömn) som Hälsa-skärmen kräver för att
     * visa data alls. Övriga [permissions] är valfria: nekas de saknas bara den
     * datapunkten, i stället för att låsa hela skärmen i behörighetsläge.
     */
    val requiredPermissions: Set<String>

    /** Om Health Connect finns/kan användas på enheten. */
    fun availability(): HealthAvailability

    /** True om samtliga [requiredPermissions] är beviljade. */
    suspend fun hasRequiredPermissions(): Boolean

    /** Läser dagens datapunkter. Kastar vid I/O- eller behörighetsfel (mappas i ViewModel). */
    suspend fun readToday(): HealthData

    /** Steg- och vilopulstrend (7 dagar) + senaste vilopuls för Idag-kortet (HLS-7). Kastar vid fel. */
    suspend fun readWeeklyHealth(): WeeklyHealth

    /** Steg- och vilopulstrend för [days] dagar bakåt — Trender-diagrammen (TRD-11). Kastar vid fel. */
    suspend fun readHealthRange(days: Int): WeeklyHealth

    /**
     * **Dagshistorik** för samtliga datapunkter i HLS-2/HLS-8 — ett [DailyHealth] per dygn
     * i perioden, äldst → nyast (HLS-12). Underlaget till Trenders hälsodiagram.
     *
     * Till skillnad från [readHealthRange], som bara ger steg och vilopuls, läses här även
     * sömn, sömnstadier, träning, aktiva kalorier, sträcka, syremättnad och blodtryck. Varje
     * posttyp läses **en gång** över hela perioden och fördelas per dygn i efterhand — en
     * läsning per dag och typ blir tusentals anrop över ett år. Kastar vid fel.
     */
    suspend fun readHealthHistory(days: Int): HealthHistory

    /**
     * Underlaget för sömnkvaliteten (HLS-10): senaste nattens sömn plus de rullande
     * måtten som kräver flera nätter (regelbundenhet, pulsbaslinje). [nights] styr hur
     * långt bakåt regelbundenheten och baslinjen räknas.
     *
     * Returnerar null om ingen sömnsession finns för senaste dygnet. Kastar vid fel.
     */
    suspend fun readSleepMeasurements(nights: Int = DEFAULT_SLEEP_QUALITY_NIGHTS): SleepMeasurements?

    /**
     * Samma underlag som [readSleepMeasurements], fast **per natt** över [nights] nätter
     * (HLS-13) — så sömnkvaliteten går att räkna bakåt i tiden och inte bara för senaste
     * natten. Regelbundenheten är rullande: varje natt bär spridningen över de nätter som
     * slutar med den (se `rollingMidpointSdMinutes`).
     *
     * Returnerar en tom lista när perioden saknar sömnsessioner. Kastar vid fel.
     */
    suspend fun readSleepMeasurementsHistory(
        nights: Int = DEFAULT_SLEEP_QUALITY_NIGHTS,
    ): List<NightlySleepMeasurements>
}

/** Fönstret för regelbundenhet och pulsbaslinje i sömnkvaliteten (HLS-10). */
const val DEFAULT_SLEEP_QUALITY_NIGHTS = 14

/** Health Connect-tillgänglighet, mappad från [HealthConnectClient.getSdkStatus]. */
enum class HealthAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

class HealthConnectRepositoryImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : HealthConnectRepository {

    override val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    override val permissions: Set<String> = requiredPermissions + setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
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

    override suspend fun hasRequiredPermissions(): Boolean = withContext(ioDispatcher) {
        client.permissionController.getGrantedPermissions().containsAll(requiredPermissions)
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

    /**
     * Som [readAllRecords], men hoppar över typer vars läsbehörighet inte är beviljad.
     * Health Connect kastar `SecurityException` vid läsning utan behörighet, och de
     * valfria typerna i HLS-8 får inte kunna fälla hela skärmen om användaren nekar
     * en enskild av dem i samtyckesdialogen.
     */
    private suspend fun <T : Record> readIfGranted(
        recordType: KClass<T>,
        range: TimeRangeFilter,
        granted: Set<String>,
    ): List<T> =
        if (HealthPermission.getReadPermission(recordType) in granted) {
            readAllRecords(recordType, range)
        } else {
            emptyList()
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

        // De valfria typerna (HLS-8) läses bara om behörigheten faktiskt beviljats.
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())

        val steps = stepsForRange(dayRange)

        val bpm = readAllRecords(HeartRateRecord::class, dayRange)
            .flatMap { it.samples }
            .map { it.beatsPerMinute }
        val heartRateAvg = if (bpm.isEmpty()) null else bpm.average().roundToLong()

        // Sömn: titta 24h bakåt för att fånga senaste natten.
        val nightRange = TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now)
        val sleepSessions = readAllRecords(SleepSessionRecord::class, nightRange)
        val sleepDuration = sleepSessions
            .fold(Duration.ZERO) { acc, r -> acc.plus(Duration.between(r.startTime, r.endTime)) }
            .takeIf { !it.isZero }
        val sleepStages = summarizeSleepStages(
            sleepSessions.flatMap { session ->
                session.stages.map { StageSlice(it.stage, Duration.between(it.startTime, it.endTime)) }
            },
        )

        val exercise = mostCompleteExercise(
            readIfGranted(ExerciseSessionRecord::class, dayRange, granted).map {
                OriginSession(it.metadata.dataOrigin.packageName, Duration.between(it.startTime, it.endTime))
            },
        )

        val activeEnergy = mostCompleteSum(
            readIfGranted(ActiveCaloriesBurnedRecord::class, dayRange, granted).map {
                OriginAmount(it.metadata.dataOrigin.packageName, it.energy.inKilocalories)
            },
        )

        val distance = mostCompleteSum(
            readIfGranted(DistanceRecord::class, dayRange, granted).map {
                OriginAmount(it.metadata.dataOrigin.packageName, it.distance.inMeters)
            },
        )

        // Syremättnaden mäts framför allt under sömnen, så samma 24-timmarsfönster
        // som sömnen används i stället för dygnet från midnatt.
        val spo2 = readIfGranted(OxygenSaturationRecord::class, nightRange, granted)
            .map { it.percentage.value }
            .takeIf { it.isNotEmpty() }
            ?.average()

        // Blodtryck mäts sporadiskt (Samsung Health Monitor) — visa den senaste
        // mätningen inom en vecka i stället för bara dagens.
        val bloodPressure = readIfGranted(
            BloodPressureRecord::class,
            TimeRangeFilter.between(now.minus(Duration.ofDays(7)), now),
            granted,
        ).maxByOrNull { it.time }?.let {
            BloodPressure(
                systolic = it.systolic.inMillimetersOfMercury.roundToInt(),
                diastolic = it.diastolic.inMillimetersOfMercury.roundToInt(),
            )
        }

        HealthData(
            steps = steps.takeIf { it > 0 },
            heartRateAvg = heartRateAvg,
            sleepDuration = sleepDuration,
            sleepStages = sleepStages,
            exerciseSessions = exercise?.sessions ?: 0,
            exerciseDuration = exercise?.duration,
            activeEnergyKcal = activeEnergy,
            distanceMeters = distance,
            oxygenSaturationAvg = spo2,
            bloodPressure = bloodPressure,
        )
    }

    override suspend fun readWeeklyHealth(): WeeklyHealth = readHealthRange(7)

    override suspend fun readHealthRange(days: Int): WeeklyHealth = withContext(ioDispatcher) {
        val core = readCoreHistory(days)
        WeeklyHealth(
            dailySteps            = core.days.map { DailySteps(it.date, it.steps ?: 0L) },
            dailyRestingHeartRate = core.days.map { DailyRestingHeartRate(it.date, it.restingHeartRate) },
            restingHeartRate      = core.periodRestingHeartRate,
        )
    }

    override suspend fun readHealthHistory(days: Int): HealthHistory = withContext(ioDispatcher) {
        val zone = ZoneId.systemDefault()
        val core = readCoreHistory(days)
        val range = periodRange(days, zone)

        // De valfria typerna (HLS-8) läses bara om behörigheten faktiskt beviljats — en
        // nekad valfri behörighet ska ge en lucka i just det diagrammet, inte ett fel.
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())

        val exercise = mostCompleteExerciseByDay(
            readIfGranted(ExerciseSessionRecord::class, range.filter, granted).map {
                OriginDaySession(
                    origin   = it.metadata.dataOrigin.packageName,
                    time     = it.startTime,
                    duration = Duration.between(it.startTime, it.endTime),
                )
            },
            zone,
        )
        val activeEnergy = mostCompleteSumByDay(
            readIfGranted(ActiveCaloriesBurnedRecord::class, range.filter, granted).map {
                OriginSample(it.metadata.dataOrigin.packageName, it.startTime, it.energy.inKilocalories)
            },
            zone,
        )
        val distance = mostCompleteSumByDay(
            readIfGranted(DistanceRecord::class, range.filter, granted).map {
                OriginSample(it.metadata.dataOrigin.packageName, it.startTime, it.distance.inMeters)
            },
            zone,
        )
        // Syremättnaden mäts framför allt under sömnen; provets egen tidpunkt daterar det,
        // vilket ger samma datum som natten (som dateras efter sitt slut).
        val oxygen = averageByDay(
            readIfGranted(OxygenSaturationRecord::class, range.filter, granted)
                .map { TimedValue(it.time, it.percentage.value) },
            zone,
        )
        // Blodtryck mäts sporadiskt — dygnets senaste mätning, ingen utfyllnad mellan
        // dagarna. Serien blir mest luckor, och det är den korrekta bilden.
        val bloodPressure = readIfGranted(BloodPressureRecord::class, range.filter, granted)
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, day) ->
                day.maxBy { it.time }.let {
                    BloodPressure(
                        systolic  = it.systolic.inMillimetersOfMercury.roundToInt(),
                        diastolic = it.diastolic.inMillimetersOfMercury.roundToInt(),
                    )
                }
            }

        HealthHistory(
            days = core.days.map { day ->
                day.copy(
                    exerciseSessions    = exercise[day.date]?.sessions ?: 0,
                    exerciseDuration    = exercise[day.date]?.duration,
                    activeEnergyKcal    = activeEnergy[day.date],
                    distanceMeters      = distance[day.date],
                    oxygenSaturationAvg = oxygen[day.date],
                    bloodPressure       = bloodPressure[day.date],
                )
            },
        )
    }

    /** Perioden som både ett datumintervall och Health Connects eget filter. */
    private data class PeriodRange(val dates: List<LocalDate>, val filter: TimeRangeFilter)

    private fun periodRange(days: Int, zone: ZoneId): PeriodRange {
        val today = LocalDate.now(zone)
        val dates = ((days - 1).toLong() downTo 0L).map { today.minusDays(it) }
        return PeriodRange(
            dates  = dates,
            filter = TimeRangeFilter.between(dates.first().atStartOfDay(zone).toInstant(), Instant.now()),
        )
    }

    /** Kärnhistoriken plus periodens vilopuls — se [readCoreHistory]. */
    private data class CoreHistory(val days: List<DailyHealth>, val periodRestingHeartRate: Long?)

    /**
     * Kärndatapunkterna (HLS-2) per dygn: steg, dygnssnittspuls, vilopuls, sömnlängd och
     * sömnstadier. Varje posttyp läses **en gång** över hela perioden och fördelas per dygn
     * av rena funktioner — den gamla varianten gjorde en läsning per dag och typ, vilket
     * blev tusentals anrop över ett år (HLS-12).
     *
     * Delas av [readHealthRange] (TRD-11) och [readHealthHistory], så steg och vilopuls
     * aldrig kan skilja sig mellan Trenders diagram och hälsohistoriken.
     */
    private suspend fun readCoreHistory(days: Int): CoreHistory {
        val zone = ZoneId.systemDefault()
        val range = periodRange(days, zone)
        val rangeStart = range.dates.first().atStartOfDay(zone).toInstant()
        val now = Instant.now()

        val steps = mostCompleteSumByDay(
            readAllRecords(StepsRecord::class, range.filter).map {
                OriginSample(it.metadata.dataOrigin.packageName, it.startTime, it.count.toDouble())
            },
            zone,
        )

        val bpm = timedBpmForRange(range.filter)
        val recordedRestingHr = readAllRecords(RestingHeartRateRecord::class, range.filter)
            .map { TimedBpm(it.time, it.beatsPerMinute) }

        // Sömnfönstren för hela perioden läses en gång och används både till nattens
        // längd/stadier och till att sålla bort nattens pulsprover ur vilopulsskattningen
        // (se estimateRestingHeartRate). Startgränsen backas ett dygn så att en session som
        // börjar kvällen före periodens start — eller korsar midnatt inne i perioden —
        // täcker båda dygnen.
        val sessions = readAllRecords(
            SleepSessionRecord::class,
            TimeRangeFilter.between(rangeStart.minus(Duration.ofHours(24)), now),
        )
        val sleepWindows = sessions.map { SleepWindow(it.startTime, it.endTime) }
        val nights = longestNightPerDay(sessions.map { it.toNightSession() }, zone)

        val restingHr = restingHeartRateByDay(recordedRestingHr, bpm, sleepWindows, zone)
        val heartRateAvg = averageBpmByDay(bpm, zone)

        val days = range.dates.map { date ->
            val night = nights[date]
            DailyHealth(
                date             = date,
                steps            = steps[date]?.roundToLong()?.takeIf { it > 0 },
                restingHeartRate = restingHr[date],
                heartRateAvg     = heartRateAvg[date],
                sleepDuration    = night?.duration?.takeIf { !it.isZero },
                sleepStages      = night?.let { summarizeSleepStages(it.stages) } ?: SleepStages(),
            )
        }

        // Senaste vilopuls i perioden (Idag-kortets StatPill) — det senaste registrerade
        // värdet, eller en skattning från periodens pulsprover om posten saknas (fler prover
        // ger en säkrare percentil än en enskild dags).
        val periodRestingHr = recordedRestingHr.maxByOrNull { it.time }?.bpm
            ?: estimateRestingHeartRate(bpm, sleepWindows)

        return CoreHistory(days = days, periodRestingHeartRate = periodRestingHr)
    }

    private fun SleepSessionRecord.toNightSession() = NightSession(
        start  = startTime,
        end    = endTime,
        stages = this.stages.map { StageSlice(it.stage, Duration.between(it.startTime, it.endTime)) },
    )

    override suspend fun readSleepMeasurements(nights: Int): SleepMeasurements? = withContext(ioDispatcher) {
        val now = Instant.now()
        val periodStart = now.minus(Duration.ofDays(nights.toLong()))

        // Startgränsen backas ett dygn så en session som börjar kvällen före perioden
        // ändå kommer med i sin helhet (samma princip som vilopulsskattningen).
        val sessions = readAllRecords(
            SleepSessionRecord::class,
            TimeRangeFilter.between(periodStart.minus(Duration.ofHours(24)), now),
        )
        // Senaste natten: den sömnsession som slutade senast inom det senaste dygnet.
        val lastNight = sessions
            .filter { it.endTime.isAfter(now.minus(Duration.ofHours(24))) }
            .maxByOrNull { it.endTime }
            ?: return@withContext null

        val stages = lastNight.stages.map { StageSlice(it.stage, Duration.between(it.startTime, it.endTime)) }
        val summary = summarizeSleepStages(stages)

        val zone = ZoneId.systemDefault()
        val midpointSd = sleepMidpointSdMinutes(
            nightlyMidpoints(
                sessions
                    .filter { !it.endTime.isBefore(periodStart) }
                    .map { SleepWindow(it.startTime, it.endTime) },
                zone,
            ),
        )

        // Ett svep över periodens pulsprover räcker till båda måtten: nattens sovpuls
        // och den vakna baslinjen som den jämförs mot.
        val sleepWindows = sessions.map { SleepWindow(it.startTime, it.endTime) }
        val samples = timedBpmForRange(TimeRangeFilter.between(periodStart, now))
        val lastNightWindow = SleepWindow(lastNight.startTime, lastNight.endTime)
        val sleepingHr = samples
            .filter { lastNightWindow.contains(it.time) }
            .map { it.bpm }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToLong()
        val baselineHr = estimateRestingHeartRate(samples, sleepWindows)

        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        val spo2 = readIfGranted(
            OxygenSaturationRecord::class,
            TimeRangeFilter.between(lastNight.startTime, lastNight.endTime),
            granted,
        ).map { it.percentage.value }
            .takeIf { it.isNotEmpty() }
            ?.average()

        SleepMeasurements(
            timeInBed = Duration.between(lastNight.startTime, lastNight.endTime),
            awake = summary.awake,
            deep = summary.deep,
            rem = summary.rem,
            midpointSdMinutes = midpointSd,
            meanOxygenSaturation = spo2,
            sleepingHeartRate = sleepingHr,
            baselineRestingHeartRate = baselineHr,
        )
    }

    override suspend fun readSleepMeasurementsHistory(nights: Int): List<NightlySleepMeasurements> =
        withContext(ioDispatcher) {
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val periodStart = now.minus(Duration.ofDays(nights.toLong()))

            // Startgränsen backas ett dygn så en session som börjar kvällen före perioden
            // ändå kommer med i sin helhet (samma princip som vilopulsskattningen).
            val sessions = readAllRecords(
                SleepSessionRecord::class,
                TimeRangeFilter.between(periodStart.minus(Duration.ofHours(24)), now),
            )
            val nightsByDay = longestNightPerDay(sessions.map { it.toNightSession() }, zone)
                .filterValues { !it.end.isBefore(periodStart) }
            if (nightsByDay.isEmpty()) return@withContext emptyList()

            val ordered = nightsByDay.entries.sortedBy { it.key }
            // Regelbundenheten är rullande (HLS-13): varje natt bär spridningen över de
            // nätter som slutar med den, inte ett gemensamt värde för hela perioden.
            val rollingSd = rollingMidpointSdMinutes(
                ordered.map { midpointOf(SleepWindow(it.value.start, it.value.end), zone) },
                DEFAULT_SLEEP_QUALITY_NIGHTS,
            )

            // Ett svep över periodens pulsprover räcker till både varje natts sovpuls och
            // den gemensamma vakna baslinjen som de jämförs mot.
            val sleepWindows = sessions.map { SleepWindow(it.startTime, it.endTime) }
            val samples = timedBpmForRange(TimeRangeFilter.between(periodStart, now))
            val baselineHr = estimateRestingHeartRate(samples, sleepWindows)

            val granted = runCatching { client.permissionController.getGrantedPermissions() }
                .getOrDefault(emptySet())
            val spo2 = readIfGranted(
                OxygenSaturationRecord::class,
                TimeRangeFilter.between(periodStart, now),
                granted,
            ).map { TimedValue(it.time, it.percentage.value) }

            ordered.mapIndexed { index, (date, night) ->
                val window = SleepWindow(night.start, night.end)
                val summary = summarizeSleepStages(night.stages)
                NightlySleepMeasurements(
                    date = date,
                    measurements = SleepMeasurements(
                        timeInBed = Duration.between(night.start, night.end),
                        awake = summary.awake,
                        deep = summary.deep,
                        rem = summary.rem,
                        midpointSdMinutes = rollingSd[index],
                        meanOxygenSaturation = spo2
                            .filter { window.contains(it.time) }
                            .map { it.value }
                            .takeIf { it.isNotEmpty() }
                            ?.average(),
                        sleepingHeartRate = samples
                            .filter { window.contains(it.time) }
                            .map { it.bpm }
                            .takeIf { it.isNotEmpty() }
                            ?.average()
                            ?.roundToLong(),
                        baselineRestingHeartRate = baselineHr,
                    ),
                )
            }
        }
}

/**
 * En sömnmittpunkt per natt, som klockslag. Flera sessioner samma natt (t.ex. en
 * avbruten sömn som Samsung delar i två) skulle annars räknas som två olika
 * läggtider och blåsa upp spridningen — därför väljs den längsta sessionen per natt.
 * Natten dateras efter sessionens **slut**, så en session som korsar midnatt hamnar
 * på morgonens datum i stället för att bli en egen natt.
 *
 * Ren funktion (inga SDK-beroenden) för enhetstestning (regel 2).
 */
internal fun nightlyMidpoints(windows: List<SleepWindow>, zone: ZoneId): List<LocalTime> =
    windows
        .groupBy { it.end.atZone(zone).toLocalDate() }
        .values
        .mapNotNull { night -> night.maxByOrNull { Duration.between(it.start, it.end) } }
        .map { midpointOf(it, zone) }

/** Sömnfönstrets mittpunkt som klockslag — delas av regelbundenhetsmåtten (HLS-10/HLS-13). */
internal fun midpointOf(window: SleepWindow, zone: ZoneId): LocalTime =
    window.start
        .plus(Duration.between(window.start, window.end).dividedBy(2))
        .atZone(zone)
        .toLocalTime()

/** Ett tidsstämplat mätvärde utan källa (syremättnad, blodtryck). */
internal data class TimedValue(val time: Instant, val value: Double)

/** En tidsstämplad mängd knuten till sin källa — underlag för dygnsfördelningen (HLS-12). */
internal data class OriginSample(val origin: String, val time: Instant, val amount: Double)

/** Ett träningspass knutet till sin källa och sin tidpunkt. */
internal data class OriginDaySession(val origin: String, val time: Instant, val duration: Duration)

/**
 * En sömnsession reducerad till det historiken behöver, utan SDK-typer (regel 2).
 */
internal data class NightSession(val start: Instant, val end: Instant, val stages: List<StageSlice>) {
    val duration: Duration get() = Duration.between(start, end)
}

/**
 * Fördelar [samples] per dygn efter postens **starttid** och väljer den mest kompletta
 * källan för varje dygn — samma per-källa-princip som [mostCompleteSum] (HLS-2/HLS-8),
 * fast tillämpad dag för dag. Dygn utan poster saknas i kartan i stället för att bli 0.
 *
 * Ren funktion (inga SDK-beroenden) för enhetstestning (regel 2).
 */
internal fun mostCompleteSumByDay(samples: List<OriginSample>, zone: ZoneId): Map<LocalDate, Double> =
    samples
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .mapNotNull { (date, day) ->
            mostCompleteSum(day.map { OriginAmount(it.origin, it.amount) })?.let { date to it }
        }
        .toMap()

/**
 * Som [mostCompleteSumByDay], fast för träningspass: den källa som har längst sammanlagd
 * passtid det dygnet vinner, så ett pass som både telefonen och klockan skrivit inte
 * räknas två gånger (HLS-8).
 *
 * Ren funktion (inga SDK-beroenden) för enhetstestning (regel 2).
 */
internal fun mostCompleteExerciseByDay(
    sessions: List<OriginDaySession>,
    zone: ZoneId,
): Map<LocalDate, ExerciseTotals> =
    sessions
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .mapNotNull { (date, day) ->
            mostCompleteExercise(day.map { OriginSession(it.origin, it.duration) })?.let { date to it }
        }
        .toMap()

/** Dygnets medelvärde av ett tidsstämplat mätvärde (t.ex. syremättnad). */
internal fun averageByDay(values: List<TimedValue>, zone: ZoneId): Map<LocalDate, Double> =
    values
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .mapValues { (_, day) -> day.map { it.value }.average() }

/** Dygnets snittpuls, avrundad till heltal (HLS-12). */
internal fun averageBpmByDay(samples: List<TimedBpm>, zone: ZoneId): Map<LocalDate, Long> =
    samples
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .mapValues { (_, day) -> day.map { it.bpm }.average().roundToLong() }

/**
 * Vilopuls per dygn (HLS-7, per dag): dygnets senast registrerade [RestingHeartRateRecord]
 * i första hand, annars en skattning från dygnets egna pulsprover. Samma fallback-princip
 * som periodvärdet, fast per dag — grövre med få prover, men tillräckligt för en trendlinje.
 *
 * Ren funktion (inga SDK-beroenden) för enhetstestning (regel 2).
 */
internal fun restingHeartRateByDay(
    recorded: List<TimedBpm>,
    samples: List<TimedBpm>,
    sleepWindows: List<SleepWindow>,
    zone: ZoneId,
): Map<LocalDate, Long> {
    val estimated = samples
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .mapNotNull { (date, day) -> estimateRestingHeartRate(day, sleepWindows)?.let { date to it } }
        .toMap()
    val measured = recorded
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .mapValues { (_, day) -> day.maxBy { it.time }.bpm }
    // Ett registrerat värde slår alltid skattningen för samma dygn.
    return estimated + measured
}

/**
 * En natt per dygn, daterad efter sessionens **slut** — en session som korsar midnatt hör
 * till morgonens datum, samma regel som [nightlyMidpoints]. Flera sessioner samma natt
 * (Samsung delar ibland en avbruten sömn i två) reduceras till den längsta, så natten inte
 * räknas som två kortare.
 *
 * Ren funktion (inga SDK-beroenden) för enhetstestning (regel 2).
 */
internal fun longestNightPerDay(sessions: List<NightSession>, zone: ZoneId): Map<LocalDate, NightSession> =
    sessions
        .groupBy { it.end.atZone(zone).toLocalDate() }
        .mapValues { (_, night) -> night.maxBy { it.duration } }

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
    mostCompleteSum(records.map { OriginAmount(it.origin, it.count.toDouble()) })
        ?.roundToLong()
        ?: 0L

/** Ett mätvärde knutet till sin källa (dataOrigin-paketnamn). */
internal data class OriginAmount(val origin: String, val amount: Double)

/**
 * Samma per-källa-princip som [mostCompleteStepSum], generaliserad till de övriga
 * summerbara måtten i HLS-8 (aktiva kalorier, sträcka): telefonen och Galaxy Watch
 * skriver båda dagsvärden till Health Connect, och en summering över källor skulle
 * dubbelräkna. Returnerar null om inga poster finns.
 *
 * Ren funktion (inga SDK-beroenden) för enhetstestning (regel 2).
 */
internal fun mostCompleteSum(records: List<OriginAmount>): Double? =
    records.groupBy { it.origin }
        .values
        .maxOfOrNull { origin -> origin.sumOf(OriginAmount::amount) }

/** Ett träningspass knutet till sin källa. */
internal data class OriginSession(val origin: String, val duration: Duration)

/** Antal träningspass och deras sammanlagda längd för dagen (HLS-8). */
internal data class ExerciseTotals(val sessions: Int, val duration: Duration)

/**
 * Väljer den mest kompletta källans träningspass, av samma skäl som
 * [mostCompleteStepSum]: samma pass kan skrivas både av telefonen och av klockan via
 * Samsung Health, och en sammanslagning skulle räkna det två gånger. Källan med längst
 * sammanlagd passtid vinner. Returnerar null om inga pass finns.
 *
 * Ren funktion (inga SDK-beroenden) för enhetstestning (regel 2).
 */
internal fun mostCompleteExercise(sessions: List<OriginSession>): ExerciseTotals? =
    sessions.groupBy { it.origin }
        .values
        .map { origin ->
            ExerciseTotals(
                sessions = origin.size,
                duration = origin.fold(Duration.ZERO) { acc, s -> acc.plus(s.duration) },
            )
        }
        .maxByOrNull { it.duration }

/** Ett sömnstadium med sin längd, hämtat ur en `SleepSessionRecord.Stage`. */
internal data class StageSlice(val stage: Int, val duration: Duration)

/**
 * Summerar nattens sömnstadier per kategori (HLS-8). Health Connect har fler
 * stadiekoder än de fyra vi visar: `AWAKE_IN_BED` och `OUT_OF_BED` räknas som vaken
 * tid, medan `SLEEPING` (ospecificerad sömn) räknas som lätt sömn — Samsung skriver
 * den när stadieindelningen saknas, och att tappa den skulle få nätter att se
 * tommare ut än de är. `UNKNOWN` ignoreras.
 *
 * En kategori utan tid blir null i stället för `Duration.ZERO` så att UI visar "—"
 * i stället för "0 min" när stadier saknas helt.
 *
 * Ren funktion (inga SDK-beroenden utöver stadiekonstanterna) för enhetstestning (regel 2).
 */
internal fun summarizeSleepStages(slices: List<StageSlice>): SleepStages {
    fun sum(vararg stages: Int): Duration? = slices
        .filter { it.stage in stages }
        .fold(Duration.ZERO) { acc, slice -> acc.plus(slice.duration) }
        .takeIf { !it.isZero && !it.isNegative }

    return SleepStages(
        deep = sum(SleepSessionRecord.STAGE_TYPE_DEEP),
        rem = sum(SleepSessionRecord.STAGE_TYPE_REM),
        light = sum(SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING),
        awake = sum(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        ),
    )
}

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
