package se.partee71.dagboken.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Enhetstest för sömnkvalitet **per natt** (HLS-13) — den rullande regelbundenheten och
 * poängsättningen bakåt i tiden.
 */
class NightlySleepHistoryTest {

    private val window = 14

    private fun midnightish(offsetMinutes: Long): LocalTime =
        LocalTime.of(3, 0).plusMinutes(offsetMinutes)

    // ─── Rullande regelbundenhet ──────────────────────────────────────────────

    @Test fun `the first nights have no regularity until the window has enough of them`() {
        val sd = rollingMidpointSdMinutes(List(6) { midnightish(0) }, window)
        // MIN_NIGHTS_FOR_REGULARITY = 4 → de tre första fönstren är för korta.
        assertNull(sd[0])
        assertNull(sd[1])
        assertNull(sd[2])
        assertNotNull(sd[3])
    }

    @Test fun `identical midpoints give zero spread`() {
        val sd = rollingMidpointSdMinutes(List(8) { midnightish(0) }, window)
        assertEquals(0.0, sd.last()!!, 0.001)
    }

    @Test fun `each night is judged against its own window, not the whole period`() {
        // Fyra spretiga nätter följt av fjorton identiska: den sista natten ska bedömas
        // som regelbunden, trots att perioden som helhet inte är det.
        val midpoints = listOf(-180L, 200L, -150L, 240L).map { midnightish(it) } +
            List(window) { midnightish(0) }
        val sd = rollingMidpointSdMinutes(midpoints, window)

        assertTrue("Den spretiga inledningen ska ge stor spridning", sd[3]!! > 60.0)
        assertEquals("Sista fönstret är fjorton identiska nätter", 0.0, sd.last()!!, 0.001)
    }

    @Test fun `the window never looks further back than its length`() {
        val midpoints = List(window) { midnightish(0) } + listOf(midnightish(300))
        val sd = rollingMidpointSdMinutes(midpoints, window)
        // Sista fönstret innehåller tretton identiska nätter plus den avvikande.
        assertTrue(sd.last()!! > 0.0)
    }

    @Test fun `an empty list gives no values`() {
        assertTrue(rollingMidpointSdMinutes(emptyList(), window).isEmpty())
    }

    // ─── Poäng per natt ───────────────────────────────────────────────────────

    private fun night(date: LocalDate, hours: Long, awakeMinutes: Long = 20) = NightlySleepMeasurements(
        date = date,
        measurements = SleepMeasurements(
            timeInBed = Duration.ofHours(hours),
            awake = Duration.ofMinutes(awakeMinutes),
            midpointSdMinutes = 15.0,
        ),
    )

    private val day: LocalDate = LocalDate.of(2026, 3, 10)

    @Test fun `every night gets its own score`() {
        val scored = scoreNightlySleep(
            listOf(night(day, hours = 8), night(day.plusDays(1), hours = 5)),
            age = 45,
            sex = Sex.MAN,
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
        val scored = scoreNightlySleep(listOf(night(day, hours = 8)), age = null, sex = Sex.MAN)
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
        val scored = scoreNightlySleep(listOf(unscorable), age = 45, sex = Sex.MAN)
        assertNull(scored[0].quality)
    }

    @Test fun `dates are preserved in order`() {
        val nights = (0L..3L).map { night(day.plusDays(it), hours = 7) }
        val scored = scoreNightlySleep(nights, age = 30, sex = Sex.EJ_ANGIVET)
        assertEquals(nights.map { it.date }, scored.map { it.date })
    }
}
