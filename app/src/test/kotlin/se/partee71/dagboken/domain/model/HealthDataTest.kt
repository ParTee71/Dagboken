package se.partee71.dagboken.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Enhetstest för [WeeklyHealth]s beräknade fält (HLS-7, §19). */
class HealthDataTest {

    private val today = LocalDate.now()

    @Test fun `hasRestingHeartRateTrend is false with fewer than two known values`() {
        val weekly = WeeklyHealth(
            dailyRestingHeartRate = listOf(
                DailyRestingHeartRate(today.minusDays(1), null),
                DailyRestingHeartRate(today, 58),
            ),
        )
        assertFalse(weekly.hasRestingHeartRateTrend)
    }

    @Test fun `hasRestingHeartRateTrend is true with two or more known values`() {
        val weekly = WeeklyHealth(
            dailyRestingHeartRate = listOf(
                DailyRestingHeartRate(today.minusDays(1), 60),
                DailyRestingHeartRate(today, 58),
            ),
        )
        assertTrue(weekly.hasRestingHeartRateTrend)
    }

    @Test fun `hasRestingHeartRateTrend ignores null days when counting`() {
        val weekly = WeeklyHealth(
            dailyRestingHeartRate = listOf(
                DailyRestingHeartRate(today.minusDays(2), null),
                DailyRestingHeartRate(today.minusDays(1), null),
                DailyRestingHeartRate(today, 58),
            ),
        )
        assertFalse(weekly.hasRestingHeartRateTrend)
    }

    @Test fun `hasStepTrend still requires at least two positive days`() {
        val weekly = WeeklyHealth(
            dailySteps = listOf(
                DailySteps(today.minusDays(1), 0),
                DailySteps(today, 5000),
            ),
        )
        assertFalse(weekly.hasStepTrend)
        assertEquals(5000L, weekly.stepsToday)
    }
}
