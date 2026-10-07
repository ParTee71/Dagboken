package se.partee71.dagboken.data.health

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.data.common.withFallback

/**
 * Klockans läge (HLS-3, HLS-4, HLS-14): vad Trender → Klocka ska visa i stället för – eller ovanför – datan.
 */
enum class HealthStatus {
    /** Health Connect saknas på enheten eller stöds inte (HLS-4) – också när porten inte byggts än. */
    UNAVAILABLE,

    /** Health Connect finns men behöver uppdateras (HLS-4). */
    UPDATE_REQUIRED,

    /** Health Connect finns men kärnbehörigheterna (HLS-3) saknas. */
    PERMISSIONS_MISSING,

    AVAILABLE,
}

/**
 * Klockdatan (§19 HLS), **enbart läsning**: Dagboken persisterar aldrig hälsodata – varken i Firestore
 * eller i exporten (HLS-5); Health Connect äger och backar upp den, appen läser live och räknar om vid varje
 * visning. Gränssnittet är kontraktet för Trender → Klocka (#267), Hälsa idag (#241) och porten av Health
 * Connect (#243); tills porten finns är [UnavailableHealthRepository] den enda implementationen.
 *
 * Ingen funktion här skriver något – `HealthRepositoryTest` bevisar att paketet inte rör datalagret.
 */
interface HealthRepository {
    /** Klockans läge, igen när det ändras (behörighet given, Health Connect installerat). */
    val status: Flow<HealthStatus>

    /**
     * Dagshistoriken för [from]…[to] (HLS-12): **ett** [DailyHealth] per dygn i datumordning, tomt för ett
     * dygn utan mätning – en lucka, aldrig en nolla. Varje posttyp läses en gång över hela perioden. Utan
     * Health Connect eller behörighet är alla dygn tomma; ett läsfel är `Result.failure`.
     */
    suspend fun history(from: LocalDate, to: LocalDate): Result<HealthHistory>

    /** Dygnet [date] (Hälsa idag, HLS-6, HLS-8): samma mått som historiken, tomt utan mätning. */
    suspend fun day(date: LocalDate): Result<DailyHealth>
}

/**
 * Klockans läge som skärmarna följer det (HLS-4, TRD-20, HEM-15): ett fel i statusflödet lämnar läget okänt
 * (`null`) i stället för att fälla skärmen, och flödet försöker igen (`withFallback`). En gång för Trender,
 * Klocka-gruppens topp och hälsokortet på Idag.
 */
fun HealthRepository.observedStatus(): Flow<HealthStatus?> =
    status.map<HealthStatus, HealthStatus?> { it }.withFallback(null).distinctUntilChanged()

/**
 * Standardbindningen tills Health Connect portats (#243): klockan är [HealthStatus.UNAVAILABLE], varje dygn är
 * en lucka. Ingenting läses, ingenting skrivs.
 */
class UnavailableHealthRepository @Inject constructor() : HealthRepository {
    override val status: Flow<HealthStatus> = flowOf(HealthStatus.UNAVAILABLE)

    override suspend fun history(from: LocalDate, to: LocalDate): Result<HealthHistory> = Result.success(HealthHistory.empty(from, to))

    override suspend fun day(date: LocalDate): Result<DailyHealth> = Result.success(DailyHealth(date))
}
