package se.partee71.dagboken.core.engine.health

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.model.DailyHealth

/**
 * Dagshistoriken och Hälsa idag ur en periods poster (HLS-8, HLS-12): ett [DailyHealth] per dygn, luckor
 * i stället för nollor, nätter efter sitt slut, sommartid via zonen. Påhittade värden.
 */
class HealthDaysTest {

    private val mar27 = LocalDate(2026, 3, 27)
    private val mar28 = LocalDate(2026, 3, 28)
    private val mar29 = LocalDate(2026, 3, 29) // sommartid börjar 02:00 → 03:00, dygnet har 23 timmar
    private val mar30 = LocalDate(2026, 3, 30)

    private fun steps(origin: String, date: LocalDate, time: String, count: Long) =
        at(date, time).let { StepSample(origin, it, it + 30.minutes, count) }

    // ─── Dagshistorik ─────────────────────────────────────────────────────────

    @Test fun `the history has exactly one day per date, oldest first`() {
        val history = healthHistory(HealthRecords(steps = listOf(steps("watch", mar28, "10:00", 4000))), mar27, mar30, STOCKHOLM)
        assertEquals(listOf(mar27, mar28, mar29, mar30), history.dates)
        assertEquals(4000L, history.days[1].steps)
    }

    @Test fun `a day without measurements is a gap, never zero`() {
        val history = healthHistory(HealthRecords(steps = listOf(steps("watch", mar28, "10:00", 4000))), mar27, mar29, STOCKHOLM)
        assertEquals(DailyHealth(mar27), history.days[0])
        assertTrue(history.days[0].isEmpty)
        assertNull(history.days[2].steps)
        assertNull(history.days[2].heartRateAvg)
        assertEquals(0, history.days[2].exerciseSessions)
        assertNull(history.days[2].exerciseDuration)
    }

    @Test fun `records outside the period are left out`() {
        val history = healthHistory(HealthRecords(steps = listOf(steps("watch", mar27, "10:00", 4000))), mar28, mar29, STOCKHOLM)
        assertTrue(!history.hasAnyData)
    }

    @Test fun `two sources that do not overlap pick the most complete one per day`() {
        // Telefonen bars på förmiddagen och klockan på eftermiddagen: klockans dygnssumma vinner, ingen summering.
        val records = HealthRecords(
            steps = listOf(steps("phone", mar28, "08:00", 3000), steps("watch", mar28, "14:00", 5000), steps("watch", mar28, "18:00", 1000)),
            calories = listOf(at(mar28, "08:00").let { CaloriesSample("phone", it, it + 1.hours, 150.0) }),
            distance = listOf(at(mar28, "14:00").let { DistanceSample("watch", it, it + 1.hours, 2500.0) }),
        )
        val day = healthHistory(records, mar28, mar28, STOCKHOLM).days.single()
        assertEquals(6000L, day.steps)
        assertEquals(150.0, day.activeEnergyKcal!!, 0.001)
        assertEquals(2500.0, day.distanceMeters!!, 0.001)
    }

    @Test fun `a night over midnight is dated by its end and the longest of several sessions wins`() {
        val records = HealthRecords(
            sleep = listOf(
                sleep("2026-03-27T22:30", "2026-03-28T01:00"),
                sleep(
                    "2026-03-28T01:30",
                    "2026-03-28T07:00",
                    stages = listOf(SleepStageSlice(SleepStageType.DEEP, local("2026-03-28T02:00"), local("2026-03-28T03:00"))),
                ),
            ),
        )
        val history = healthHistory(records, mar27, mar28, STOCKHOLM)
        assertNull(history.days[0].sleepDuration)
        assertEquals(330.minutes, history.days[1].sleepDuration)
        assertEquals(60.minutes, history.days[1].sleepStages.deep)
    }

    @Test fun `the spring-forward change neither loses nor doubles a day`() {
        val records = HealthRecords(
            steps = listOf(
                steps("watch", mar29, "00:30", 100), // före omställningen
                steps("watch", mar29, "23:30", 200), // sent samma dygn, efter den
                steps("watch", mar30, "00:10", 400),
            ),
            sleep = listOf(sleep("2026-03-28T23:00", "2026-03-29T07:00")),
        )
        val history = healthHistory(records, mar28, mar30, STOCKHOLM)
        assertEquals(listOf(mar28, mar29, mar30), history.dates)
        assertEquals(300L, history.days[1].steps)
        assertEquals(400L, history.days[2].steps)
        // 23:00 CET → 07:00 CEST är sju timmar verklig tid, inte åtta.
        assertEquals(7.hours, history.days[1].sleepDuration)
    }

