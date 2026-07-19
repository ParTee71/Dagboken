package se.partee71.dagboken.ui.diagram

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val FALLBACK_RANGE = -10f..10f
private const val FALLBACK_STEP = 5f
private const val MARGIN_FRACTION = 0.1f
private val NICE_FRACTIONS = floatArrayOf(1f, 2f, 5f, 10f)

/**
 * [range] — se [computeSmartYRange]. [step] — avståndet mellan varje "snygg" gridlinje;
 * [range]s ändpunkter är alltid exakta multiplar av [step], så gridlinjer/etiketter ritade
 * vid varje multipel av [step] landar exakt på range-gränserna (garanterar TRD-9).
 */
data class SmartYAxis(val range: ClosedFloatingPointRange<Float>, val step: Float)

/**
 * Beräknar ett y-axelspann anpassat efter [values] faktiska min/max (#136) — i stället för
 * att alltid ankra vid 0. Lägger på en marginal och avrundar ut till närmaste "snygga" steg
 * (1/2/5 × 10^n) så ett smalt värdeband (t.ex. 5–8) fyller diagramhöjden och långt-från-noll
 * serier (t.ex. steg ~5000–9000) inte får orimligt många rutnätslinjer.
 */
fun computeSmartYRange(values: List<Float>): ClosedFloatingPointRange<Float> = computeSmartYAxis(values).range

/** Som [computeSmartYRange] men returnerar även gridlinje-steget (#141 — värdelinjer i `IntervalBarChart`). */
fun computeSmartYAxis(values: List<Float>): SmartYAxis {
    val finite = values.filter { it.isFinite() }
    if (finite.isEmpty()) return SmartYAxis(FALLBACK_RANGE, FALLBACK_STEP)

    val rawMin = finite.min()
    val rawMax = finite.max()

    if (rawMin == rawMax) {
        val pad = if (rawMin == 0f) 1f else abs(rawMin) * 0.5f
        return roundOutward(rawMin - pad, rawMax + pad)
    }

    val margin = (rawMax - rawMin) * MARGIN_FRACTION
    return roundOutward(rawMin - margin, rawMax + margin)
}

private fun roundOutward(min: Float, max: Float): SmartYAxis {
    val step = niceStep(max - min)
    val roundedMin = floor(min / step) * step
    val roundedMax = ceil(max / step) * step
    return SmartYAxis(roundedMin..roundedMax, step)
}

/** Rundar [rawSpan] upp till närmaste "snygga" steg (1/2/5 × 10^n) för läsbara axelgränser. */
internal fun niceStep(rawSpan: Float): Float {
    if (rawSpan <= 0f) return 1f
    val magnitude = 10.0.pow(floor(log10(rawSpan.toDouble()))).toFloat()
    val normalized = rawSpan / magnitude
    val niceFraction = NICE_FRACTIONS.firstOrNull { normalized <= it } ?: NICE_FRACTIONS.last()
    return niceFraction * magnitude / 10f
}
