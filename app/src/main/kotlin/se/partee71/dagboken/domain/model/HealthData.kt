package se.partee71.dagboken.domain.model

import java.time.Duration
import java.time.LocalDate

/**
 * Dagens hälsodata läst från Health Connect (epic #54, §19 HLS).
 * Samtliga fält är nullbara — en datapunkt kan saknas om användaren inte har
 * någon inloggad källa (t.ex. Galaxy Watch) som skrivit den till Health Connect.
 */
data class HealthData(
    val steps: Long? = null,
    val heartRateAvg: Long? = null,
    val sleepDuration: Duration? = null,
) {
    val isEmpty: Boolean get() = steps == null && heartRateAvg == null && sleepDuration == null
}

/** Stegsumma för en enskild dag (HLS-7). */
data class DailySteps(val date: LocalDate, val steps: Long)

/** Vilopuls (bpm) för en enskild dag (HLS-7); null om varken uppmätt eller skattningsbar. */
data class DailyRestingHeartRate(val date: LocalDate, val bpm: Long?)

/**
 * Veckoöversikt för Idag-kortet (HLS-7): stegtrend och vilopulstrend för senaste
 * 7 dagarna, läst live från Health Connect.
 */
data class WeeklyHealth(
    val dailySteps: List<DailySteps> = emptyList(),                        // äldst -> nyast
    val dailyRestingHeartRate: List<DailyRestingHeartRate> = emptyList(),  // äldst -> nyast
    val restingHeartRate: Long? = null,                                    // senaste vilopulsen (bpm)
) {
    /** Senaste dagens (idag) steg, eller null om noll/saknas. */
    val stepsToday: Long? get() = dailySteps.lastOrNull()?.steps?.takeIf { it > 0 }

    /** Sparkline kräver minst 2 dagar med data (HEM-7-mönstret). */
    val hasStepTrend: Boolean get() = dailySteps.count { it.steps > 0 } >= 2

    /** Sparkline kräver minst 2 dagar med vilopulsvärde. */
    val hasRestingHeartRateTrend: Boolean get() = dailyRestingHeartRate.count { it.bpm != null } >= 2

    val hasAnyData: Boolean get() = dailySteps.any { it.steps > 0 } || restingHeartRate != null
}
