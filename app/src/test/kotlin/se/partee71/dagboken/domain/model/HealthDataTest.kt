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

    // ─── statsFor (#138 — hälsokort per vald dag) ────────────────────────────

    @Test fun `statsFor today uses stepsToday and restingHeartRate rather than the daily lists`() {
        val weekly = WeeklyHealth(
            dailySteps           = listOf(DailySteps(today, 8200)),
            dailyRestingHeartRate = emptyList(),
            restingHeartRate     = 58,
        )
        val stats = weekly.statsFor(today, isToday = true)
        assertEquals(8200L, stats.steps)
        assertEquals(58L, stats.restingHeartRate)
    }

    @Test fun `statsFor a past day reads from the daily lists`() {
        val yesterday = today.minusDays(1)
        val weekly = WeeklyHealth(
            dailySteps            = listOf(DailySteps(yesterday, 4000), DailySteps(today, 8200)),
            dailyRestingHeartRate = listOf(DailyRestingHeartRate(yesterday, 55), DailyRestingHeartRate(today, 58)),
            restingHeartRate      = 58,
        )
        val stats = weekly.statsFor(yesterday, isToday = false)
        assertEquals(4000L, stats.steps)
        assertEquals(55L, stats.restingHeartRate)
    }

    @Test fun `statsFor a day outside the fetched window is null for both values`() {
        val weekly = WeeklyHealth(
            dailySteps            = listOf(DailySteps(today, 8200)),
            dailyRestingHeartRate = listOf(DailyRestingHeartRate(today, 58)),
            restingHeartRate      = 58,
        )
        val stats = weekly.statsFor(today.minusDays(10), isToday = false)
        assertEquals(null, stats.steps)
        assertEquals(null, stats.restingHeartRate)
    }

    @Test fun `statsFor a past day with zero steps is null, not zero`() {
        val yesterday = today.minusDays(1)
        val weekly = WeeklyHealth(dailySteps = listOf(DailySteps(yesterday, 0), DailySteps(today, 8200)))
        val stats = weekly.statsFor(yesterday, isToday = false)
        assertEquals(null, stats.steps)
    }
}
