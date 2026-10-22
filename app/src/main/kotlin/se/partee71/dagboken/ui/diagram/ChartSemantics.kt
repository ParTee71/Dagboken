package se.partee71.dagboken.ui.diagram

import android.content.res.Resources
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.SeriesSummary
import se.partee71.dagboken.core.engine.TrendDirection
import se.partee71.dagboken.core.engine.formatChartValue

// Diagrammens talbara sammanfattning (NFR-14): ett diagram är annars en rityta utan text för
// TalkBack. Beskrivningen bär det ett seende öga läser direkt – antal, lägsta, högsta, senaste och
// trendens riktning – och byggs bara här, så att alla diagram läses upp på samma sätt (regel 4). Funktionerna tar
// `Resources` (som `chartStatText`), så att diagrammen kan `remember`a beskrivningen.

/** Hela diagrammets beskrivning som en nod för skärmläsaren. */
internal fun Modifier.chartDescription(description: String): Modifier = semantics { contentDescription = description }

/** Trendens riktning i löptext ("stigande trend"); `null` = för få punkter för en trend. */
internal fun trendSpoken(resources: Resources, direction: TrendDirection?): String = resources.getString(
    when (direction) {
        TrendDirection.RISING -> R.string.chart_a11y_trend_rising
        TrendDirection.FALLING -> R.string.chart_a11y_trend_falling
        TrendDirection.FLAT -> R.string.chart_a11y_trend_flat
        null -> R.string.chart_a11y_trend_unknown
    }
)

/** En series sammanfattning ("Energi: 6 värden, lägsta 3, högsta 8, senaste 7, stigande trend"). */
internal fun seriesSpoken(resources: Resources, label: String, summary: SeriesSummary?, trend: TrendDirection?): String = if (summary == null) {
    resources.getString(R.string.chart_a11y_empty, label)
} else {
    resources.getString(
        R.string.chart_a11y_summary,
        label,
        resources.getQuantityString(R.plurals.chart_a11y_values, summary.count, summary.count),
        formatChartValue(summary.min),
        formatChartValue(summary.max),
        formatChartValue(summary.last),
        trendSpoken(resources, trend)
    )
}
