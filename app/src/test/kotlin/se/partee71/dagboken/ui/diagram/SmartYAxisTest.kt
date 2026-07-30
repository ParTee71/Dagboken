package se.partee71.dagboken.ui.diagram

import org.junit.Assert.assertEquals
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

    // ─── computeSmartYAxis step — #141, värdelinjer i IntervalBarChart ────────

    @Test fun `computeSmartYAxis range matches computeSmartYRange for the same input`() {
        val values = listOf(5f, 6f, 8f, 7f)
        assertTrue(computeSmartYAxis(values).range == computeSmartYRange(values))
    }

    @Test fun `step is positive and range span is a whole multiple of step`() {
        val values = listOf(5200f, 8800f, 6400f, 9100f)
        val axis = computeSmartYAxis(values)
        assertTrue("step should be positive, was ${axis.step}", axis.step > 0f)
        val span = axis.range.endInclusive - axis.range.start
        val multiples = span / axis.step
        val nearestWhole = Math.round(multiples)
        assertTrue(
            "span $span should be a whole multiple of step ${axis.step}, got $multiples",
            Math.abs(multiples - nearestWhole) < 0.01f,
        )
    }

    @Test fun `empty values fall back to the default step`() {
        val axis = computeSmartYAxis(emptyList())
        assertTrue(axis.step > 0f)
        assertTrue(axis.range == -10f..10f)
    }

    // ─── Heltaliga y-axlar (#170) ──────────────────────────────────────────────

    private val bands = listOf(
        listOf(5f, 6f, 8f, 7f), // symptomband/energiband 5..8
        listOf(58f, 61f, 55f, 65f), // vilopuls
        listOf(5200f, 8800f, 6400f, 9100f), // stegtrend
        listOf(5f, 5f, 5f), // enda distinkta värdet
        listOf(0f, 0f),
        listOf(-8f, -5f, -3f),
        listOf(42f),
    )

    @Test fun `step is always a whole number and at least 1`() {
        bands.forEach { values ->
            val axis = computeSmartYAxis(values)
            assertTrue(
                "step for $values should be >= 1, was ${axis.step}",
                axis.step >= 1f,
            )
            assertEquals("step for $values should be a whole number", axis.step, Math.round(axis.step).toFloat())
        }
    }

    @Test fun `range endpoints are always whole numbers`() {
        bands.forEach { values ->
            val axis = computeSmartYAxis(values)
            assertEquals(
                "range.start for $values should be a whole number",
                axis.range.start,
                Math.round(axis.range.start).toFloat(),
            )
            assertEquals(
                "range.endInclusive for $values should be a whole number",
                axis.range.endInclusive,
                Math.round(axis.range.endInclusive).toFloat(),
            )
        }
    }

    @Test fun `step is a 1, 2 or 5 times a power of ten`() {
        bands.forEach { values ->
            val axis = computeSmartYAxis(values)
            val magnitude = Math.pow(10.0, Math.floor(Math.log10(axis.step.toDouble())))
            val normalized = axis.step / magnitude
            assertTrue(
                "step ${axis.step} for $values should normalize to 1, 2, 5 or 10, was $normalized",
                listOf(1.0, 2.0, 5.0, 10.0).any { Math.abs(normalized - it) < 0.01 },
            )
        }
    }

    @Test fun `grid line count stays within the max for narrow and wide spans`() {
        bands.forEach { values ->
            val axis = computeSmartYAxis(values)
            val values2 = gridValuesFor(axis.range.start, axis.range.endInclusive, axis.step)
            assertTrue(
                "grid line count for $values was ${values2.size}",
                values2.size <= 12,
            )
        }
    }

    @Test fun `symptom band 5 to 8 no longer produces a half-step axis`() {
        // regression för #136/#141: innan #170 gav detta 4.5..8.5 med steg 0.5.
        val axis = computeSmartYAxis(listOf(5f, 6f, 8f, 7f))
        assertTrue("expected step >= 1, was ${axis.step}", axis.step >= 1f)
        assertEquals(axis.range.start, Math.round(axis.range.start).toFloat())
        assertEquals(axis.range.endInclusive, Math.round(axis.range.endInclusive).toFloat())
    }

    @Test fun `gridValuesFor includes the endpoints and only whole numbers for a whole step`() {
        val values = gridValuesFor(0f, 10f, 2f)
        assertEquals(0f, values.first())
        assertEquals(10f, values.last())
        values.forEach { assertEquals(it, Math.round(it).toFloat()) }
    }

    @Test fun `formatChartValue renders whole numbers without a decimal`() {
        assertEquals("5", formatChartValue(5f))
        assertEquals("5.5", formatChartValue(5.5f))
    }
}
