package se.partee71.dagboken.domain.usecase

import se.partee71.dagboken.domain.model.Medicin
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class CheckCooldownUseCase @Inject constructor() {

    /**
     * Returns remaining cooldown in hours (fractional), or null if no cooldown active.
     * Ports checkMedicinCooldown() from src/storage/mediciner.ts.
     */
    fun remainingHours(
        namn: String,
        minTidMellan: Int,
        lastTaken: Medicin?,
    ): Double? {
        if (minTidMellan <= 0 || lastTaken == null) return null
        return try {
            val lastTime = Instant.parse(lastTaken.timestamp)
            val now      = Instant.now()
            val elapsed  = ChronoUnit.SECONDS.between(lastTime, now) / 3600.0
            val remaining = minTidMellan - elapsed
            if (remaining > 0) remaining else null
        } catch (_: Exception) {
            null
        }
    }
}

class CheckDailyLimitUseCase @Inject constructor() {

    /**
     * Returns true if the daily dose limit has been reached.
     * Ports checkDailyLimit() from src/stores/medicinerStore.ts.
     */
    fun limitReached(maxDoserPerDag: Int, takenToday: Int): Boolean =
        maxDoserPerDag > 0 && takenToday >= maxDoserPerDag
}
