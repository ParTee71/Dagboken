package se.partee71.dagboken.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Enhetstest för [mostCompleteStepSum] — väljer den mest kompletta stegkällan när
 * flera källor (t.ex. telefon + Galaxy Watch) skrivit steg till Health Connect
 * (HLS-2, §19). Summerar per källa och tar den högsta, utan att dubbelräkna.
 */
class MostCompleteStepSumTest {

    @Test fun `returns 0 for no records`() {
        assertEquals(0L, mostCompleteStepSum(emptyList()))
    }

    @Test fun `sums a single source`() {
        assertEquals(
            6567L,
            mostCompleteStepSum(
                listOf(
                    OriginSteps("phone", 4000L),
                    OriginSteps("phone", 2567L),
                ),
            ),
        )
    }

    @Test fun `picks the most complete source instead of de-duplicating across sources`() {
        // Telefon 6567, klocka 8709 — ta klockans fullständigare dagssumma, summera
        // aldrig över källor (ingen dubbelräkning: 6567+8709 vore fel).
        val records = listOf(
            OriginSteps("phone", 6567L),
            OriginSteps("watch", 5000L),
            OriginSteps("watch", 3709L),
        )
        assertEquals(8709L, mostCompleteStepSum(records))
    }
}
