package se.partee71.dagboken.core.engine.health

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dygnsfördelningen i hälsohistoriken (HLS-12): ett svep per posttyp blir ett värde per dygn, och
 * fördelningen ska bli densamma som en läsning per dag hade gett. Portad från 3.x `HealthHistoryBucketingTest`.
 */
class HealthHistoryBucketingTest {

    private val day1 = LocalDate(2026, 3, 10)
    private val day2 = LocalDate(2026, 3, 11)
    private val day3 = LocalDate(2026, 3, 12)

    private fun steps(origin: String, date: LocalDate, time: String, count: Long) =
        at(date, time).let { StepSample(origin, it, it + 1.minutes, count) }

    private fun exercise(origin: String, date: LocalDate, time: String, minutes: Int) =
        at(date, time).let { ExerciseSession(origin, it, it + minutes.minutes) }

    private fun bpm(date: LocalDate, time: String, value: Long) = HeartRateSample("watch", at(date, time), value)

    // ─── Summerbara mått per dygn ─────────────────────────────────────────────

    @Test fun `samples are bucketed on the day their start time falls on`() {
        val byDay = mostCompleteSumByDay(
            listOf(steps("watch", day1, "08:00", 1000), steps("watch", day1, "18:00", 2000), steps("watch", day2, "09:00", 500)),
            STOCKHOLM,
        )
        assertEquals(3000.0, byDay.getValue(day1), 0.001)
        assertEquals(500.0, byDay.getValue(day2), 0.001)
    }

    @Test fun `a day without samples is absent rather than zero`() {
        val byDay = mostCompleteSumByDay(listOf(steps("watch", day1, "08:00", 1000)), STOCKHOLM)
        assertFalse(byDay.containsKey(day2))
        assertNull(byDay[day2])
    }

    @Test fun `the most complete source wins per day and sources are never summed`() {
        val byDay = mostCompleteSumByDay(
            listOf(
                steps("phone", day1, "08:00", 4000),
                steps("watch", day1, "08:00", 6000),
                steps("watch", day1, "18:00", 3000),
                steps("phone", day2, "08:00", 2500),
            ),
            STOCKHOLM,
        )
        assertEquals("Klockans 9000 ska vinna, aldrig 13000", 9000.0, byDay.getValue(day1), 0.001)
        assertEquals(2500.0, byDay.getValue(day2), 0.001)
    }

    @Test fun `exercise sessions written by two sources are deduplicated per day`() {
        val byDay = mostCompleteExerciseByDay(
            listOf(
                exercise("phone", day1, "07:00", 20),
                exercise("watch", day1, "07:00", 45),
                exercise("watch", day1, "17:00", 30),
                exercise("watch", day2, "07:00", 15),
            ),
            STOCKHOLM,
        )
        assertEquals(2, byDay.getValue(day1).sessions)
        assertEquals(75.minutes, byDay.getValue(day1).duration)
        assertEquals(1, byDay.getValue(day2).sessions)
        assertFalse(byDay.containsKey(day3))
    }

    @Test fun `complementary sessions from two sources are both kept for the day`() {
        // Regression för #220.
        val byDay = mostCompleteExerciseByDay(
            listOf(exercise("watch", day1, "07:00", 45), exercise("phone", day1, "12:00", 25)),
            STOCKHOLM,
        )
        assertEquals(2, byDay.getValue(day1).sessions)
        assertEquals(70.minutes, byDay.getValue(day1).duration)
    }

    // ─── Puls per dygn ────────────────────────────────────────────────────────

    @Test fun `average heart rate is computed per day`() {
        val byDay = averageBpmByDay(listOf(bpm(day1, "08:00", 60), bpm(day1, "12:00", 80), bpm(day2, "08:00", 100)), STOCKHOLM)
        assertEquals(70L, byDay.getValue(day1))
        assertEquals(100L, byDay.getValue(day2))
    }

    @Test fun `a recorded resting heart rate wins over the estimate for the same day`() {
        val byDay = restingHeartRateByDay(
            recorded = listOf(RestingHeartRateSample("watch", at(day1, "22:00"), 52)),
            samples = listOf(bpm(day1, "08:00", 70), bpm(day1, "12:00", 90)),
            sleepWindows = emptyList(),
            zone = STOCKHOLM,
        )
        assertEquals(52L, byDay.getValue(day1))
    }

    @Test fun `the latest recorded value of the day wins`() {
        val byDay = restingHeartRateByDay(
            recorded = listOf(RestingHeartRateSample("watch", at(day1, "07:00"), 60), RestingHeartRateSample("watch", at(day1, "22:00"), 54)),
            samples = emptyList(),
            sleepWindows = emptyList(),
            zone = STOCKHOLM,
        )
        assertEquals(54L, byDay.getValue(day1))
    }

    @Test fun `resting heart rate falls back to an estimate from the day's own samples`() {
        val byDay = restingHeartRateByDay(
            recorded = emptyList(),
            samples = (1..40).map { HeartRateSample("watch", at(day1, "08:00") + (it * 60).seconds, 60L + it) },
            sleepWindows = emptyList(),
            zone = STOCKHOLM,
        )
        // Lägsta 5-percentilen av 61..100 → 61 och 62, medel 61,5 → 62.
        assertEquals(62L, byDay.getValue(day1))
    }

