package se.partee71.dagboken.core.engine.health

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import se.partee71.dagboken.core.engine.SLEEP_SCORE_KEY
import se.partee71.dagboken.core.engine.scoreSleepQuality
import se.partee71.dagboken.core.engine.sleepMeasurements
import se.partee71.dagboken.core.engine.sleepScoreOn
import se.partee71.dagboken.core.time.shownDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import se.partee71.dagboken.core.engine.sleepQualitySeries
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.SleepStages

/** Hälsa idag och Idags veckotrender (HEM-14, HEM-15, HEM-17, HLS-8, HLS-10, HLS-11, HLS-13). */
class HealthTodayTest {

    private val date = LocalDate(2026, 10, 6)
    private val night = DailyHealth(
        date,
        sleepDuration = 7.hours + 12.minutes,
        sleepStages = SleepStages(deep = 1.hours + 12.minutes, rem = 1.hours + 35.minutes, light = 3.hours + 48.minutes, awake = 37.minutes),
    )

    @Test fun `en trendrad kräver två dagar med värde – annars utelämnas den`() {
        assertNull(trendOrNull(emptyList()))
        assertNull(trendOrNull(listOf(null, 5f, null)))
        assertEquals(listOf(null, 5f, 6f), trendOrNull(listOf(null, 5f, 6f)))
    }

    @Test fun `nattens underlag är sömnlängden och stadierna – utan sömn inget underlag`() {
        val measured = night.sleepMeasurements()!!
        assertEquals(7.hours + 12.minutes, measured.timeInBed)
        assertEquals(37.minutes, measured.awake)
        assertEquals(1.hours + 12.minutes, measured.deep)
        assertEquals(1.hours + 35.minutes, measured.rem)
        assertNull(DailyHealth(date, steps = 7_842).sleepMeasurements())
    }

    @Test fun `sömnpoängen idag är Trenders poäng för samma natt – sista punkten i samma serie (HLS-10, HLS-13)`() {
        val earlier = (1..13).map { back ->
            val d = date.minus(back, DateTimeUnit.DAY)
            DailyHealth(d, sleepDuration = (6 + back % 3).hours, sleepStages = SleepStages(deep = 1.hours, rem = 80.minutes, awake = (20 + back).minutes))
        }
        val history = HealthHistory((earlier.reversed() + night))
        val score = sleepScoreOn(history, date, age = 55, sex = Sex.FEMALE)
        val trend = sleepQualitySeries(history, 55, Sex.FEMALE, history.dates, listOf(SLEEP_SCORE_KEY)).single().points.last()
        assertEquals(trend, score?.toFloat())
        assertEquals(scoreSleepQuality(night.sleepMeasurements()!!, 55, Sex.FEMALE)!!.score, score)
    }

    @Test fun `utan födelseår eller utan sömn ingen poäng (HLS-11)`() {
        assertNull(sleepScoreOn(HealthHistory(listOf(night)), date, age = null, sex = Sex.MALE))
        assertNull(sleepScoreOn(HealthHistory(listOf(DailyHealth(date, steps = 7_842))), date, age = 40, sex = Sex.MALE))
        assertNull("natten saknas i historiken", sleepScoreOn(HealthHistory(), date, age = 40, sex = Sex.MALE))
    }

    @Test fun `den visade dagen är den valda, idag utan val, aldrig efter idag (HEM-14)`() {
        assertEquals(date, shownDate(null, date))
        assertEquals(date.minus(2, DateTimeUnit.DAY), shownDate(date.minus(2, DateTimeUnit.DAY), date))
        assertEquals(date, shownDate(date.plus(1, DateTimeUnit.DAY), date))
    }
}
