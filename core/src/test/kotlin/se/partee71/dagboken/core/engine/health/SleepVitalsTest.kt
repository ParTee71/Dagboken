package se.partee71.dagboken.core.engine.health

import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.engine.SleepFlag
import se.partee71.dagboken.core.engine.sleepMeasurements
import se.partee71.dagboken.core.engine.sleepQualityOn
import se.partee71.dagboken.core.engine.SleepMeasurements
import se.partee71.dagboken.core.engine.sleepFlags
import se.partee71.dagboken.core.engine.sleepFlagsOn
import se.partee71.dagboken.core.model.Sex

/**
 * Nattens sovpuls, vakna baslinje och syremättnad (HLS-10, HLS-13) – underlaget för sömnkvalitetens varningsrader,
 * med 3.x `readSleepMeasurements`/`readSleepMeasurementsHistory` som facit. Påhittade värden, aldrig riktig data.
 */
class SleepVitalsTest {

    private val aug1 = LocalDate(2026, 8, 1)
    private val aug2 = LocalDate(2026, 8, 2)
    private val aug3 = LocalDate(2026, 8, 3)
    private val now = local("2026-08-03T20:00")

    private fun hr(time: String, bpm: Long) = HeartRateSample("watch", local(time), bpm)
    private fun spo2(time: String, percent: Double) = OxygenSample("watch", local(time), percent)

    /** 3.x-fallet: natten 23:00–07:00 med sovpuls 68 mot en vaken baslinje på 60 och syremättnad 88,5 %. */
    private val records = HealthRecords(
        heartRate = listOf(
            hr("2026-08-01T10:00", 60),
            hr("2026-08-01T14:00", 72),
            hr("2026-08-01T23:30", 66), // i natt, före midnatt
            hr("2026-08-02T03:00", 70),
            hr("2026-08-02T12:00", 75),
        ),
        sleep = listOf(sleep("2026-08-01T23:00", "2026-08-02T07:00")),
        oxygen = listOf(
            spo2("2026-08-01T23:30", 88.0),
            spo2("2026-08-02T04:00", 89.0),
            spo2("2026-08-02T09:00", 97.0), // vaken – utanför natten
        ),
    )

    @Test fun `sleeping heart rate and oxygen are the averages inside the night, the baseline the awake estimate`() {
        val night = healthHistory(records, aug1, aug2, STOCKHOLM).days[1]
        assertEquals(68L, night.sleepHeartRate)
        assertEquals(60L, night.sleepHeartRateBaseline)
        assertEquals(88.5, night.sleepOxygenSaturation!!, 0.001)
    }

    @Test fun `the night carries both warnings into the sleep quality, as in 3_x`() {
        val history = healthHistory(records, aug1, aug2, STOCKHOLM)
        val measurements = sleepMeasurements(history).single().measurements
        assertEquals(68L, measurements.sleepingHeartRate)
        assertEquals(60L, measurements.baselineRestingHeartRate)
        assertEquals(88.5, measurements.meanOxygenSaturation!!, 0.001)

        val quality = sleepQualityOn(history, aug2, 50, Sex.FEMALE)!!
        assertEquals(listOf(SleepFlag.LOW_OXYGEN_SATURATION, SleepFlag.ELEVATED_SLEEPING_HEART_RATE), quality.flags)
        assertEquals("samma rader som poängens", quality.flags, sleepFlagsOn(history, aug2))
        assertNull("utan födelseår ingen poäng", sleepQualityOn(history, aug2, null, Sex.FEMALE))
    }

    @Test fun `a calm night with normal oxygen raises no warning`() {
        val calm = records.copy(
            heartRate = records.heartRate.map { if (it.time in records.sleep.single()) it.copy(bpm = 62) else it },
            oxygen = records.oxygen.map { it.copy(percent = 95.0) },
        )
        val quality = sleepQualityOn(healthHistory(calm, aug1, aug2, STOCKHOLM), aug2, 50, Sex.FEMALE)!!
        assertTrue(quality.flags.isEmpty())
    }

    @Test fun `the night window is half open - a sample at the start counts, one at the end does not`() {
        val edges = HealthRecords(
            heartRate = listOf(hr("2026-08-01T23:00", 50), hr("2026-08-02T07:00", 90), hr("2026-08-02T12:00", 70)),
            sleep = records.sleep,
            oxygen = listOf(spo2("2026-08-01T23:00", 91.0), spo2("2026-08-02T07:00", 80.0)),
        )
        val night = healthHistory(edges, aug2, aug2, STOCKHOLM).days.single()
        assertEquals(50L, night.sleepHeartRate)
        assertEquals(91.0, night.sleepOxygenSaturation!!, 0.001)
    }

