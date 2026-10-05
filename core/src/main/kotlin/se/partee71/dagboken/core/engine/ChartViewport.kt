package se.partee71.dagboken.core.engine

// Stapeldiagrammens koordinater och zoom/panorering (TRD-10) – den handrullade varianten för
// `IntervalBarChart` och `StackedBarChart`, som inte bygger på Vico. Portad från 3.x (samma
// formler i båda diagrammen där); här en gång och testbar utan Android.

/** Största inzoomning, som i 3.x. */
const val MAX_BAR_ZOOM = 6f

/**
 * Zoom ([scale] ≥ 1) och panorering ([offsetX] ≤ 0, i pixlar) längs x. Standard är helt utzoomat:
 * hela perioden syns (TRD-10).
 */
data class ZoomPan(val scale: Float = 1f, val offsetX: Float = 0f) {

    /**
     * Ny zoom och panorering efter en gest: [zoom] multipliceras in och begränsas till 1…[MAX_BAR_ZOOM],
     * [panX] flyttar innehållet men aldrig förbi första eller sista dagen i en yta som är [width] bred.
     */
    fun transform(zoom: Float, panX: Float, width: Float): ZoomPan {
        val newScale = (scale * zoom).coerceIn(1f, MAX_BAR_ZOOM)
        val maxOffset = (width * (newScale - 1f)).coerceAtLeast(0f)
        return ZoomPan(newScale, (offsetX + panX).coerceIn(-maxOffset, 0f))
    }
}

/**
 * Ritytan för ett stapeldiagram med [count] lika breda platser mellan [left] och [right] och
 * värdena [minValue]…[maxValue] mellan [bottom] och [top] (pixlar, y nedåt).
 */
data class BarViewport(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val minValue: Float,
    val maxValue: Float,
    val count: Int,
    val zoomPan: ZoomPan = ZoomPan(),
) {
    /** Bredden per plats (dag eller natt) utan zoom. */
    val slotWidth: Float get() = if (count <= 0) 0f else (right - left) / count

    /** Platsens mitt i x, med zoom och panorering – vid standardläget samma som utan zoom. */
    fun xOf(index: Int): Float {
        val baseX = left + slotWidth * (index + 0.5f)
        return left + (baseX - left) * zoomPan.scale + zoomPan.offsetX
    }

    /**
     * Ny zoom och panorering efter en gest, begränsad till **ritytan** ([left]…[right]) – inte hela
     * diagrammets bredd med y-etiketterna – så att sista platsen aldrig kan panoreras förbi.
     */
    fun transform(zoom: Float, panX: Float): ZoomPan = zoomPan.transform(zoom, panX, right - left)

    /** Värdets höjd i y; ett tomt spann ritas som spannet 1 i stället för att dela med noll. */
    fun yOf(value: Float): Float {
        val span = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        return bottom - ((value - minValue) / span) * (bottom - top).coerceAtLeast(1f)
    }
}
