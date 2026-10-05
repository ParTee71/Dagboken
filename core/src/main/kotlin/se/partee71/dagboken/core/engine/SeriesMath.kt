package se.partee71.dagboken.core.engine

// Det diagrammen räknar ur en serie utöver axel och trend: sammanfattningen under diagrammet och i
// skärmläsarbeskrivningen (TRD-9, NFR-14) och uppdelningen i sammanhängande bitar runt luckorna
// (TRD-6, TRD-15: luckor ritas aldrig som nollor). Enda stället (regel 4).

/** Kända värden i en serie: antal, lägsta, högsta, medel och det senaste. */
data class SeriesSummary(val count: Int, val min: Float, val max: Float, val average: Float, val last: Float)

/** Sammanfattning av de kända värdena i [points]; `null` när serien bara har luckor. */
fun summarize(points: List<Float?>): SeriesSummary? {
    val known = points.filterNotNull()
    if (known.isEmpty()) return null
    return SeriesSummary(known.size, known.min(), known.max(), known.average().toFloat(), known.last())
}

/** Antal kända (icke-`null`) värden – det diagrammens tomma läge räknar på. */
fun knownCount(points: List<Float?>): Int = points.count { it != null }

/** En sammanhängande bit av en serie utan luckor: värdena på x = [start], [start] + 1, … */
data class GapFreeRun(val start: Int, val values: List<Float>) {
    val xs: List<Int> get() = values.indices.map { start + it }
}

/**
 * Delar [points] i sammanhängande bitar runt luckorna (`null`). Varje bit ritas som en egen
 * kurva i samma färg, så att en dag utan data blir ett avbrott i kurvan – aldrig en nolla.
 */
fun gapFreeRuns(points: List<Float?>): List<GapFreeRun> {
    val runs = mutableListOf<GapFreeRun>()
    var start = -1
    val current = mutableListOf<Float>()
    points.forEachIndexed { i, value ->
        if (value == null) {
            if (current.isNotEmpty()) runs += GapFreeRun(start, current.toList())
            current.clear()
        } else {
            if (current.isEmpty()) start = i
            current += value
        }
    }
    if (current.isNotEmpty()) runs += GapFreeRun(start, current.toList())
    return runs
}

/** En dags intervall: [min]–[max] för dagen, med [value] (t.ex. dagsgenomsnittet) markerat (TRD-8). */
data class IntervalPoint(val min: Float, val value: Float, val max: Float)

/**
 * Sammanfattning av ett intervalldiagram: lägst = lägsta dagsminimum, högst = högsta dagsmaximum
 * (som 3.x Trender), medel och senaste över dagsvärdena. `null` utan någon dag med data.
 */
fun summarizeIntervals(points: List<IntervalPoint?>): SeriesSummary? {
    val known = points.filterNotNull()
    if (known.isEmpty()) return null
    val values = known.map { it.value }
    return SeriesSummary(known.size, known.minOf { it.min }, known.maxOf { it.max }, values.average().toFloat(), values.last())
}
