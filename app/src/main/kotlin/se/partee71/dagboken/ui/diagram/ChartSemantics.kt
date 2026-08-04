package se.partee71.dagboken.ui.diagram

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R

/**
 * Bygger en talbar sammanfattning av en dataserie för skärmläsare (a11y). Ett diagram
 * är annars helt osynligt för TalkBack — bara en rityta utan text. Sammanfattningen
 * följer samma innehåll som [MinMaxCaption] visar visuellt: antal punkter samt lägsta,
 * högsta och senaste värde.
 */
@Composable
fun chartContentDescription(label: String, points: List<Float?>): String {
    val known = points.filterNotNull()
    if (known.isEmpty()) return stringResource(R.string.diagram_a11y_empty, label)
    return stringResource(
        R.string.diagram_a11y_summary,
        label,
        known.size,
        formatChartValue(known.min()),
        formatChartValue(known.max()),
        formatChartValue(known.last()),
    )
}
