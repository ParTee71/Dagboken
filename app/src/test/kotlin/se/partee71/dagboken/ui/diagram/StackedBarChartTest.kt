package se.partee71.dagboken.ui.diagram

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Enhetstest för det staplade stapeldiagrammets rena uträkningar (TRD-16) — totalhöjd,
 * segmentens underkanter och vilken kategori som dominerar perioden.
 */
class StackedBarChartTest {

    private val segments = listOf(
        StackSegment("Djup", Color(0xFF4f46e5)),
        StackSegment("REM", Color(0xFFa78bfa)),
        StackSegment("Lätt", Color(0xFF93c5fd)),
        StackSegment("Vaken", Color(0xFFfbbf24)),
    )

    private fun night(vararg values: Float?) = StackedPoint(values.toList())

    // ─── Totalhöjd ────────────────────────────────────────────────────────────

    @Test fun `the total is the sum of the segments`() {
        assertEquals(8f, stackTotal(night(1.5f, 1.5f, 4.5f, 0.5f))!!, 0.001f)
    }

    @Test fun `missing segments do not count towards the total`() {
        assertEquals(6f, stackTotal(night(1.5f, null, 4.5f, null))!!, 0.001f)
    }

    @Test fun `a night without any segment has no total at all`() {
        // En natt utan mätning är en lucka, inte en nollhög stapel.
        assertNull(stackTotal(night(null, null, null, null)))
    }

    @Test fun `totals are computed per night`() {
        val totals = stackTotals(
            listOf(
                night(1f, 1f, 4f, 0.5f),
                night(null, null, null, null),
                night(2f, 1f, 4f, 1f),
            ),
        )
        assertEquals(3, totals.size)
        assertEquals(6.5f, totals[0]!!, 0.001f)
        assertNull(totals[1])
        assertEquals(8f, totals[2]!!, 0.001f)
    }

    // ─── Segmentens underkanter ───────────────────────────────────────────────

    @Test fun `segments stack from the bottom up`() {
        val bases = stackBases(night(1.5f, 1.5f, 4.5f, 0.5f))
        assertEquals(listOf(0f, 1.5f, 3f, 7.5f), bases)
    }

    @Test fun `a missing segment does not push the next one upwards`() {
        // Utan detta skulle en natt utan REM-mätning se ut att ha mer djupsömn än den hade.
        val bases = stackBases(night(1.5f, null, 4.5f, 0.5f))
        assertEquals(listOf(0f, 1.5f, 1.5f, 6f), bases)
    }

    @Test fun `an all-empty night has every base at zero`() {
        assertEquals(listOf(0f, 0f, 0f, 0f), stackBases(night(null, null, null, null)))
    }

    // ─── Dominerande kategori ─────────────────────────────────────────────────

    @Test fun `the dominant segment is the one with most time across the period`() {
        val dominant = dominantSegment(
            listOf(
                night(1f, 1.5f, 4f, 0.5f),
                night(1.2f, 1.5f, 4.5f, 0.4f),
            ),
            segments,
        )
        assertEquals("Lätt", dominant)
    }

    @Test fun `the dominant segment is measured over the whole period, not the last night`() {
        val dominant = dominantSegment(
            listOf(
                night(5f, 0f, 0f, 0f),
                night(5f, 0f, 0f, 0f),
                night(0f, 0f, 3f, 0f),
            ),
            segments,
        )
        assertEquals("Djup", dominant)
    }

    @Test fun `there is no dominant segment when nothing was measured`() {
        assertNull(dominantSegment(listOf(night(null, null, null, null)), segments))
        assertNull(dominantSegment(emptyList(), segments))
        assertNull(dominantSegment(listOf(night(1f)), emptyList()))
    }
}
