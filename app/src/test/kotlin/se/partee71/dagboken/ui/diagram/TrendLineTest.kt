package se.partee71.dagboken.ui.diagram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendLineTest {

    @Test fun `strictly increasing series has a positive slope`() {
        val trend = computeTrendLine(listOf(1f, 3f, 5f, 7f, 9f))
        assertTrue("expected slope > 0, was ${trend?.slope}", (trend?.slope ?: 0f) > 0f)
    }

    @Test fun `strictly decreasing series has a negative slope`() {
        val trend = computeTrendLine(listOf(9f, 7f, 5f, 3f, 1f))
        assertTrue("expected slope < 0, was ${trend?.slope}", (trend?.slope ?: 0f) < 0f)
    }

    @Test fun `constant series has a slope of zero`() {
        val trend = computeTrendLine(listOf(5f, 5f, 5f, 5f))
        assertEquals(0f, trend?.slope)
    }

    @Test fun `exact linear series produces the exact expected slope and intercept`() {
        // y = 2x + 2 för x = 0..3
        val trend = computeTrendLine(listOf(2f, 4f, 6f, 8f))
        assertEquals(2f, trend?.slope!!, 0.001f)
        assertEquals(2f, trend.intercept, 0.001f)
    }

    @Test fun `null gaps are skipped without shifting the slope`() {
        val withoutGaps = computeTrendLine(listOf(1f, 2f, 3f, 4f, 5f))
        val withGaps = computeTrendLine(listOf(1f, null, 3f, null, 5f))
        assertEquals(withoutGaps?.slope, withGaps?.slope)
        assertEquals(withoutGaps?.intercept, withGaps?.intercept)
    }

    @Test fun `fewer than two known points produce no trend line`() {
        assertNull(computeTrendLine(emptyList()))
        assertNull(computeTrendLine(listOf(5f)))
        assertNull(computeTrendLine(listOf(null, null, null)))
        assertNull(computeTrendLine(listOf(null, 5f, null)))
    }

    @Test fun `valueAt interpolates correctly at the series endpoints`() {
        val trend = computeTrendLine(listOf(2f, 4f, 6f, 8f))!!
        assertEquals(2f, trend.valueAt(0f), 0.001f)
        assertEquals(8f, trend.valueAt(3f), 0.001f)
    }
}
