package se.partee71.dagboken.ui.common

import androidx.compose.ui.graphics.Color
/**
 * Färger för serier som visas tillsammans (TRD-17): varje serie behåller sin **egen** färg ([preferred], `null` =
 * [fallback], diagrammets kurvfärg), men två serier får aldrig samma – vid krock tar den senare nästa lediga färg
 * ur [palette] (diagrampaletten, `AppColors.extended.chartSeries`, sist [fallback]), så att två klockmått som båda är teal i sina egna kort går att skilja åt.
 */
fun distinctSeriesColors(preferred: List<Color?>, fallback: Color, palette: List<Color>): List<Color> {
    val taken = mutableSetOf<Color>()
    return preferred.map { wanted ->
        val own = wanted ?: fallback
        if (taken.add(own)) own else (palette + fallback).firstOrNull { taken.add(it) } ?: own
    }
}
