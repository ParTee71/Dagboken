package se.partee71.dagboken.core.engine.health

import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [mostCompleteSteps] – den mest kompletta stegkällan när telefonen och klockan båda skrivit steg (HLS-2).
 * Portad från 3.x `MostCompleteStepSumTest`; tomt ger här en lucka (`null`) i stället för 3.x 0, som
 * 3.x ändå gjorde om till "saknas" innan värdet visades (HLS-12).
 */
class MostCompleteStepSumTest {

    private val t = Instant.parse("2026-03-10T08:00:00Z")
    private fun steps(origin: String, count: Long) = StepSample(origin, t, t, count)

    @Test fun `no records is a gap, not zero`() {
        assertNull(mostCompleteSteps(emptyList()))
    }

    @Test fun `sums a single source`() {
        assertEquals(6567L, mostCompleteSteps(listOf(steps("phone", 4000L), steps("phone", 2567L))))
    }

    @Test fun `picks the most complete source instead of de-duplicating across sources`() {
        // Telefon 6567, klocka 8709 – klockans fullständigare dagssumma, aldrig 6567 + 8709.
        val records = listOf(steps("phone", 6567L), steps("watch", 5000L), steps("watch", 3709L))
        assertEquals(8709L, mostCompleteSteps(records))
    }

    @Test fun `zero steps is a gap`() {
        assertNull(mostCompleteSteps(listOf(steps("phone", 0L))))
    }
}
