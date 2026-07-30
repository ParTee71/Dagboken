package se.partee71.dagboken.ui.diagram

/** En rät linje `y = slope * x + intercept`, x är punktens index i serien. */
data class TrendLine(val slope: Float, val intercept: Float) {
    fun valueAt(x: Float): Float = slope * x + intercept
}

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
