package se.partee71.dagboken.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import se.partee71.dagboken.core.engine.health.CaloriesSample
import se.partee71.dagboken.core.engine.health.DistanceSample
import se.partee71.dagboken.core.engine.health.ExerciseSession
import se.partee71.dagboken.core.engine.health.HealthRecords
import se.partee71.dagboken.core.engine.health.HeartRateSample
import se.partee71.dagboken.core.engine.health.HealthReadWindows
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric
import se.partee71.dagboken.core.engine.health.OxygenSample
import se.partee71.dagboken.core.engine.health.RestingHeartRateSample
import se.partee71.dagboken.core.engine.health.SleepSession
import se.partee71.dagboken.core.engine.health.SleepStageSlice
import se.partee71.dagboken.core.engine.health.SleepStageType
import se.partee71.dagboken.core.engine.health.StepSample
import se.partee71.dagboken.core.engine.health.TimeSpan
import se.partee71.dagboken.di.IoDispatcher

/** Health Connects läge på enheten (HLS-4), ur `HealthConnectClient.getSdkStatus`. */
enum class HealthSdkStatus { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

/**
 * Behörighetsuppsättningen (HLS-3, HLS-8, HLS-9, HLS-14) – det enda stället behörighetsnamnen står, så att manifestet
 * (`PrivacyManifestTest`), begäran och läget inte kan glida isär. Vad som faktiskt begärs på en enhet avgör
 * [HealthAccess.requestable] (historiken bara där den stöds). **Bara läsbehörigheter**, och inget blodtryck
 * (borttaget i 4.0).
 */
object HealthPermissionSet {
    /** Kärnan (HLS-3): utan dem visar klockan inget. */
    val CORE: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    /** De valfria måtten (HLS-8, HLS-9): en nekad behörighet ger bara en lucka i sitt mått. */
    val OPTIONAL: Map<OptionalHealthMetric, String> = mapOf(
        OptionalHealthMetric.EXERCISE to HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        OptionalHealthMetric.ACTIVE_ENERGY to HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        OptionalHealthMetric.DISTANCE to HealthPermission.getReadPermission(DistanceRecord::class),
        OptionalHealthMetric.OXYGEN_SATURATION to HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        OptionalHealthMetric.HISTORY to HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
    )

    /** Hela uppsättningen, som manifestet deklarerar (HLS-14). */
    val ALL: Set<String> = CORE + OPTIONAL.values
}

/**
 * Den tunna källan mot Health Connect (§19 HLS): läser rådataposter och översätter dem till `:core`-typerna i
 * [HealthRecords] – all logik (per källa, dygn, vilopuls, sömn) ligger i `:core/engine/health`. Gränssnitt, så att
 * `HealthConnectRepository` testas i JVM mot en fejk. Ingenting här sparas eller loggas (HLS-5, NFR-13).
 */
interface HealthConnectSource {
    /** Health Connects läge på enheten (HLS-4). */
    suspend fun sdkStatus(): HealthSdkStatus

    /** De beviljade behörigheterna; kastar när Health Connect inte svarar. */
    suspend fun grantedPermissions(): Set<String>

    /**
     * Om Health Connect på enheten kan lämna ut historik bortom 30 dagar (`FEATURE_READ_HEALTH_DATA_HISTORY`, HLS-9) –
     * annars finns behörigheten inte att ge, och den begärs inte och räknas inte som saknad.
     */
    suspend fun historySupported(): Boolean

    /**
     * Periodens poster, varje posttyp läst **en gång** över sitt fönster i [windows] (`healthReadWindows`, HLS-12):
     * puls och vilopuls över perioden, de summerbara typerna och syremättnaden från ett dygn före, sömnen från
     * regelbundenhetsfönstret före (HLS-7, HLS-13). En typ vars behörighet saknas i [granted] läses inte och blir en
     * tom lista (HLS-8).
     */
    suspend fun read(windows: HealthReadWindows, granted: Set<String>): HealthRecords
}

/** [HealthConnectSource] mot den riktiga `HealthConnectClient`, på den injicerade IO-dispatchern (NFR-8). */
@Singleton
class HealthConnectClientSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : HealthConnectSource {

    // getOrCreate kastar när Health Connect saknas – skapas först när sdkStatus sagt AVAILABLE.
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun sdkStatus(): HealthSdkStatus = withContext(io) {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthSdkStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthSdkStatus.UPDATE_REQUIRED
            else -> HealthSdkStatus.UNAVAILABLE
        }
    }

