package se.partee71.dagboken.core.engine

// Det staplade stapeldiagrammets uträkningar (TRD-16) – portade från 3.x `ui/diagram/StackedBarChart.kt`.
// Färg och etikett per segment hör till appen; här är segmenten bara index.

/**
 * En stapels delvärden, i samma ordning som diagrammets segment. Ett `null` betyder att
 * kategorin saknas den dagen och tar **ingen höjd** i stapeln — inte att den var noll.
 */
data class StackedPoint(val values: List<Float?>)

/** Stapelns totalhöjd, eller null om ingen kategori har något värde alls. */
fun stackTotal(point: StackedPoint): Float? =
    point.values.filterNotNull().takeIf { it.isNotEmpty() }?.sum()

/** Totalhöjden per stapel — underlaget för y-skalan, min/max och trendlinjen. */
fun stackTotals(points: List<StackedPoint>): List<Float?> = points.map { stackTotal(it) }

/**
 * Segmentens underkanter, ackumulerade nedifrån och upp. Ett saknat segment flyttar inte
 * nästa segment uppåt, så en natt utan REM-mätning inte ser ut att ha mer djupsömn än den
 * hade.
 */
fun stackBases(point: StackedPoint): List<Float> {
    var base = 0f
    return point.values.map { value ->
        val current = base
        base += value ?: 0f
        current
    }
}

/**
 * Index för den kategori som står för mest av perioden (av [segmentCount]) — används i
 * skärmläsarbeskrivningen, eftersom "vilket stadium dominerade" är det ett seende öga läser ur
 * staplarna direkt. Null när ingen kategori har någon tid alls. Vid lika vinner den första.
 */
fun dominantSegment(points: List<StackedPoint>, segmentCount: Int): Int? {
    if (segmentCount <= 0 || points.isEmpty()) return null
    val sums = (0 until segmentCount).map { index ->
        points.sumOf { (it.values.getOrNull(index) ?: 0f).toDouble() }
    }
    val largest = sums.max()
    return if (largest <= 0.0) null else sums.indexOf(largest)
}
