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

    @Test
    fun `seriesStats ger samma sammanfattning och trend som de enskilda funktionerna`() {
        val points = listOf(null, 2f, 4f, null, 8f, null)
        val stats = seriesStats(points)
        assertEquals(summarize(points), stats.summary)
        assertEquals(trendSegment(points), stats.segment)
        assertEquals(computeTrendLine(points), stats.trend)
        assertEquals(TrendDirection.RISING, stats.direction)
    }

    @Test
    fun `seriesStats med för få punkter har sammanfattning men ingen trend`() {
        val stats = seriesStats(listOf(null, 3f, null))
        assertEquals(SeriesSummary(count = 1, min = 3f, max = 3f, average = 3f, last = 3f), stats.summary)
        assertNull(stats.segment)
        assertNull(stats.trend)
        assertNull(stats.direction)
        assertEquals(SeriesStats(null, null), seriesStats(emptyList()))
    }

    @Test
    fun `seriesStats tar en egen sammanfattning, trenden räknas på värdena`() {
        val intervals = listOf(IntervalPoint(1f, 3f, 5f), null, IntervalPoint(4f, 6f, 9f))
        val stats = seriesStats(intervals.map { it?.value }, summarizeIntervals(intervals))
        assertEquals(SeriesSummary(count = 2, min = 1f, max = 9f, average = 4.5f, last = 6f), stats.summary)
        assertEquals(TrendSegment(0, 3f, 2, 6f, computeTrendLine(listOf(3f, null, 6f))!!), stats.segment)
        assertEquals(TrendDirection.RISING, stats.direction)
    }
}