    override suspend fun grantedPermissions(): Set<String> = withContext(io) { client.permissionController.getGrantedPermissions() }

    override suspend fun historySupported(): Boolean = withContext(io) {
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    override suspend fun read(windows: HealthReadWindows, granted: Set<String>): HealthRecords = withContext(io) {
        val day = windows.samples.filter()
        val lead = windows.lead.filter()
        val sleepRange = windows.sleep.filter()

        suspend fun <T : Record> read(type: KClass<T>, range: TimeRangeFilter): List<T> =
            if (HealthPermission.getReadPermission(type) in granted) {
                readPages { token -> client.readRecords(ReadRecordsRequest(type, range, pageToken = token)).let { it.records to it.pageToken } }
            } else {
                emptyList()
            }

        HealthRecords(
            steps = read(StepsRecord::class, lead).map { StepSample(it.origin, it.startTime.toKotlinInstant(), it.endTime.toKotlinInstant(), it.count) },
            heartRate = read(HeartRateRecord::class, day).flatMap(::heartRateSamples),
            restingHeartRate = read(RestingHeartRateRecord::class, day).map { RestingHeartRateSample(it.origin, it.time.toKotlinInstant(), it.beatsPerMinute) },
            sleep = read(SleepSessionRecord::class, sleepRange).map(::sleepSession),
            exercise = read(ExerciseSessionRecord::class, lead).map { ExerciseSession(it.origin, it.startTime.toKotlinInstant(), it.endTime.toKotlinInstant()) },
            calories = read(ActiveCaloriesBurnedRecord::class, lead).map {
                CaloriesSample(it.origin, it.startTime.toKotlinInstant(), it.endTime.toKotlinInstant(), it.energy.inKilocalories)
            },
            distance = read(DistanceRecord::class, lead).map {
                DistanceSample(it.origin, it.startTime.toKotlinInstant(), it.endTime.toKotlinInstant(), it.distance.inMeters)
            },
            oxygen = read(OxygenSaturationRecord::class, lead).map { OxygenSample(it.origin, it.time.toKotlinInstant(), it.percentage.value) },
        )
    }
}

/**
 * Alla sidor ur en sidvis läsning (`readRecords` lämnar 1000 poster åt gången): utan att följa `pageToken` tappades
 * allt efter första sidan tyst – hårdast för pulsproven över ett år (3.x, TRD-11). [page] får förra sidans token
 * (`null` först) och ger postern och nästa token, `null` efter sista sidan.
 */
internal suspend fun <T> readPages(page: suspend (token: String?) -> Pair<List<T>, String?>): List<T> {
    val all = mutableListOf<T>()
    var token: String? = null
    do {
        val (records, next) = page(token)
        all += records
        token = next?.takeIf { it.isNotEmpty() }
    } while (token != null)
    return all
}

private fun TimeSpan.filter(): TimeRangeFilter = TimeRangeFilter.between(start.toJavaInstant(), end.toJavaInstant())

/** Källan som skrev posten (`dataOrigin`) – grunden för per-källa-valet (HLS-2). */
private val Record.origin: String get() = metadata.dataOrigin.packageName

/** En `HeartRateRecord` bär många prover; varje prov blir ett eget [HeartRateSample] (HLS-2, HLS-7). */
internal fun heartRateSamples(record: HeartRateRecord): List<HeartRateSample> =
    record.samples.map { HeartRateSample(record.origin, it.time.toKotlinInstant(), it.beatsPerMinute) }

/** En sömnsession med sina stadier (HLS-8). */
internal fun sleepSession(record: SleepSessionRecord): SleepSession = SleepSession(
    origin = record.origin,
    start = record.startTime.toKotlinInstant(),
    end = record.endTime.toKotlinInstant(),
    stages = record.stages.map { SleepStageSlice(sleepStageType(it.stage), it.startTime.toKotlinInstant(), it.endTime.toKotlinInstant()) },
)

/** Health Connects `STAGE_TYPE_*` som domänbegrepp; en okänd kod (en nyare SDK) är [SleepStageType.UNKNOWN] och ignoreras (HLS-8). */
internal fun sleepStageType(stage: Int): SleepStageType = when (stage) {
    SleepSessionRecord.STAGE_TYPE_AWAKE -> SleepStageType.AWAKE
    SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStageType.SLEEPING
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStageType.OUT_OF_BED
    SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.LIGHT
    SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
    SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStageType.AWAKE_IN_BED
    else -> SleepStageType.UNKNOWN
}