    @Test fun `sleeping samples are excluded from the daily resting estimate`() {
        val byDay = restingHeartRateByDay(
            recorded = emptyList(),
            samples = listOf(bpm(day1, "03:00", 44), bpm(day1, "09:00", 62), bpm(day1, "15:00", 88)),
            sleepWindows = listOf(TimeWindow(at(day1, "01:00"), at(day1, "06:00"))),
            zone = STOCKHOLM,
        )
        assertEquals(62L, byDay.getValue(day1))
    }

    // ─── Nätter per dygn ──────────────────────────────────────────────────────

    private fun night(from: LocalDate, fromTime: String, to: LocalDate, toTime: String, deepMinutes: Int = 0): SleepSession {
        val start = at(from, fromTime)
        val stages = if (deepMinutes > 0) listOf(SleepStageSlice(SleepStageType.DEEP, start, start + deepMinutes.minutes)) else emptyList()
        return SleepSession("watch", start, at(to, toTime), stages)
    }

    @Test fun `a night crossing midnight belongs to the morning's date`() {
        val byDay = longestNightPerDay(listOf(night(day1, "23:10", day2, "06:40")), STOCKHOLM)
        assertTrue("Natten ska dateras efter sitt slut", byDay.containsKey(day2))
        assertFalse(byDay.containsKey(day1))
        assertEquals(450.minutes, byDay.getValue(day2).duration)
    }

    @Test fun `the longest session wins when a night is split in two`() {
        val byDay = longestNightPerDay(
            listOf(night(day1, "22:30", day2, "01:00"), night(day2, "01:30", day2, "07:00", deepMinutes = 60)),
            STOCKHOLM,
        )
        assertEquals(1, byDay.size)
        assertEquals(330.minutes, byDay.getValue(day2).duration)
        assertEquals(60.minutes, summarizeSleepStages(byDay.getValue(day2).stages).deep)
    }

    @Test fun `separate nights land on separate days`() {
        val byDay = longestNightPerDay(listOf(night(day1, "23:00", day2, "07:00"), night(day2, "23:00", day3, "07:00")), STOCKHOLM)
        assertEquals(setOf(day2, day3), byDay.keys)
    }

    @Test fun `the midpoint of a night is the clock time halfway through it`() {
        assertEquals(LocalTime(3, 0), midpointOf(TimeWindow(at(day1, "23:00"), at(day2, "07:00")), STOCKHOLM))
    }

    // ─── Syremättnad och blodtryck per dygn ───────────────────────────────────

    @Test fun `oxygen saturation is averaged per day`() {
        val byDay = averageOxygenByDay(
            listOf(
                OxygenSample("watch", at(day1, "02:00"), 95.0),
                OxygenSample("watch", at(day1, "03:00"), 93.0),
                OxygenSample("watch", at(day2, "02:00"), 90.0),
            ),
            emptyList(),
            STOCKHOLM,
        )
        assertEquals(94.0, byDay.getValue(day1), 0.001)
        assertEquals(90.0, byDay.getValue(day2), 0.001)
    }

    @Test fun `oxygen inside a sleep session is dated by the session's end`() {
        // Hela natten hamnar på morgonens datum, också provet före midnatt; ett prov på dagen behåller sitt datum.
        val byDay = averageOxygenByDay(
            listOf(
                OxygenSample("watch", at(day1, "23:30"), 94.0),
                OxygenSample("watch", at(day2, "04:00"), 96.0),
                OxygenSample("watch", at(day1, "14:00"), 99.0),
            ),
            listOf(night(day1, "23:00", day2, "07:00")),
            STOCKHOLM,
        )
        assertEquals(99.0, byDay.getValue(day1), 0.001)
        assertEquals(95.0, byDay.getValue(day2), 0.001)
    }

    @Test fun `the same session from two sources either side of midnight counts once`() {
        // Klockan startade 23:50, telefonen 00:05 – samma pass, inte ett per dygn.
        val byDay = mostCompleteExerciseByDay(
            listOf(exercise("watch", day1, "23:50", 50), exercise("phone", day2, "00:05", 20)),
            STOCKHOLM,
        )
        assertEquals(setOf(day1), byDay.keys)
        assertEquals(1, byDay.getValue(day1).sessions)
        assertEquals(50.minutes, byDay.getValue(day1).duration)
    }

    @Test fun `blood pressure is the latest reading of each day, rounded`() {
        val byDay = bloodPressureByDay(
            listOf(
                BloodPressureSample("cuff", at(day1, "08:00"), 140.0, 90.0),
                BloodPressureSample("cuff", at(day1, "20:00"), 121.6, 79.4),
            ),
            STOCKHOLM,
        )
        assertEquals(122, byDay.getValue(day1).systolic)
        assertEquals(79, byDay.getValue(day1).diastolic)
        assertFalse(byDay.containsKey(day2))
    }
}
