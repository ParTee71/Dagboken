package se.partee71.dagboken.ui.diagram

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.SeriesSummary
import se.partee71.dagboken.core.engine.TrendDirection
import se.partee71.dagboken.core.engine.formatChartValue

// Diagrammens talbara sammanfattning (NFR-14): ett diagram är annars en rityta utan text för
// TalkBack. Beskrivningen bär det ett seende öga läser direkt – antal, lägsta, högsta, senaste och
// trendens riktning – och byggs bara här, så att alla diagram läses upp på samma sätt (regel 4).

/** Hela diagrammets beskrivning som en nod för skärmläsaren. */
internal fun Modifier.chartDescription(description: String): Modifier = semantics { contentDescription = description }

/** Trendens riktning i löptext ("stigande trend"); `null` = för få punkter för en trend. */
@Composable
@ReadOnlyComposable
internal fun trendSpoken(direction: TrendDirection?): String = stringResource(
    when (direction) {
        TrendDirection.RISING -> R.string.chart_a11y_trend_rising
        TrendDirection.FALLING -> R.string.chart_a11y_trend_falling
        TrendDirection.FLAT -> R.string.chart_a11y_trend_flat
        null -> R.string.chart_a11y_trend_unknown
    },
)

/** En series sammanfattning ("Energi: 6 värden, lägsta 3, högsta 8, senaste 7, stigande trend"). */
@Composable
internal fun seriesSpoken(label: String, summary: SeriesSummary?, trend: TrendDirection?): String =
    if (summary == null) {
        stringResource(R.string.chart_a11y_empty, label)
    } else {
        stringResource(
            R.string.chart_a11y_summary,
            label,
            pluralStringResource(R.plurals.chart_a11y_values, summary.count, summary.count),
            formatChartValue(summary.min),
            formatChartValue(summary.max),
            formatChartValue(summary.last),
            trendSpoken(trend),
        )
    }
