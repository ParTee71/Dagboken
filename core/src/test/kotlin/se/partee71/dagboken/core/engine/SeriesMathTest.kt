package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** Sammanfattningen under diagrammen och uppdelningen runt luckor (TRD-9, TRD-15, NFR-14). */
class SeriesMathTest {

    @Test
    fun `sammanfattningen räknar bara kända värden`() {
        val summary = summarize(listOf(4f, null, 8f, 6f, null))!!
        assertEquals(SeriesSummary(count = 3, min = 4f, max = 8f, average = 6f, last = 6f), summary)
    }

    @Test
    fun `en serie med bara luckor har ingen sammanfattning`() {
        assertNull(summarize(listOf(null, null)))
        assertNull(summarize(emptyList()))
        assertEquals(0, knownCount(listOf(null, null)))
    }

    @Test
    fun `luckor delar serien i sammanhängande bitar och blir aldrig nollor`() {
        val runs = gapFreeRuns(listOf(1f, 2f, null, null, 5f, null, 7f, 8f))
        assertEquals(listOf(GapFreeRun(0, listOf(1f, 2f)), GapFreeRun(4, listOf(5f)), GapFreeRun(6, listOf(7f, 8f))), runs)
        assertEquals(listOf(6, 7), runs.last().xs)
    }

    @Test
    fun `serie utan luckor är en bit, bara luckor är inga bitar`() {
        assertEquals(listOf(GapFreeRun(0, listOf(3f, 4f))), gapFreeRuns(listOf(3f, 4f)))
        assertEquals(emptyList(), gapFreeRuns(listOf(null, null)))
    }

    @Test
    fun `nollan är ett värde, inte en lucka`() {
        assertEquals(listOf(GapFreeRun(0, listOf(0f, 0f))), gapFreeRuns(listOf(0f, 0f)))
        assertEquals(0f, summarize(listOf(0f, null))!!.min)
    }

    @Test
    fun `intervallens sammanfattning tar lägsta minimum och högsta maximum som 3x Trender`() {
        val summary = summarizeIntervals(listOf(IntervalPoint(3f, 5f, 7f), null, IntervalPoint(2f, 4f, 9f)))!!
        assertEquals(2f, summary.min)
        assertEquals(9f, summary.max)
        assertEquals(4.5f, summary.average)
        assertEquals(4f, summary.last)
        assertEquals(2, summary.count)
        assertNull(summarizeIntervals(listOf(null)))
    }
}
