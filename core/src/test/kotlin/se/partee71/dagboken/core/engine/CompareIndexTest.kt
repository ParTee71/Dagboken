package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.SleepStages
import se.partee71.dagboken.core.model.SymptomScore

/**
 * Jämför (TRD-17): varje serie 0–100 mot sitt eget min/max, konstant serie som mittlinje, luckor aldrig nollor,
 * en serie utan värden faller bort, nycklarna går fram och tillbaka, och enheterna följer med till legenden.
 */
class CompareIndexTest {

    private val d1 = LocalDate(2026, 10, 1)
    private val d2 = LocalDate(2026, 10, 2)
    private val d3 = LocalDate(2026, 10, 3)
    private val days = listOf(d1, d2, d3)

    @Test
    fun `serien indexeras 0–100 mot sitt eget lägsta och högsta – luckor förblir luckor`() {
        assertEquals(listOf(0f, null, 50f, 100f), indexSeries(listOf(2_000f, null, 6_000f, 10_000f)))
        assertEquals(listOf(100f, 0f), indexSeries(listOf(9f, 3f)))
    }

    @Test
    fun `en konstant serie blir mittlinjen 50, en tom serie förblir tom`() {
        assertEquals(listOf(50f, null, 50f), indexSeries(listOf(7f, null, 7f)))
        assertEquals(listOf(50f), indexSeries(listOf(0f)), "ett enda värde är också konstant")
        assertEquals(listOf(null, null), indexSeries(listOf(null, null)))
        assertEquals(emptyList(), indexSeries(emptyList()))
    }

    @Test
    fun `jämförda serier bär sitt verkliga min och max, och en serie utan värden faller bort`() {
        val compared = compareSeries(
            listOf(
                TrendSerie("steps", listOf(2_000f, null, 10_000f)),
                TrendSerie("energy", listOf(null, null, null)),
                TrendSerie("stress", listOf(4f, 4f, 4f)),
            ),
        )
        assertEquals(listOf("steps", "stress"), compared.map { it.key })
        assertEquals(ComparedSerie("steps", listOf(0f, null, 100f), 2_000f, 10_000f), compared[0])
        assertEquals(ComparedSerie("stress", listOf(50f, 50f, 50f), 4f, 4f), compared[1])
        assertTrue(compareSeries(listOf(TrendSerie("steps", listOf(2_000f, null, 10_000f)))).size < 2, "en serie räcker inte – skärmen visar tomt läge")
    }

    @Test
    fun `axeln är fast 0–100 i steg om 25`() {
        assertEquals(0f..100f, COMPARE_AXIS.range)
        assertEquals(25f, COMPARE_AXIS.step)
        assertEquals(listOf(0f, 25f, 50f, 75f, 100f), gridValuesFor(COMPARE_AXIS.range.start, COMPARE_AXIS.range.endInclusive, COMPARE_AXIS.step))
    }

    @Test
    fun `nycklarna går fram och tillbaka och bär sin enhet – skalan för dagboken, måttets för klockan`() {
        val keys = listOf(
            CompareKey.EnergyDay,
            CompareKey.EnergyOccasion(Occasion.BREAKFAST),
            CompareKey.Stress(StressSeries.DRAIN),
            CompareKey.Symptom("yrsel"),
            CompareKey.Watch(WatchMetric.STEPS),
            CompareKey.SleepQuality,
        )
        keys.forEach { key -> assertEquals(key, CompareKey.parse(key.wire), key.wire) }
        assertEquals(listOf(WatchUnit.SCALE, WatchUnit.SCALE, WatchUnit.SCALE, WatchUnit.SCALE, WatchUnit.STEPS, WatchUnit.POINTS), keys.map { it.unit })
        assertEquals(listOf(false, false, false, false, true, true), keys.map { it.fromWatch })
        assertEquals(WatchUnit.BPM, CompareKey.Watch(WatchMetric.RESTING_HEART_RATE).unit)
        assertEquals(WatchUnit.HOURS, CompareKey.Watch(WatchMetric.SLEEP_DEEP).unit)
        listOf("", "watch:", "watch:UNKNOWN", "occasion:middag", "stress:LUGN", "symptom:", "okänd", "sleepQuality:x").forEach { assertNull(CompareKey.parse(it), it) }
        assertEquals(11, WATCH_COMPARE_KEYS.size)
        assertFalse(WATCH_COMPARE_KEYS.any { it is CompareKey.Watch && it.metric in setOf(WatchMetric.SLEEP_LIGHT, WatchMetric.SLEEP_AWAKE) }, "lätt sömn och vaken tid jämförs inte")
        assertEquals(WATCH_COMPARE_KEYS.size, WATCH_COMPARE_KEYS.map { it.wire }.distinct().size)
    }

