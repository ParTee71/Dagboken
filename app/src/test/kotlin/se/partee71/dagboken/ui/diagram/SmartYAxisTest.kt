package se.partee71.dagboken.ui.diagram

import org.junit.Assert.assertTrue
import org.junit.Test

class SmartYAxisTest {

    @Test fun `empty values fall back to symmetric default range`() {
        val range = computeSmartYRange(emptyList())
        assertTrue(range == -10f..10f)
    }

    @Test fun `narrow band high above zero does not anchor at zero`() {
        // symptomgradering/energiband 5..8 — ska inte klämmas in mot 0
        val range = computeSmartYRange(listOf(5f, 6f, 8f, 7f))
        assertTrue("expected min > 0, was ${range.start}", range.start > 0f)
        assertTrue("expected max close to data max", range.endInclusive in 8f..9f)
    }

    @Test fun `range covers all input values`() {
        val values = listOf(5f, 6f, 8f, 7f)
        val range = computeSmartYRange(values)
        values.forEach { assertTrue("$it should be within $range", it in range) }
    }

    @Test fun `values far from zero produce a tight non-zero-anchored range`() {
        // stegtrend-liknande värden, ~5000-9000
        val range = computeSmartYRange(listOf(5200f, 8800f, 6400f, 9100f))
        assertTrue("expected min > 1000, was ${range.start}", range.start > 1000f)
        assertTrue("expected max < 10000, was ${range.endInclusive}", range.endInclusive < 10000f)
    }

    @Test fun `resting heart rate band rounds to a tight readable range`() {
        val range = computeSmartYRange(listOf(58f, 61f, 55f, 65f))
        assertTrue("expected min > 40, was ${range.start}", range.start > 40f)
        assertTrue("expected max < 80, was ${range.endInclusive}", range.endInclusive < 80f)
    }

    @Test fun `single distinct value produces a small symmetric range around it`() {
        val range = computeSmartYRange(listOf(5f, 5f, 5f))
        assertTrue(5f in range)
        assertTrue("range should not collapse to a point", range.endInclusive > range.start)
    }

    @Test fun `single value of zero produces a small symmetric range around zero`() {
        val range = computeSmartYRange(listOf(0f, 0f))
        assertTrue(0f in range)
        assertTrue(range.start < 0f)
        assertTrue(range.endInclusive > 0f)
    }

    @Test fun `negative values produce a range that does not force zero in`() {
        val range = computeSmartYRange(listOf(-8f, -5f, -3f))
        assertTrue("expected max < 0, was ${range.endInclusive}", range.endInclusive < 0f)
    }

    @Test fun `single point list produces a non-degenerate range`() {
        val range = computeSmartYRange(listOf(42f))
        assertTrue(42f in range)
        assertTrue(range.endInclusive > range.start)
    }
}
