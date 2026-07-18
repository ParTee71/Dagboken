package se.partee71.dagboken.ui.diagram

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val FALLBACK_RANGE = -10f..10f
private const val MARGIN_FRACTION = 0.1f
private val NICE_FRACTIONS = floatArrayOf(1f, 2f, 5f, 10f)

/**
 * Beräknar ett y-axelspann anpassat efter [values] faktiska min/max (#136) — i stället för
 * att alltid ankra vid 0. Lägger på en marginal och avrundar ut till närmaste "snygga" steg
 * (1/2/5 × 10^n) så ett smalt värdeband (t.ex. 5–8) fyller diagramhöjden och långt-från-noll
 * serier (t.ex. steg ~5000–9000) inte får orimligt många rutnätslinjer.
 */
fun computeSmartYRange(values: List<Float>): ClosedFloatingPointRange<Float> {
    val finite = values.filter { it.isFinite() }
    if (finite.isEmpty()) return FALLBACK_RANGE

    val rawMin = finite.min()
    val rawMax = finite.max()

    if (rawMin == rawMax) {
        val pad = if (rawMin == 0f) 1f else abs(rawMin) * 0.5f
        return roundOutward(rawMin - pad, rawMax + pad)
    }

    val margin = (rawMax - rawMin) * MARGIN_FRACTION
    return roundOutward(rawMin - margin, rawMax + margin)
}

private fun roundOutward(min: Float, max: Float): ClosedFloatingPointRange<Float> {
    val step = niceStep(max - min)
    val roundedMin = floor(min / step) * step
    val roundedMax = ceil(max / step) * step
    return roundedMin..roundedMax
}

/** Rundar [rawSpan] upp till närmaste "snygga" steg (1/2/5 × 10^n) för läsbara axelgränser. */
private fun niceStep(rawSpan: Float): Float {
    if (rawSpan <= 0f) return 1f
    val magnitude = 10.0.pow(floor(log10(rawSpan.toDouble()))).toFloat()
    val normalized = rawSpan / magnitude
    val niceFraction = NICE_FRACTIONS.firstOrNull { normalized <= it } ?: NICE_FRACTIONS.last()
    return niceFraction * magnitude / 10f
}