    @Test fun `resting heart rate in the history skips the night the session covers on both dates`() {
        val records = HealthRecords(
            heartRate = listOf(
                HeartRateSample("watch", local("2026-03-27T23:30"), 44),
                HeartRateSample("watch", local("2026-03-28T03:00"), 45),
                HeartRateSample("watch", local("2026-03-27T12:00"), 64),
                HeartRateSample("watch", local("2026-03-28T12:00"), 61),
            ),
            sleep = listOf(sleep("2026-03-27T23:00", "2026-03-28T06:00")),
        )
        val history = healthHistory(records, mar27, mar28, STOCKHOLM)
        assertEquals(64L, history.days[0].restingHeartRate)
        assertEquals(61L, history.days[1].restingHeartRate)
        assertEquals(54L, history.days[0].heartRateAvg)
    }

    @Test fun `every metric of a day is filled from its own records`() {
        val records = HealthRecords(
            heartRate = listOf(HeartRateSample("watch", at(mar28, "12:00"), 70)),
            restingHeartRate = listOf(RestingHeartRateSample("watch", at(mar28, "09:00"), 55)),
            exercise = listOf(at(mar28, "17:00").let { ExerciseSession("watch", it, it + 40.minutes) }),
            oxygen = listOf(OxygenSample("watch", at(mar28, "03:00"), 96.0)),
        )
        val day = healthHistory(records, mar28, mar28, STOCKHOLM).days.single()
        assertEquals(70L, day.heartRateAvg)
        assertEquals(55L, day.restingHeartRate)
        assertEquals(1, day.exerciseSessions)
        assertEquals(40.minutes, day.exerciseDuration)
        assertEquals(96.0, day.oxygenSaturationAvg!!, 0.001)
    }

    // ─── Hälsa idag ───────────────────────────────────────────────────────────

    private val now = at(mar28, "14:00")

    @Test fun `today counts from midnight up to now`() {
        val records = HealthRecords(
            steps = listOf(steps("watch", mar27, "22:00", 900), steps("watch", mar28, "09:00", 2000), steps("watch", mar28, "15:00", 700)),
        )
        assertEquals(2000L, healthDay(records, mar28, now, STOCKHOLM).steps)
    }

    @Test fun `last night sums every session ending within the night window, unlike the history`() {
        // Paritet med 3.x readToday: längd och stadier summeras över sessionerna (3 h + 5 h); historiken tar den längsta.
        val records = HealthRecords(
            sleep = listOf(
                sleep("2026-03-26T23:00", "2026-03-27T07:00"), // för gammal
                sleep(
                    "2026-03-27T23:00",
                    "2026-03-28T02:00",
                    stages = listOf(SleepStageSlice(SleepStageType.DEEP, local("2026-03-27T23:30"), local("2026-03-28T00:30"))),
                ),
                sleep(
                    "2026-03-28T02:30",
                    "2026-03-28T07:30",
                    stages = listOf(SleepStageSlice(SleepStageType.DEEP, local("2026-03-28T03:00"), local("2026-03-28T03:45"))),
                ),
            ),
        )
        val today = healthDay(records, mar28, now, STOCKHOLM)
        assertEquals(8.hours, today.sleepDuration)
        assertEquals(105.minutes, today.sleepStages.deep)
        val history = healthHistory(records, mar28, mar28, STOCKHOLM).days.single()
        assertEquals(5.hours, history.sleepDuration)
        assertEquals(45.minutes, history.sleepStages.deep)
    }

    @Test fun `a record exactly at the next midnight belongs only to the next date`() {
        val midnight = at(mar28, "00:00")
        val records = HealthRecords(
            steps = listOf(StepSample("watch", midnight, midnight + 1.minutes, 500), steps("watch", mar27, "12:00", 1000)),
            heartRate = listOf(HeartRateSample("watch", midnight, 90), HeartRateSample("watch", at(mar27, "12:00"), 60)),
            oxygen = listOf(OxygenSample("watch", midnight, 90.0)),
            sleep = listOf(SleepSession("watch", at(mar27, "16:00"), midnight)),
        )
        val earlier = healthDay(records, mar27, now, STOCKHOLM)
        assertEquals(1000L, earlier.steps)
        assertEquals(60L, earlier.heartRateAvg)
        assertNull(earlier.oxygenSaturationAvg)
        assertNull(earlier.sleepDuration)
        val next = healthDay(records, mar28, now, STOCKHOLM)
        assertEquals(500L, next.steps)
        assertEquals(8.hours, next.sleepDuration)
    }

