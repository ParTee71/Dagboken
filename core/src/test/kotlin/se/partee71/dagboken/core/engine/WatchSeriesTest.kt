package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import org.junit.Test
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.SleepStages

/**
 * Klockans serier i Trender (TRD-11, TRD-15, TRD-16, HLS-12, HLS-13): ett värde per dag i måttets enhet,
 * luckor där dygnet saknar måttet, sömnstadierna som staplar, sömnkvaliteten per natt och "Allt" kapad vid ett år.
 */
class WatchSeriesTest {

    private val today = LocalDate(2026, 10, 6)
    private val days = daysEnding(today, 4)

    private val full = DailyHealth(
        LocalDate(2026, 10, 5),
        steps = 8_250,
        restingHeartRate = 56,
        heartRateAvg = 71,
        sleepDuration = 7.hours + 30.minutes,
        sleepStages = SleepStages(deep = 1.hours + 15.minutes, rem = 1.hours + 30.minutes, light = 4.hours, awake = 45.minutes),
        exerciseDuration = 42.minutes,
        activeEnergyKcal = 512.5,
        distanceMeters = 6_250.0,
        oxygenSaturationAvg = 96.4,
    )
    private val history = HealthHistory.of(days.first(), today, mapOf(full.date to full, today to DailyHealth(today, steps = 1_200, sleepDuration = 6.hours)))

    @Test
    fun `varje mått plockas i sin enhet – timmar, minuter, kilometer – och saknas som lucka`() {
        val expected = mapOf(
            WatchMetric.STEPS to 8_250f,
            WatchMetric.RESTING_HEART_RATE to 56f,
            WatchMetric.HEART_RATE_AVG to 71f,
            WatchMetric.SLEEP_TOTAL to 7.5f,
            WatchMetric.SLEEP_DEEP to 1.25f,
            WatchMetric.SLEEP_REM to 1.5f,
            WatchMetric.SLEEP_LIGHT to 4f,
            WatchMetric.SLEEP_AWAKE to 0.75f,
            WatchMetric.EXERCISE to 42f,
            WatchMetric.ACTIVE_CALORIES to 512.5f,
            WatchMetric.DISTANCE to 6.25f,
            WatchMetric.OXYGEN_SATURATION to 96.4f,
        )
        WatchMetric.entries.forEach { metric ->
            assertEquals(expected.getValue(metric), metric.value(full), metric.name)
            if (metric != WatchMetric.STEPS) assertNull(metric.value(DailyHealth(today, steps = 1)), "${metric.name} saknas idag")
        }
    }

    @Test
    fun `serierna ligger på periodens dagar med luckor utanför historiken och där måttet saknas (HLS-12)`() {
        val series = watchSeries(history, listOf(WatchMetric.STEPS, WatchMetric.RESTING_HEART_RATE), days)
        assertEquals(listOf("STEPS", "RESTING_HEART_RATE"), series.map { it.key })
        assertEquals(listOf(null, null, 8_250f, 1_200f), series[0].points)
        assertEquals(listOf(null, null, 56f, null), series[1].points)

        val longer = watchSeries(history, listOf(WatchMetric.STEPS), daysEnding(today, 6))
        assertEquals(6, longer.single().points.size)
        assertNull(longer.single().points.first(), "en dag före historiken är en lucka, ingen nolla")
    }

    @Test
    fun `sömnstadierna staplas djup, REM, lätt, vaken i timmar – natt utan stadier är en lucka (TRD-16)`() {
        val points = sleepStagePoints(history, days)
        assertEquals(4, points.size)
        assertEquals(StackedPoint(listOf(1.25f, 1.5f, 4f, 0.75f)), points[2])
        assertEquals(StackedPoint(listOf(null, null, null, null)), points[3])
        assertEquals(listOf(WatchMetric.SLEEP_DEEP, WatchMetric.SLEEP_REM, WatchMetric.SLEEP_LIGHT, WatchMetric.SLEEP_AWAKE), SLEEP_STAGE_METRICS)
        assertEquals(WatchMetric.SLEEP_TOTAL, SLEEP_METRICS.first())
    }

