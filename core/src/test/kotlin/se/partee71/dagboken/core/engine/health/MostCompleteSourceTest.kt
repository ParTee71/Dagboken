package se.partee71.dagboken.core.engine.health

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Per-källa-principen för aktiva kalorier och sträcka (HLS-8) – aldrig en summa över källor – och
 * träningspassens dedup på tidsöverlapp (#220). Portad från 3.x `MostCompleteSourceTest`.
 */
class MostCompleteSourceTest {

    private val t = Instant.parse("2026-03-10T08:00:00Z")
    private fun meters(origin: String, amount: Double) = DistanceSample(origin, t, t, amount)

    @Test fun `mostCompleteSum returns null for no records`() {
        assertNull(mostCompleteSum(emptyList()))
    }

    @Test fun `a zero sum is a gap, not zero`() {
        // HLS-12: ett dygn med bara nollposter för kalorier eller sträcka är ingen mätning.
        assertNull(mostCompleteSum(listOf(CaloriesSample("watch", t, t, 0.0), meters("phone", 0.0))))
    }

    @Test fun `mostCompleteSum sums within a source`() {
        val sum = mostCompleteSum(listOf(CaloriesSample("watch", t, t, 120.0), CaloriesSample("watch", t, t, 80.5)))
        assertEquals(200.5, sum!!, 0.001)
    }

    @Test fun `mostCompleteSum picks the largest source without adding sources together`() {
        // Telefon 1500 m, klocka 4200 m – 5700 m vore dubbelräkning.
        val sum = mostCompleteSum(listOf(meters("phone", 1500.0), meters("watch", 2000.0), meters("watch", 2200.0)))
        assertEquals(4200.0, sum!!, 0.001)
    }

    @Test fun `two sources that do not overlap in time still never add up`() {
        // Telefonen på förmiddagen, klockan på eftermiddagen: summorna jämförs, de läggs aldrig ihop (HLS-2).
        val morning = Instant.parse("2026-03-10T08:00:00Z")
        val afternoon = Instant.parse("2026-03-10T15:00:00Z")
        val sum = mostCompleteSum(
            listOf(
                StepSample("phone", morning, morning + 60.minutes, 3000),
                StepSample("watch", afternoon, afternoon + 60.minutes, 5000),
            ),
        )
        assertEquals(5000.0, sum!!, 0.001)
    }

    // ─── Träningspass: dedup på tidsöverlapp (#220) ───────────────────────────

    /** Ett pass som startar [at] (UTC-klockslag) och håller på i [minutes]. */
    private fun session(origin: String, at: String, minutes: Long): ExerciseSession {
        val start = Instant.parse("2026-03-10T$at:00Z")
        return ExerciseSession(origin, start, start + minutes.minutes)
    }

    @Test fun `mostCompleteExercise returns null without sessions`() {
        assertNull(mostCompleteExercise(emptyList()))
    }

    @Test fun `mostCompleteExercise counts sessions and total time for one source`() {
        val totals = mostCompleteExercise(listOf(session("watch", "07:00", 30), session("watch", "17:00", 45)))
        assertEquals(2, totals!!.sessions)
        assertEquals(75.minutes, totals.duration)
    }

    @Test fun `the same session written by two sources counts once`() {
        // Klockans 45 minuter representerar händelsen – aldrig 65 minuter.
        val totals = mostCompleteExercise(listOf(session("phone", "07:02", 20), session("watch", "07:00", 45)))
        assertEquals(1, totals!!.sessions)
        assertEquals(45.minutes, totals.duration)
    }

    @Test fun `sessions from different sources that do not overlap both count`() {
        // Regression för #220: källvalet kastade bort telefonens pass helt.
        val totals = mostCompleteExercise(listOf(session("watch", "07:00", 45), session("phone", "12:00", 25)))
        assertEquals(2, totals!!.sessions)
        assertEquals(70.minutes, totals.duration)
    }

    @Test fun `a source's other sessions survive when one of them is a duplicate`() {
        val totals = mostCompleteExercise(
            listOf(session("watch", "07:00", 45), session("phone", "07:05", 30), session("phone", "16:00", 20)),
        )
        assertEquals(2, totals!!.sessions)
        assertEquals(65.minutes, totals.duration)
    }

    @Test fun `back-to-back sessions are separate events`() {
        val totals = mostCompleteExercise(listOf(session("watch", "07:00", 30), session("watch", "07:30", 30)))
        assertEquals(2, totals!!.sessions)
        assertEquals(60.minutes, totals.duration)
    }

    @Test fun `a chain of overlapping sessions collapses into one event`() {
        val totals = mostCompleteExercise(
            listOf(session("phone", "07:00", 20), session("watch", "07:10", 40), session("ring", "07:30", 25)),
        )
        assertEquals(1, totals!!.sessions)
        assertEquals(40.minutes, totals.duration)
    }

    @Test fun `the order of the input does not matter`() {
        val totals = mostCompleteExercise(
            listOf(session("phone", "16:00", 20), session("watch", "07:00", 45), session("phone", "07:05", 30)),
        )
        assertEquals(2, totals!!.sessions)
        assertEquals(65.minutes, totals.duration)
    }

    @Test fun `step sum uses the shared per-source helper`() {
        val steps = listOf(StepSample("phone", t, t, 6567L), StepSample("watch", t, t, 5000L), StepSample("watch", t, t, 3709L))
        assertEquals(8709L, mostCompleteSteps(steps))
    }
}