    @Test fun `a night without heart rate or oxygen has gaps, and a day without a night has no night values`() {
        val bare = HealthRecords(heartRate = listOf(hr("2026-08-02T12:00", 70)), sleep = records.sleep)
        val history = healthHistory(bare, aug2, aug3, STOCKHOLM)
        val night = history.days[0]
        assertNull(night.sleepHeartRate)
        assertNull(night.sleepOxygenSaturation)
        assertEquals("baslinjen finns även när natten saknar pulsprov", 70L, night.sleepHeartRateBaseline)
        val noNight = history.days[1]
        assertNull(noNight.sleepHeartRate)
        assertNull(noNight.sleepHeartRateBaseline)
        assertNull(noNight.sleepOxygenSaturation)
    }

    @Test fun `the longest session is the night, and every session is left out of the baseline`() {
        val split = HealthRecords(
            heartRate = listOf(hr("2026-08-02T00:30", 52), hr("2026-08-02T04:00", 64), hr("2026-08-02T12:00", 61)),
            sleep = listOf(sleep("2026-08-02T00:00", "2026-08-02T01:00"), sleep("2026-08-02T02:00", "2026-08-02T07:00")),
        )
        val night = healthHistory(split, aug2, aug2, STOCKHOLM).days.single()
        assertEquals("bara den längsta sessionens prov", 64L, night.sleepHeartRate)
        assertEquals("den korta sessionens prov är inte vaken tid", 61L, night.sleepHeartRateBaseline)
    }

    @Test fun `the baseline is shared by the nights of the period`() {
        val twoNights = records.copy(
            heartRate = records.heartRate + hr("2026-08-03T02:00", 58),
            sleep = records.sleep + sleep("2026-08-02T23:00", "2026-08-03T07:00"),
        )
        val history = healthHistory(twoNights, aug2, aug3, STOCKHOLM)
        assertEquals(listOf(68L, 58L), history.days.map { it.sleepHeartRate })
        assertEquals(listOf(60L, 60L), history.days.map { it.sleepHeartRateBaseline })
    }

    @Test fun `Health today's single day carries no night values - they live only in the history`() {
        val day = healthDay(records, aug2, now, STOCKHOLM)
        assertNull(day.sleepHeartRate)
        assertNull(day.sleepHeartRateBaseline)
        assertNull(day.sleepOxygenSaturation)
    }

    @Test fun `a watch worn only at night gives no baseline and no heart rate warning, but resting heart rate still falls back`() {
        val nightOnly = records.copy(heartRate = records.heartRate.filter { it.time in records.sleep.single() })
        val history = healthHistory(nightOnly, aug1, aug2, STOCKHOLM)
        val night = history.days[1]
        assertEquals(68L, night.sleepHeartRate)
        assertNull("ingen baslinje ur sömnprov", night.sleepHeartRateBaseline)
        assertEquals(listOf(SleepFlag.LOW_OXYGEN_SATURATION), sleepFlagsOn(history, aug2))
        assertEquals("vilopulsen behåller 3.x-fallbacken (HLS-7)", 66L, estimateRestingHeartRate(nightOnly.heartRate, nightOnly.sleep))
    }

    @Test fun `the warnings need no age - low oxygen alone, the heart rate warning only with a baseline`() {
        val history = healthHistory(records, aug1, aug2, STOCKHOLM)
        assertEquals(listOf(SleepFlag.LOW_OXYGEN_SATURATION, SleepFlag.ELEVATED_SLEEPING_HEART_RATE), sleepFlagsOn(history, aug2))
        assertEquals("ingen natt – inga rader", emptyList<SleepFlag>(), sleepFlagsOn(history, aug1))
        val night = SleepMeasurements(timeInBed = 1.hours, sleepingHeartRate = 80, meanOxygenSaturation = 89.9)
        assertEquals(listOf(SleepFlag.LOW_OXYGEN_SATURATION), sleepFlags(night))
        assertEquals(SleepFlag.entries.toList(), sleepFlags(night.copy(baselineRestingHeartRate = 75)))
        assertEquals(emptyList<SleepFlag>(), sleepFlags(night.copy(baselineRestingHeartRate = 76, meanOxygenSaturation = 90.0)))
    }

    @Test fun `only the nights of the period get night values`() {
        val history = healthHistory(records, aug2, aug3, STOCKHOLM)
        assertEquals(listOf(aug2, aug3), history.dates)
        assertEquals(68L, history.days[0].sleepHeartRate)
    }

    @Test fun `many nights are looked up per night, not mixed up`() {
        // Ett år av nätter 23:00–07:00, var och en med en egen sovpuls; proven i omvänd ordning.
        val first = LocalDate(2025, 8, 3)
        val nights = (0 until 365).map { i -> LocalDate.fromEpochDays(first.toEpochDays() + i) }
        val many = HealthRecords(
            heartRate = nights.map { date -> HeartRateSample("watch", at(date, "03:00"), 40L + date.toEpochDays() % 30) }.reversed(),
            sleep = nights.map { date -> SleepSession("watch", at(date, "00:00") - 1.hours, at(date, "07:00")) },
        )
        val history = healthHistory(many, nights.first(), nights.last(), STOCKHOLM)
        assertEquals(nights.map { 40L + it.toEpochDays() % 30 }, history.days.map { it.sleepHeartRate })
    }
}