    @Test
    fun `sömnkvaliteten räknas per natt ur sömnlängd och stadier – poäng och delpoäng, 0–100 (HLS-13)`() {
        val nights = sleepMeasurements(history)
        assertEquals(listOf(full.date, today), nights.map { it.date }, "bara nätter med sömnlängd")
        val night = nights.first().measurements
        assertEquals(7.hours + 30.minutes, night.timeInBed)
        assertEquals(45.minutes, night.awake)
        assertNull(night.midpointSdMinutes, "utan mittpunkter i dagshistoriken faller regelbundenheten bort")

        val series = sleepQualitySeries(history, age = 50, sex = Sex.MALE, days = days)
        assertEquals(SLEEP_QUALITY_KEYS, series.map { it.key })
        assertEquals(SleepQualityKind.entries.size + 1, series.size, "poängen och sex delpoäng")
        assertTrue("REGULARITY" in SLEEP_QUALITY_KEYS, "regelbundenheten visas när dagshistoriken bär mittpunkterna (#243)")
        assertEquals(listOf(SLEEP_SCORE_KEY), sleepQualitySeries(history, 50, Sex.MALE, days, listOf(SLEEP_SCORE_KEY)).map { it.key })
        assertEquals(emptyList(), sleepQualitySeries(history, 50, Sex.MALE, days, emptyList()))
        val expected = scoreSleepQuality(night, 50, Sex.MALE)
        assertNotNull(expected)
        val score = series.first { it.key == SLEEP_SCORE_KEY }
        assertEquals(listOf(null, null, expected.score.toFloat()), score.points.take(3))
        assertNotNull(score.points[3], "en natt med bara längd bedöms på längden")
        assertEquals(expected.components.first { it.kind == SleepQualityKind.DURATION }.score.toFloat(), series.first { it.key == "DURATION" }.points[2])
        assertNull(series.first { it.key == "EFFICIENCY" }.points[3], "en komponent som inte gick att räkna (inga stadier) är en lucka")
        series.forEach { serie -> serie.points.filterNotNull().forEach { assertTrue(it in 0f..100f, "${serie.key} = $it") } }
    }

    @Test
    fun `utan födelseår är varje natt en lucka (HLS-11)`() {
        val series = sleepQualitySeries(history, age = null, sex = Sex.UNSPECIFIED, days = days)
        series.forEach { serie -> assertTrue(serie.points.all { it == null }, serie.key) }
    }

    @Test
    fun `Allt kapas vid 365 dagar för klockan, fasta perioder är som förut (TRD-15, TRD-17)`() {
        val all = TrendRange.ALL.cappedDays(today)
        assertEquals(WATCH_MAX_DAYS, all.size)
        assertEquals(today, all.last())
        assertEquals(LocalDate(2025, 10, 7), all.first())
        assertEquals(LocalDate(2025, 10, 7), TrendRange.ALL.cappedReadFrom(today, withPrevious = true))
        assertEquals(TrendRange.MONTH.days(today), TrendRange.MONTH.cappedDays(today))
        assertEquals(TrendRange.MONTH.readFrom(today, true), TrendRange.MONTH.cappedReadFrom(today, true))
        assertEquals(TrendRange.SEVEN_DAYS.from(today), TrendRange.SEVEN_DAYS.cappedReadFrom(today, false))
    }

    @Test
    fun `nattens regelbundenhet ur dagshistoriken går in i sömnkvaliteten (HLS-13)`() {
        val regular = full.copy(sleepMidpointSdMinutes = 12.0)
        val nights = sleepMeasurements(HealthHistory.of(full.date, full.date, mapOf(full.date to regular)))
        assertEquals(12.0, nights.single().measurements.midpointSdMinutes)
        val quality = assertNotNull(scoreSleepQuality(nights.single().measurements, 50, Sex.MALE))
        assertTrue(quality.components.any { it.kind == SleepQualityKind.REGULARITY })
    }
}
