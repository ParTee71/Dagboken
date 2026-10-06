package se.partee71.dagboken.data.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.HealthHistory

/**
 * Klockan i test: [status] styrs av testet, [measured] är dygnen med mätning (övriga blir luckor) och
 * [failure] gör varje läsning till ett fel. Läsningarna räknas ([reads]), så att ett test kan visa att ett
 * stängt kort inte läser (TRD-15).
 */
class FakeHealthRepository(
    status: HealthStatus = HealthStatus.AVAILABLE,
    val measured: MutableMap<LocalDate, DailyHealth> = mutableMapOf(),
    var failure: Throwable? = null,
) : HealthRepository {
    override val status: MutableStateFlow<HealthStatus> = MutableStateFlow(status)

    val reads = mutableListOf<ClosedRange<LocalDate>>()

    override suspend fun history(from: LocalDate, to: LocalDate): Result<HealthHistory> {
        reads += from..to
        failure?.let { return Result.failure(it) }
        return Result.success(HealthHistory.of(from, to, measured))
    }

    override suspend fun day(date: LocalDate): Result<DailyHealth> {
        reads += date..date
        failure?.let { return Result.failure(it) }
        return Result.success(measured[date] ?: DailyHealth(date))
    }
}
