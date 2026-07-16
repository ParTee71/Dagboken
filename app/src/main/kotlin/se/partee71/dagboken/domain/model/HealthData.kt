package se.partee71.dagboken.domain.model

import java.time.Duration

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
