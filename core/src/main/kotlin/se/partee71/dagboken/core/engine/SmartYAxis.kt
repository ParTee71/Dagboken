package se.partee71.dagboken.core.engine

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// Diagrammens y-axel (TRD-7, TRD-9) – portad oförändrad från 3.x `ui/diagram/SmartYAxis.kt`.
// Enda stället för axelns gränser, steg och rutnät; alla diagram i appen använder den (regel 4).

private val FALLBACK_RANGE = -10f..10f
private const val FALLBACK_STEP = 5f
private const val MARGIN_FRACTION = 0.1f
private val NICE_FRACTIONS = floatArrayOf(1f, 2f, 5f, 10f)

/** Högst så många rutnätslinjer ritas, hur smalt steget än blir. */
const val MAX_GRID_LINES = 12

/** Högst så många etiketter på x-axeln innan de glesas ut ([xLabelStep]). */
const val MAX_X_LABELS = 6

/**
 * [range] — se [computeSmartYRange]. [step] — avståndet mellan varje "snygg" gridlinje;
 * [range]s ändpunkter är alltid exakta multiplar av [step], så gridlinjer/etiketter ritade
 * vid varje multipel av [step] landar exakt på range-gränserna (garanterar TRD-9).
 */
data class SmartYAxis(val range: ClosedFloatingPointRange<Float>, val step: Float)

/**
 * Beräknar ett y-axelspann anpassat efter [values] faktiska min/max (TRD-7) — i stället för
 * att alltid ankra vid 0. Lägger på en marginal och avrundar ut till närmaste "snygga" steg
 * (1/2/5 × 10^n) så ett smalt värdeband (t.ex. 5–8) fyller diagramhöjden och långt-från-noll
 * serier (t.ex. steg ~5000–9000) inte får orimligt många rutnätslinjer.
 */
fun computeSmartYRange(values: List<Float>): ClosedFloatingPointRange<Float> = computeSmartYAxis(values).range

/** Som [computeSmartYRange] men returnerar även gridlinje-steget (värdelinjerna, TRD-9). */
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

/**
 * Rundar [rawSpan] upp till närmaste "snygga" steg (1/2/5 × 10^n), lägst **1** — axelns
 * gränser och gridlinjer ska alltid vara heltal, även för ett smalt värdeband (t.ex.
 * symptomgradering 1–10) där den obegränsade formeln annars ger 0,5/0,2/0,1.
 */
fun niceStep(rawSpan: Float): Float {
    if (rawSpan <= 0f) return 1f
    val magnitude = 10.0.pow(floor(log10(rawSpan.toDouble()))).toFloat()
    val normalized = rawSpan / magnitude
    val niceFraction = NICE_FRACTIONS.firstOrNull { normalized <= it } ?: NICE_FRACTIONS.last()
    return (niceFraction * magnitude / 10f).coerceAtLeast(1f)
}

/** Rutnätsvärden mellan [minValue] och [maxValue], jämnt fördelade med [step] mellanrum. */
fun gridValuesFor(minValue: Float, maxValue: Float, step: Float): List<Float> {
    if (step <= 0f || maxValue <= minValue) return listOf(minValue, maxValue)
    val values = mutableListOf<Float>()
    var v = minValue
    var guard = 0
    while (v <= maxValue + step * 0.001f && guard < MAX_GRID_LINES) {
        values += v
        v += step
        guard++
    }
    if (values.isEmpty() || values.last() < maxValue - step * 0.001f) values += maxValue
    return values
}

/**
 * Var hur många x-positioner som får en etikett, så att högst ungefär [MAX_X_LABELS] står
 * utskrivna (3.x: `max(1, antal / 6)`).
 */
fun xLabelStep(count: Int): Int = maxOf(1, count / MAX_X_LABELS)

/**
 * Var hur många x-positioner som får en etikett när etiketterna är [labelWidth] breda och ritytan [plotWidth]
 * (samma enhet), med [gap] luft mellan två etiketter: aldrig glesare än nödvändigt, aldrig tätare än [xLabelStep]
 * – så att "23 sep 25 sep 27 sep …" inte trängs i ett 14-dagarsdiagram (TRD-6).
 */
