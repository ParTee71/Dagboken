package se.partee71.dagboken.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.model.Sex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import se.partee71.dagboken.core.engine.health.midpointSdByNight
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime

/**
 * Enhetstest för sömnkvalitet **per natt** (HLS-13) — den rullande regelbundenheten och
 * poängsättningen bakåt i tiden.
 */
class NightlySleepHistoryTest {

    private val window = 14

    private fun midnightish(offsetMinutes: Long): LocalTime =
        LocalTime.fromSecondOfDay(Math.floorMod(3 * 3600 + offsetMinutes.toInt() * 60, 86_400))

    // ─── Rullande regelbundenhet ──────────────────────────────────────────────

    private val night0 = LocalDate(2026, 3, 1)

    /** [midpoints] som nätter i följd från [night0], genom `midpointSdByNight` – i följd är dygn och nätter samma fönster. */
    private fun rolling(midpoints: List<LocalTime>, days: Int = REGULARITY_WINDOW_NIGHTS): List<Double?> =
        midpointSdByNight(midpoints.mapIndexed { i, time -> night0.plus(DatePeriod(days = i)) to time }.toMap(), days).values.toList()

    @Test fun `the first nights have no regularity until the window has enough of them`() {
        val sd = rolling(List(6) { midnightish(0) }, window)
        // MIN_NIGHTS_FOR_REGULARITY = 4 → de tre första fönstren är för korta.
        assertNull(sd[0])
        assertNull(sd[1])
        assertNull(sd[2])
        assertNotNull(sd[3])
    }

    @Test fun `identical midpoints give zero spread`() {
        val sd = rolling(List(8) { midnightish(0) }, window)
        assertEquals(0.0, sd.last()!!, 0.001)
    }

    @Test fun `each night is judged against its own window, not the whole period`() {
        // Fyra spretiga nätter följt av fjorton identiska: den sista natten ska bedömas
        // som regelbunden, trots att perioden som helhet inte är det.
        val midpoints = listOf(-180L, 200L, -150L, 240L).map { midnightish(it) } +
            List(window) { midnightish(0) }
        val sd = rolling(midpoints, window)

        assertTrue("Den spretiga inledningen ska ge stor spridning", sd[3]!! > 60.0)
        assertEquals("Sista fönstret är fjorton identiska nätter", 0.0, sd.last()!!, 0.001)
    }

    @Test fun `the window never looks further back than its length`() {
        val midpoints = List(window) { midnightish(0) } + listOf(midnightish(300))
        val sd = rolling(midpoints, window)
        // Sista fönstret innehåller tretton identiska nätter plus den avvikande.
        assertTrue(sd.last()!! > 0.0)
    }

    @Test fun `an empty list gives no values`() {
        assertTrue(rolling(emptyList(), window).isEmpty())
    }

    // ─── Poäng per natt ───────────────────────────────────────────────────────

    private fun night(date: LocalDate, hours: Long, awakeMinutes: Long = 20) = NightlySleepMeasurements(
        date = date,
        measurements = SleepMeasurements(
            timeInBed = hours.hours,
            awake = awakeMinutes.minutes,
            midpointSdMinutes = 15.0,
        ),
    )

    private val day: LocalDate = LocalDate(2026, 3, 10)

    @Test fun `every night gets its own score`() {
        val scored = scoreNightlySleep(
            listOf(night(day, hours = 8), night(day.plus(DatePeriod(days = 1)), hours = 5)),
            age = 45,
            sex = Sex.MALE,
        )
        assertEquals(2, scored.size)
        assertEquals(day, scored[0].date)
        assertNotNull(scored[0].quality)
        assertTrue(
            "En åtta timmars natt ska få högre poäng än en femtimmarsnatt",
            scored[0].quality!!.score > scored[1].quality!!.score,
        )
    }

    @Test fun `no birth year means no score at all`() {
        val scored = scoreNightlySleep(listOf(night(day, hours = 8)), age = null, sex = Sex.MALE)
        // Poängen är åldersjusterad (HLS-11) — en poäng mot fel norm vore missvisande.
        assertEquals(1, scored.size)
        assertNull(scored[0].quality)
        assertEquals(day, scored[0].date)
    }

    @Test fun `a night that cannot be scored gives a gap, not a zero`() {
        val unscorable = NightlySleepMeasurements(
            date = day,
            measurements = SleepMeasurements(timeInBed = Duration.ZERO),
        )
        val scored = scoreNightlySleep(listOf(unscorable), age = 45, sex = Sex.MALE)
        assertNull(scored[0].quality)
    }

    @Test fun `dates are preserved in order`() {
        val nights = (0L..3L).map { night(day.plus(DatePeriod(days = it.toInt())), hours = 7) }
        val scored = scoreNightlySleep(nights, age = 30, sex = Sex.UNSPECIFIED)
        assertEquals(nights.map { it.date }, scored.map { it.date })
    }

    @Test fun `the default window is the fourteen days of HLS-10`() {
        val midpoints = List(window) { midnightish(0) } + listOf(midnightish(300))
        assertEquals(rolling(midpoints, window), rolling(midpoints))
    }

    @Test fun `the window counts days, not nights - a gap leaves older nights outside`() {
        // Fyra nätter, elva dygn utan klocka, sedan en natt: dess fönster (14 dygn) når bara de två senaste av de fyra.
        val dates = listOf(0, 1, 2, 3, 15).map { night0.plus(DatePeriod(days = it)) }
        val sd = midpointSdByNight(dates.associateWith { midnightish(0) })
        assertNotNull(sd[dates[3]])
        assertNull("bara tre nätter inom fjorton dygn", sd[dates[4]])
    }

    @Test fun `the sliding window gives the same spread as recounting every window`() {
        val dates = (0 until 60).filter { it % 7 != 3 }.map { night0.plus(DatePeriod(days = it)) }
        val midpoints = dates.mapIndexed { i, date -> date to midnightish(((i * 37) % 120 - 60).toLong()) }.toMap()
        val sliding = midpointSdByNight(midpoints)
        midpoints.keys.forEach { night ->
            val first = night.minus(DatePeriod(days = REGULARITY_WINDOW_NIGHTS - 1))
            val expected = sleepMidpointSdMinutes(midpoints.filterKeys { it in first..night }.values.toList())
            if (expected == null) assertNull(sliding[night]) else assertEquals(expected, sliding[night]!!, 1e-6)
        }
    }
}
