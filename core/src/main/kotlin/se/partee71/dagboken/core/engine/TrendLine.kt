package se.partee71.dagboken.core.engine

// Trendlinjen (TRD-13) – portad oförändrad från 3.x `ui/diagram/TrendLine.kt`; enda stället för
// minsta kvadrat-anpassningen i appen (regel 4).

/** En rät linje `y = slope * x + intercept`, x är punktens index i serien. */
data class TrendLine(val slope: Float, val intercept: Float) {
    fun valueAt(x: Float): Float = slope * x + intercept

    /** Riktningen som den skrivs i trendpillen och skärmläsarbeskrivningen (samma gränser som 3.x). */
    val direction: TrendDirection
        get() = when {
            slope > 0f -> TrendDirection.RISING
            slope < 0f -> TrendDirection.FALLING
            else -> TrendDirection.FLAT
        }
}

/** Trendens riktning: uppåt, nedåt eller oförändrad. */
enum class TrendDirection { RISING, FALLING, FLAT }

/**
 * Anpassar en linjär minsta-kvadrat-trendlinje över [points] indexerade positioner
 * (TRD-13) — `null`-luckor hoppas över utan att förskjuta lutningen, eftersom varje känd
 * punkt behåller sitt ursprungliga index som x-värde. Kräver minst två kända punkter,
 * annars finns ingen entydig riktning att rita.
 */
fun computeTrendLine(points: List<Float?>): TrendLine? {
    val known = points.withIndex().mapNotNull { (i, v) -> v?.let { i.toFloat() to it } }
    if (known.size < 2) return null

    val n = known.size
    val sumX = known.sumOf { it.first.toDouble() }
    val sumY = known.sumOf { it.second.toDouble() }
    val sumXY = known.sumOf { it.first.toDouble() * it.second.toDouble() }
    val sumXX = known.sumOf { it.first.toDouble() * it.first.toDouble() }

    val slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX)
    val intercept = (sumY - slope * sumX) / n
    return TrendLine(slope.toFloat(), intercept.toFloat())
}

/** Den ritade trendlinjen: från seriens första till sista kända index (som i 3.x). */
data class TrendSegment(val startX: Int, val startY: Float, val endX: Int, val endY: Float, val line: TrendLine)

/** Trendlinjens ändpunkter för [points], eller `null` med färre än två kända punkter. */
fun trendSegment(points: List<Float?>): TrendSegment? {
    val line = computeTrendLine(points) ?: return null
    val first = points.indexOfFirst { it != null }
    val last = points.indexOfLast { it != null }
    return TrendSegment(first, line.valueAt(first.toFloat()), last, line.valueAt(last.toFloat()), line)
}