fun xLabelStepFitting(count: Int, labelWidth: Float, plotWidth: Float, gap: Float): Int {
    val base = xLabelStep(count)
    if (count <= 1 || labelWidth <= 0f || plotWidth <= 0f) return base
    val slot = plotWidth / count
    val needed = ceil((labelWidth + gap) / slot).toInt()
    return maxOf(base, needed, 1)
}

/**
 * Y-axeln för ett diagram med flera serier (TRD-2, TRD-7): skalan räknas över alla seriers kända
 * värden **och** deras trendlinjers ändpunkter (TRD-13), så att en trendlinje aldrig klipps av
 * axeln. Luckor (`null`) räknas inte.
 */
fun chartAxisFor(series: List<List<Float?>>): SmartYAxis {
    val values = series.flatMap { points ->
        points.filterNotNull() + trendSegment(points)?.let { listOf(it.startY, it.endY) }.orEmpty()
    }
    return computeSmartYAxis(values)
}

/**
 * Y-axeln för intervalldiagrammet (TRD-8): över varje dags lägsta och högsta värde (som 3.x Trender)
 * och dagsvärdenas trendlinje (TRD-13).
 */
fun intervalAxisFor(points: List<IntervalPoint?>): SmartYAxis {
    val known = points.filterNotNull()
    val trend = trendSegment(points.map { it?.value })?.let { listOf(it.startY, it.endY) }.orEmpty()
    return computeSmartYAxis(known.flatMap { listOf(it.min, it.max) } + trend)
}

/**
 * Y-axeln för det staplade diagrammet (TRD-16): över staplarnas totalhöjd och totalens trendlinje,
 * och alltid med noll – en stapel är en längd från noll, och en axel som börjar ovanför noll skulle
 * kapa de nedersta segmenten. (3.x räknade axeln utan noll men ritade staplarna från axelns botten,
 * så att en stapel kunde gå över axelns topp och värdelinjerna inte stämde med segmenten.)
 */
fun stackedAxisFor(points: List<StackedPoint>, line: List<Float?> = emptyList()): SmartYAxis {
    val totals = stackTotals(points)
    val trend = trendSegment(totals)?.let { listOf(it.startY, it.endY) }.orEmpty()
    // En linje ovanpå staplarna (TRD-21: incheckningarnas svårighet) räknas in i skalan; den ritas utan trend.
    val lineValues = line.filterNotNull()
    val axis = computeSmartYAxis(listOf(0f) + totals.filterNotNull() + trend + lineValues)
    // Marginalen under noll betyder ingenting för en längd; noll är en multipel av steget, så
    // värdelinjerna landar fortfarande exakt på gränserna.
    val start = if ((totals + line).all { it == null || it >= 0f }) maxOf(0f, axis.range.start) else axis.range.start
    return axis.copy(range = start..axis.range.endInclusive)
}

/**
 * "5" för heltal (även efter avrundning), annars en decimal med svenskt decimalkomma ("6,4"), och tusental avskilda med
 * ett hårt mellanslag från 1 000 ("7 842", "12 345,5") — i axlar, bildtexter, skärmläsarens sammanfattning och
 * klockans mätvärden (HLS-6), en formaterare för alla tal. Avrundningen är 3.x:s (halva uppåt på en decimal); 3.x
 * skrev punkt och grupperade inte.
 */
fun formatChartValue(value: Float): String {
    // Avrunda först, avgör heltal sedan: 6,96 → "7" (inte "7,0") och −0,04 → "0" (inte "-0,0").
    val rounded = String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0").let { if (it == "-0") "0" else it }
    val sign = if (rounded.startsWith("-")) "-" else ""
    val whole = rounded.removePrefix("-").substringBefore('.')
    val decimals = rounded.substringAfter('.', "")
    val grouped = whole.reversed().chunked(DIGITS_PER_GROUP).joinToString(NO_BREAK_SPACE).reversed()
    return sign + grouped + if (decimals.isEmpty()) "" else ",$decimals"
}

private const val DIGITS_PER_GROUP = 3

/** Ett hårt mellanslag, så att ett tal aldrig bryts mellan tusentalen. */
private const val NO_BREAK_SPACE = "\u00A0"
