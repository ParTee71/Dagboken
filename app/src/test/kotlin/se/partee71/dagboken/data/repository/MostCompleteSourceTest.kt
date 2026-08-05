package se.partee71.dagboken.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/**
 * Enhetstest för per-källa-principen i HLS-8: aktiva kalorier, sträcka och
 * träningspass skrivs av både telefonen och Galaxy Watch via Samsung Health, så
 * värdena får aldrig summeras över källor (dubbelräkning) — samma regel som för
 * steg (HLS-2, se [MostCompleteStepSumTest]).
 */
class MostCompleteSourceTest {

    @Test fun `mostCompleteSum returns null for no records`() {
        assertNull(mostCompleteSum(emptyList()))
    }

    @Test fun `mostCompleteSum sums within a source`() {
        val sum = mostCompleteSum(
            listOf(
                OriginAmount("watch", 120.0),
                OriginAmount("watch", 80.5),
            ),
        )
        assertEquals(200.5, sum!!, 0.001)
    }

    @Test fun `mostCompleteSum picks the largest source without adding sources together`() {
        // Telefon 1500 m, klocka 4200 m — 5700 m vore dubbelräkning.
        val sum = mostCompleteSum(
            listOf(
                OriginAmount("phone", 1500.0),
                OriginAmount("watch", 2000.0),
                OriginAmount("watch", 2200.0),
            ),
        )
        assertEquals(4200.0, sum!!, 0.001)
    }

    @Test fun `mostCompleteExercise returns null without sessions`() {
        assertNull(mostCompleteExercise(emptyList()))
    }

    @Test fun `mostCompleteExercise counts sessions and total time for one source`() {
        val totals = mostCompleteExercise(
            listOf(
                OriginSession("watch", Duration.ofMinutes(30)),
                OriginSession("watch", Duration.ofMinutes(45)),
            ),
        )
        assertEquals(2, totals!!.sessions)
        assertEquals(Duration.ofMinutes(75), totals.duration)
    }

    @Test fun `mostCompleteExercise picks the source with the longest total, never merging them`() {
        // Samma pass loggat av båda källorna: 1 pass à 20 min på telefonen,
        // 2 pass à 30+45 min på klockan. Resultatet ska vara klockans, inte 3 pass.
        val totals = mostCompleteExercise(
            listOf(
                OriginSession("phone", Duration.ofMinutes(20)),
                OriginSession("watch", Duration.ofMinutes(30)),
                OriginSession("watch", Duration.ofMinutes(45)),
            ),
        )
        assertEquals(2, totals!!.sessions)
        assertEquals(Duration.ofMinutes(75), totals.duration)
    }

    @Test fun `step sum still works after the shared per-source helper was extracted`() {
        // Regression: mostCompleteStepSum delegerar numera till mostCompleteSum.
        assertEquals(
            8709L,
            mostCompleteStepSum(
                listOf(
                    OriginSteps("phone", 6567L),
                    OriginSteps("watch", 5000L),
                    OriginSteps("watch", 3709L),
                ),
            ),
        )
    }
}