    @Test fun `a finished day gives the same values in healthDay and healthHistory, apart from sleep`() {
        val records = HealthRecords(
            steps = listOf(steps("phone", mar27, "08:00", 3000), steps("watch", mar27, "14:00", 5200)),
            heartRate = listOf(
                HeartRateSample("watch", local("2026-03-27T02:00"), 48),
                HeartRateSample("watch", local("2026-03-27T10:00"), 66),
                HeartRateSample("watch", local("2026-03-27T18:00"), 82),
            ),
            restingHeartRate = listOf(RestingHeartRateSample("watch", local("2026-03-27T09:00"), 57)),
            sleep = listOf(sleep("2026-03-26T23:00", "2026-03-27T06:30")),
            exercise = listOf(at(mar27, "17:00").let { ExerciseSession("watch", it, it + 30.minutes) }),
            calories = listOf(at(mar27, "17:00").let { CaloriesSample("watch", it, it + 30.minutes, 210.0) }),
            distance = listOf(at(mar27, "17:00").let { DistanceSample("watch", it, it + 30.minutes, 4100.0) }),
        )
        val day = healthDay(records, mar27, now, STOCKHOLM)
        val history = healthHistory(records, mar27, mar27, STOCKHOLM).days.single()
        assertEquals(history.steps, day.steps)
        assertEquals(history.heartRateAvg, day.heartRateAvg)
        assertEquals(history.restingHeartRate, day.restingHeartRate)
        assertEquals(history.exerciseSessions, day.exerciseSessions)
        assertEquals(history.exerciseDuration, day.exerciseDuration)
        assertEquals(history.activeEnergyKcal, day.activeEnergyKcal)
        assertEquals(history.distanceMeters, day.distanceMeters)
        assertEquals(5200L, day.steps)
    }

    @Test fun `oxygen uses the same 24-hour window as the sleep`() {
        val records = HealthRecords(
            oxygen = listOf(
                OxygenSample("watch", local("2026-03-27T13:00"), 80.0), // mer än ett dygn sedan
                OxygenSample("watch", local("2026-03-27T23:30"), 95.0), // i natt, före midnatt
                OxygenSample("watch", local("2026-03-28T04:00"), 97.0),
            ),
        )
        assertEquals(96.0, healthDay(records, mar28, now, STOCKHOLM).oxygenSaturationAvg!!, 0.001)
    }

    @Test fun `an earlier date is read up to the end of that day`() {
        val records = HealthRecords(steps = listOf(steps("watch", mar27, "09:00", 1200), steps("watch", mar27, "23:00", 300)))
        assertEquals(1500L, healthDay(records, mar27, now, STOCKHOLM).steps)
    }

    @Test fun `a day without anything is empty`() {
        assertEquals(DailyHealth(mar28), healthDay(HealthRecords(), mar28, now, STOCKHOLM))
    }

    @Test fun `regularity uses the nights read before the period, so every period gives the same value`() {
        // Tjugo nätter 23:00–07:00 med mittpunkten växlande 03:00/03:30; perioderna "vecka" och "månad" ur samma poster.
        val sleep = (1..20).map { day ->
            val shift = if (day % 2 == 0) "30" else "00"
            sleep("2026-08-%02dT23:%s".format(day, shift), "2026-08-%02dT07:%s".format(day + 1, shift))
        }
        val records = HealthRecords(sleep = sleep)
        val to = LocalDate(2026, 8, 21)
        val week = healthHistory(records, LocalDate(2026, 8, 15), to, STOCKHOLM)
        val month = healthHistory(records, LocalDate(2026, 7, 22), to, STOCKHOLM)
        val weekFirst = week.days.first()
        assertTrue("veckans första natt har sitt fönster ur nätterna före veckan", weekFirst.sleepMidpointSdMinutes != null)
        assertEquals(week.days.map { it.sleepMidpointSdMinutes }, month.days.takeLast(7).map { it.sleepMidpointSdMinutes })
        assertNull("en dag utan natt har ingen regelbundenhet", month.days.first().sleepMidpointSdMinutes)
    }

    @Test fun `the read windows reach a night before the period and the whole regularity window for sleep`() {
        val start = at(mar28, "00:00")
        val windows = healthReadWindows(start, start + 10.hours)
        assertEquals(start, windows.samples.start)
        assertEquals(start - 24.hours, windows.lead.start)
        assertEquals(start - 24.hours * 14, windows.sleep.start)
        assertEquals(listOf(start + 10.hours), listOf(windows.samples, windows.lead, windows.sleep).map { it.end }.distinct())
    }
}
