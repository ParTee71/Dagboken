package se.partee71.dagboken.data.health

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric
import se.partee71.dagboken.core.engine.health.healthDay
import se.partee71.dagboken.core.engine.health.healthHistory
import se.partee71.dagboken.core.engine.health.healthReadWindows
import se.partee71.dagboken.core.engine.health.missingMetrics
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.di.ApplicationScope

/**
 * Health Connects läge på enheten vid ett tillfälle (HLS-3, HLS-4, HLS-9, HLS-14): SDK-status, de beviljade
 * behörigheterna och om historikbehörigheten finns att ge. Allt som läget, raden för saknade mått och läsningarna
 * behöver – härlett här en gång.
 */
data class HealthAccess(
    val sdk: HealthSdkStatus,
    val granted: Set<String> = emptySet(),
    val historySupported: Boolean = false,
) {
    /** De valfria måtten som går att ge på enheten – historiken bara där Health Connect stöder den (HLS-9). */
    val optional: Map<OptionalHealthMetric, String> =
        if (historySupported) HealthPermissionSet.OPTIONAL else HealthPermissionSet.OPTIONAL - OptionalHealthMetric.HISTORY

    /** Det samtyckesdialogen begär (HLS-14). */
    val requestable: Set<String> = HealthPermissionSet.CORE + optional.values

    /** Klockans läge (HLS-3, HLS-4). */
    val status: HealthStatus = when (sdk) {
        HealthSdkStatus.UNAVAILABLE -> HealthStatus.UNAVAILABLE
        HealthSdkStatus.UPDATE_REQUIRED -> HealthStatus.UPDATE_REQUIRED
        HealthSdkStatus.AVAILABLE -> if (granted.containsAll(HealthPermissionSet.CORE)) HealthStatus.AVAILABLE else HealthStatus.PERMISSIONS_MISSING
    }

    /** De valfria mått som saknar åtkomst (HLS-14); `null` utan Health Connect – då vet vi inget. */
    val missingOptional: Set<OptionalHealthMetric>? = if (sdk == HealthSdkStatus.AVAILABLE) missingMetrics(granted, optional) else null
}

/**
 * Health Connects läge, läst **en gång per ändring** och delat av läget, raden för saknade mått och varje läsning (IPC
 * mot Health Connect är dyrt): [changed] anropas efter samtyckesdialogen och när appen kommer tillbaka i förgrunden,
 * och då läses SDK-status, behörigheterna och historikstödet om – ett par anrop per återupptagning, inte ett per
 * lyssnare. Ett fel är ett [DataError] i [current], aldrig ett fel i flödet.
 */
@Singleton
class HealthConnectAccess @Inject constructor(
    private val source: HealthConnectSource,
    @ApplicationScope scope: CoroutineScope,
) {
    private val count = MutableStateFlow(0)

    /** Det senast lästa läget – för samtyckesdialogen, som måste startas direkt; `null` innan något lästs. */
    @Volatile
    var latest: HealthAccess? = null
        private set

    /** Läget med ändringen det lästes efter, så att [now] kan vänta ut en läsning som ännu inte gjorts om. */
    private val versioned: SharedFlow<Pair<Int, Result<HealthAccess>>> =
        count.map { version -> version to read() }.shareIn(scope, SharingStarted.Lazily, replay = 1)

    /** Läget, igen efter varje ändring. */
    val current: Flow<Result<HealthAccess>> = versioned.map { it.second }

    /** Läget efter den senaste ändringen – inte ett som lästes före den. */
    suspend fun now(): Result<HealthAccess> {
        val version = count.value
        return versioned.first { it.first >= version }.second
    }

    /** Läget kan ha ändrats – läs om det. */
    fun changed() = count.update { it + 1 }

    private suspend fun read(): Result<HealthAccess> = suspendRunCatching(::healthError) {
        when (val sdk = source.sdkStatus()) {
            HealthSdkStatus.AVAILABLE -> HealthAccess(sdk, source.grantedPermissions(), source.historySupported())
            else -> HealthAccess(sdk)
        }
    }.onSuccess { latest = it }
}

/**
 * Klockdatan ur Health Connect (§19 HLS), **enbart läsning** (HLS-5): [HealthConnectSource] läser posterna och
 * `:core/engine/health` gör dygnen av dem – dagens värden med `healthDay`, historiken med `healthHistory`. Läget och
 * behörigheterna kommer ur det delade [HealthConnectAccess]; de valfria behörigheterna når skärmen via
 * [HealthPermissions]. Fel mappas här till [DataError], och inget mätvärde loggas eller sparas.
 */
class HealthConnectRepository @Inject constructor(
    private val source: HealthConnectSource,
    private val access: HealthConnectAccess,
    private val clock: Clock,
    private val zone: Provider<TimeZone>,
) : HealthRepository {

    /**
     * Läget ur [HealthConnectAccess]. Nekar Health Connect att ens lämna ut behörigheterna (`SecurityException`) är det
     * ett behörighetsläge, inte ett okänt; övriga fel lämnar läget okänt (`observedStatus` → `null`).
     */
    override val status: Flow<HealthStatus> = access.current.map { result ->
        result.fold(
            onSuccess = { it.status },
            onFailure = { error -> if (error == DataError.PermissionDenied) HealthStatus.PERMISSIONS_MISSING else throw error },
        )
    }

    override suspend fun history(from: LocalDate, to: LocalDate): Result<HealthHistory> = suspendRunCatching(::healthError) {
        val zone = zone.get()
        read(from, to, zone)?.let { healthHistory(it, from, to, zone) } ?: HealthHistory.empty(from, to)
    }

    override suspend fun day(date: LocalDate): Result<DailyHealth> = suspendRunCatching(::healthError) {
        val zone = zone.get()
        read(date, date, zone)?.let { healthDay(it, date, clock.now(), zone) } ?: DailyHealth(date)
    }

    /**
     * Posterna för dygnen [from]…[to] fram till nu, över fönstren i `healthReadWindows`, eller `null` – alla dygn
     * luckor – när Health Connect saknas, kärnbehörigheterna inte är givna eller perioden ligger i framtiden (HLS-3,
     * HLS-4, HLS-12).
     */
    private suspend fun read(from: LocalDate, to: LocalDate, zone: TimeZone) = run {
        val access = access.now().getOrThrow()
        if (access.status != HealthStatus.AVAILABLE) return@run null
        val start = from.atStartOfDayIn(zone)
        val end = minOf(to.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone), clock.now())
        if (end <= start) null else source.read(healthReadWindows(start, end), access.granted)
    }
}

/**
 * Health Connects fel som [DataError] – en gång för läget och läsningarna. Nekad behörighet (t.ex. återkallad mitt i
 * en läsning) är [DataError.PermissionDenied], allt annat [DataError.Unknown]. Felets text följer aldrig med: den kan
 * nämna en post (HLS-5, NFR-13).
 */
internal fun healthError(error: Throwable): DataError = when (error) {
    is SecurityException -> DataError.PermissionDenied
    else -> DataError.Unknown
}
