package se.partee71.dagboken.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Portad från 3.x `ui/diagram/SmartYAxisTest` – samma värden för samma indata (TRD-7, TRD-9). */
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
        assertEquals("5,5", formatChartValue(5.5f))
    }

    // ─── 4.0: exakta 3.x-värden, x-etiketter och axel över flera serier ─────────

    @Test fun `the 3x bands give exactly the same axes as in 3x`() {
        // Uträknade för hand med 3.x-formeln – ändras de har beräkningen glidit isär från 3.x.
        assertEquals(SmartYAxis(4f..9f, 1f), computeSmartYAxis(listOf(5f, 6f, 8f, 7f)))
        assertEquals(SmartYAxis(54f..66f, 2f), computeSmartYAxis(listOf(58f, 61f, 55f, 65f)))
        assertEquals(SmartYAxis(4500f..9500f, 500f), computeSmartYAxis(listOf(5200f, 8800f, 6400f, 9100f)))
        assertEquals(SmartYAxis(2f..8f, 1f), computeSmartYAxis(listOf(5f, 5f, 5f)))
        assertEquals(SmartYAxis(-1f..1f, 1f), computeSmartYAxis(listOf(0f, 0f)))
        assertEquals(SmartYAxis(-9f..-2f, 1f), computeSmartYAxis(listOf(-8f, -5f, -3f)))
        assertEquals(SmartYAxis(20f..65f, 5f), computeSmartYAxis(listOf(42f)))
    }

    @Test fun `non-finite values are ignored`() {
        assertEquals(computeSmartYAxis(listOf(5f, 8f)), computeSmartYAxis(listOf(5f, Float.NaN, 8f, Float.POSITIVE_INFINITY)))
    }

    @Test fun `x labels are thinned to about six`() {
        assertEquals(1, xLabelStep(0))
        assertEquals(1, xLabelStep(7))
        assertEquals(2, xLabelStep(14))
        assertEquals(5, xLabelStep(30))
        assertEquals(15, xLabelStep(90))
    }

    @Test fun `the axis over several series covers every series and its trend line`() {
        val rising = listOf(1f, null, 9f, 10f)
        val flat = listOf(5f, 5f, null, 5f)
        val axis = chartAxisFor(listOf(rising, flat))
        val trend = trendSegment(rising)!!
        listOf(1f, 9f, 10f, 5f, trend.startY, trend.endY).forEach { assertTrue("$it within ${axis.range}", it in axis.range) }
    }

    @Test fun `the axis ignores gaps and falls back like computeSmartYAxis without values`() {
        assertEquals(computeSmartYAxis(emptyList()), chartAxisFor(listOf(listOf(null, null))))
        assertEquals(computeSmartYAxis(listOf(4f)), chartAxisFor(listOf(listOf(null, 4f, null))))
    }

    @Test fun `formatChartValue rounds to one decimal with a Swedish decimal comma and keeps the sign`() {
        assertEquals("6,3", formatChartValue(6.25f + 0.01f))
        assertEquals("6,4", formatChartValue(6.4f))
        assertEquals("-2", formatChartValue(-2f))
        assertEquals("-2,5", formatChartValue(-2.5f))
        assertEquals("0", formatChartValue(0f))
        assertEquals("7", formatChartValue(6.96f)) // avrundas före heltalskontrollen
    }

    @Test fun `formatChartValue groups thousands with a no-break space from 1 000 (TRD-9, HLS-6)`() {
        assertEquals("999", formatChartValue(999f))
        assertEquals("1\u00A0000", formatChartValue(1_000f))
        assertEquals("7\u00A0842", formatChartValue(7_842f))
        assertEquals("12\u00A0345,5", formatChartValue(12_345.5f))
        assertEquals("1\u00A0234\u00A0567", formatChartValue(1_234_567f))
        assertEquals("-4\u00A0020", formatChartValue(-4_020f))
        assertEquals("1\u00A0000", formatChartValue(999.96f)) // avrundningen kan ge en grupp till
        assertEquals("0", formatChartValue(-0.04f)) // ingen minusnolla
        assertEquals("-0,1", formatChartValue(-0.06f))
        assertEquals("10\u00A0000", formatChartValue(10_000f)) // tusentalen grupperas sedan #241 (TRD-9)
    }

    @Test fun `the interval axis covers every day's span and the trend of the day values`() {
        val points = listOf(IntervalPoint(3f, 5f, 7f), null, IntervalPoint(2f, 4f, 9f))
        val axis = intervalAxisFor(points)
        assertEquals(computeSmartYAxis(listOf(3f, 7f, 2f, 9f) + trendSegment(listOf(5f, null, 4f))!!.let { listOf(it.startY, it.endY) }), axis)
        assertTrue(2f in axis.range && 9f in axis.range)
    }

    @Test fun `the stacked axis always starts at zero so no segment is cut`() {
        val axis = stackedAxisFor(listOf(StackedPoint(listOf(1.5f, 1.5f, 4.5f, 0.5f)), StackedPoint(listOf(1f, 1f, 4f, 0.5f))))
        assertTrue("min ${axis.range.start} should be <= 0", axis.range.start <= 0f)
        assertTrue(8f in axis.range)
        assertEquals(0f, axis.range.start)
    }

    @Test fun `x labels thin out until the measured width fits (TRD-6)`() {
        assertEquals("slot 50, label 68 → every other", 2, xLabelStepFitting(14, labelWidth = 60f, plotWidth = 700f, gap = 8f))
        assertEquals(3, xLabelStepFitting(14, labelWidth = 120f, plotWidth = 700f, gap = 8f))
        assertEquals(1, xLabelStepFitting(7, labelWidth = 40f, plotWidth = 700f, gap = 8f))
        assertEquals("never denser than xLabelStep", xLabelStep(90), xLabelStepFitting(90, labelWidth = 10f, plotWidth = 10_000f, gap = 0f))
        assertEquals(1, xLabelStepFitting(1, labelWidth = 500f, plotWidth = 10f, gap = 0f))
    }

    @Test fun `a line over the stacked bars is part of the axis (TRD-21)`() {
        val bars = listOf(StackedPoint(listOf(3f)), StackedPoint(listOf(null)), StackedPoint(listOf(4f)))
        val without = stackedAxisFor(bars)
        val with = stackedAxisFor(bars, line = listOf(null, 9f, 8f))
        assertTrue(4f in without.range)
        assertTrue("the line's 9 must fit", 9f in with.range)
        assertEquals(0f, with.range.start)
        // The line is drawn without a trend, so a steep line does not widen the axis beyond its values.
        assertEquals(stackedAxisFor(bars, line = listOf(1f, 9f)), stackedAxisFor(bars, line = listOf(9f, 1f)))
        assertEquals("no line keeps the axis as before", without, stackedAxisFor(bars, line = emptyList()))
    }
}
