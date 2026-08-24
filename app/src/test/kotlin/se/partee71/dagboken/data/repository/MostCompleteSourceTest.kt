package se.partee71.dagboken.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Enhetstest för per-källa-principen i HLS-8: aktiva kalorier och sträcka skrivs av både
 * telefonen och Galaxy Watch via Samsung Health, så värdena får aldrig summeras över källor
 * (dubbelräkning) — samma regel som för steg (HLS-2, se [MostCompleteStepSumTest]).
 *
 * Träningspassen följer **inte** den regeln: de är diskreta händelser och dedupliceras på
 * tidsöverlapp i stället för att en källa väljs för hela dygnet (#220).
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

    // ─── Träningspass: dedup på tidsöverlapp (#220) ───────────────────────────

    /** Ett pass som startar [at] (timme på dygnet) och håller på i [minutes]. */
    private fun session(origin: String, at: String, minutes: Long) = OriginSession(
        origin   = origin,
        start    = Instant.parse("2026-03-10T${at}:00Z"),
        duration = Duration.ofMinutes(minutes),
    )

    @Test fun `mostCompleteExercise returns null without sessions`() {
        assertNull(mostCompleteExercise(emptyList()))
    }

    @Test fun `mostCompleteExercise counts sessions and total time for one source`() {
        val totals = mostCompleteExercise(
            listOf(
                session("watch", "07:00", 30),
                session("watch", "17:00", 45),
            ),
        )
        assertEquals(2, totals!!.sessions)
        assertEquals(Duration.ofMinutes(75), totals.duration)
    }

    @Test fun `the same session written by two sources counts once`() {
        // Samma pass loggat av båda: klockans 45 minuter är den mest kompletta
        // inspelningen och får representera händelsen — aldrig 65 minuter.
        val totals = mostCompleteExercise(
            listOf(
                session("phone", "07:02", 20),
                session("watch", "07:00", 45),
            ),
        )
        assertEquals(1, totals!!.sessions)
        assertEquals(Duration.ofMinutes(45), totals.duration)
    }

    @Test fun `sessions from different sources that do not overlap both count`() {
        // Regression för #220: här kastade det gamla källvalet bort telefonens pass
        // helt, eftersom klockan hade längst sammanlagd tid den dagen.
        val totals = mostCompleteExercise(
            listOf(
                session("watch", "07:00", 45),
                session("phone", "12:00", 25),
            ),
        )
        assertEquals(2, totals!!.sessions)
        assertEquals(Duration.ofMinutes(70), totals.duration)
    }

    @Test fun `a source's other sessions survive when one of them is a duplicate`() {
        // Telefonens 07:05-pass är samma händelse som klockans 07:00, men telefonens
        // eftermiddagspass är en egen händelse och ska räknas.
        val totals = mostCompleteExercise(
            listOf(
                session("watch", "07:00", 45),
                session("phone", "07:05", 30),
                session("phone", "16:00", 20),
            ),
        )
        assertEquals(2, totals!!.sessions)
        assertEquals(Duration.ofMinutes(65), totals.duration)
    }

    @Test fun `back-to-back sessions are separate events`() {
        // Slutar 07:30, nästa börjar 07:30 — ingen överlappning, alltså två pass.
        val totals = mostCompleteExercise(
            listOf(
                session("watch", "07:00", 30),
                session("watch", "07:30", 30),
            ),
        )
        assertEquals(2, totals!!.sessions)
        assertEquals(Duration.ofMinutes(60), totals.duration)
    }

    @Test fun `a chain of overlapping sessions collapses into one event`() {
        // Tre inspelningar som överlappar i kedja hör till samma händelse.
        val totals = mostCompleteExercise(
            listOf(
                session("phone", "07:00", 20),
                session("watch", "07:10", 40),
                session("ring", "07:30", 25),
            ),
        )
        assertEquals(1, totals!!.sessions)
        assertEquals(Duration.ofMinutes(40), totals.duration)
    }

    @Test fun `the order of the input does not matter`() {
        val unordered = listOf(
            session("phone", "16:00", 20),
            session("watch", "07:00", 45),
            session("phone", "07:05", 30),
        )
        val totals = mostCompleteExercise(unordered)
        assertEquals(2, totals!!.sessions)
        assertEquals(Duration.ofMinutes(65), totals.duration)
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
