package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import org.junit.Test

/** Stapeldiagrammens koordinater och zoom/panorering (TRD-10), samma formler som 3.x. */
class ChartViewportTest {

    private val viewport = BarViewport(left = 32f, top = 8f, right = 332f, bottom = 208f, minValue = 0f, maxValue = 10f, count = 3)

    @Test
    fun `utan zoom ligger varje plats mitt i sin lika breda del`() {
        assertEquals(100f, viewport.slotWidth)
        assertEquals(listOf(82f, 182f, 282f), (0..2).map { viewport.xOf(it) })
    }

    @Test
    fun `y går från botten vid minsta värdet till toppen vid största`() {
        assertEquals(208f, viewport.yOf(0f))
        assertEquals(8f, viewport.yOf(10f))
        assertEquals(108f, viewport.yOf(5f))
    }

    @Test
    fun `ett tomt värdespann delar inte med noll`() {
        val flat = viewport.copy(minValue = 4f, maxValue = 4f)
        assertEquals(208f, flat.yOf(4f))
    }

    @Test
    fun `zoom och panorering skalar kring vänsterkanten`() {
        val zoomed = viewport.copy(zoomPan = ZoomPan(scale = 2f, offsetX = -100f))
        assertEquals(32f + 50f * 2f - 100f, zoomed.xOf(0))
    }

    @Test
    fun `standard är helt utzoomat och zoomen stannar inom 1 till 6`() {
        assertEquals(ZoomPan(1f, 0f), ZoomPan())
        assertEquals(1f, ZoomPan().transform(zoom = 0.5f, panX = 0f, width = 300f).scale)
        assertEquals(MAX_BAR_ZOOM, ZoomPan().transform(zoom = 10f, panX = 0f, width = 300f).scale)
    }

    @Test
    fun `panoreringen går aldrig förbi första eller sista platsen`() {
        assertEquals(0f, ZoomPan(2f, 0f).transform(zoom = 1f, panX = 50f, width = 300f).offsetX, EXACT, "inte förbi början")
        assertEquals(-300f, ZoomPan(2f, 0f).transform(zoom = 1f, panX = -1000f, width = 300f).offsetX, EXACT, "inte förbi slutet")
        assertEquals(0f, ZoomPan().transform(zoom = 1f, panX = -80f, width = 300f).offsetX, EXACT, "utzoomat går inte att panorera")
    }

    @Test
    fun `panoreringen begränsas av ritytan, inte av y-etiketterna`() {
        // 40 px y-etiketter till vänster, ritytan 300 px: inzoomat ×2 går det att panorera 300 px – inte 340.
        val plot = BarViewport(left = 40f, top = 0f, right = 340f, bottom = 200f, minValue = 0f, maxValue = 10f, count = 3, zoomPan = ZoomPan(2f, 0f))
        val panned = plot.transform(zoom = 1f, panX = -10_000f)
        assertEquals(-300f, panned.offsetX, EXACT)
        val last = plot.copy(zoomPan = panned)
        assertEquals(plot.right - plot.slotWidth, last.xOf(2), EXACT, "sista dagen står helt i ritytan")
    }

    private companion object {
        /** Jämför tal (−0 och 0 är samma läge). */
        const val EXACT = 0f
    }
}