    @Test
    fun `dagbokens serier för Jämför är samma uträkningar som Måendes kort, med Jämför-nycklar`() {
        val screenings = listOf(
            Screening("s1", d1, LocalTime(8, 0), Occasion.BREAKFAST, energy = 4, stress = 2, symptoms = listOf(SymptomScore("yrsel", 6))),
            Screening("s2", d1, LocalTime(12, 0), Occasion.LUNCH, energy = 8, stress = 4),
            Screening("s3", d3, LocalTime(8, 0), Occasion.BREAKFAST, energy = 6, stress = 6),
        )
        val activities = listOf(Activity("a1", d3, LocalTime(15, 0), optionId = "promenad", stress = 2, recovering = true))
        val series = moodCompareSeries(screenings, activities, days).associateBy { it.key }

        assertEquals(1 + Occasion.entries.size + StressSeries.entries.size + 1, series.size)
        assertEquals(dailyEnergyAverages(screenings, days), series.getValue(CompareKey.EnergyDay.wire).points)
        assertEquals(listOf(6f, null, 6f), series.getValue(CompareKey.EnergyDay.wire).points)
        assertEquals(listOf(4f, null, 6f), series.getValue(CompareKey.EnergyOccasion(Occasion.BREAKFAST).wire).points)
        assertEquals(listOf(3f, null, 4f), series.getValue(CompareKey.Stress(StressSeries.STRESS).wire).points)
        assertEquals(listOf(0f, null, 5f), series.getValue(CompareKey.Stress(StressSeries.RECOVERING).wire).points)
        assertEquals(listOf(6f, null, null), series.getValue(CompareKey.Symptom("yrsel").wire).points)
    }

    @Test
    fun `klockans serier för Jämför följer menyns ordning och sömnkvaliteten är poängen`() {
        val night = DailyHealth(d2, steps = 5_000, sleepDuration = 8.hours, sleepStages = SleepStages(deep = 1.hours, rem = 2.hours, light = 4.hours, awake = 1.hours))
        val history = HealthHistory.of(d1, d3, mapOf(d2 to night))
        val series = watchCompareSeries(history, age = 40, sex = Sex.FEMALE, days = days)

        assertEquals(WATCH_COMPARE_KEYS.map { it.wire }, series.map { it.key })
        val two = watchCompareSeries(history, 40, Sex.FEMALE, days, listOf(CompareKey.Watch(WatchMetric.DISTANCE), CompareKey.Watch(WatchMetric.STEPS)))
        assertEquals(listOf(CompareKey.Watch(WatchMetric.STEPS).wire, CompareKey.Watch(WatchMetric.DISTANCE).wire), two.map { it.key }, "bara de valda, i menyns ordning")
        assertEquals(emptyList(), watchCompareSeries(history, 40, Sex.FEMALE, days, emptyList()))
        assertEquals(listOf(null, 5_000f, null), series.first { it.key == CompareKey.Watch(WatchMetric.STEPS).wire }.points)
        assertEquals(listOf(null, 8f, null), series.first { it.key == CompareKey.Watch(WatchMetric.SLEEP_TOTAL).wire }.points)
        val score = sleepQualitySeries(history, 40, Sex.FEMALE, days).first { it.key == SLEEP_SCORE_KEY }.points
        assertEquals(score, series.first { it.key == CompareKey.SleepQuality.wire }.points)
        assertTrue(score[1] != null)
    }
}
