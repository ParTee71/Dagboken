package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.TrendDirection
import se.partee71.dagboken.core.engine.formatChartValue
import se.partee71.dagboken.ui.components.InfoPill
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Raden under **varje** diagram (TRD-9): datats faktiska lägsta och högsta värde som text – oavsett hur
 * y-axeln avrundats – och, när de finns, [average] ("Snitt") och dagens värde ([today], "Idag" i
 * sparklinen; [showToday] visar "Idag —" när dagen saknar värde). [trend] står som pill ("Trend
 * uppåt"), så att riktningen aldrig bara bärs av den streckade linjens färg.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinMaxCaption(
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    average: Float? = null,
    trend: TrendDirection? = null,
    today: Float? = null,
    showToday: Boolean = today != null,
) {
    val missing = stringResource(R.string.value_missing)
    val stats = buildList {
        add(stringResource(R.string.chart_stat_format, stringResource(R.string.chart_min), formatChartValue(min)))
        add(stringResource(R.string.chart_stat_format, stringResource(R.string.chart_max), formatChartValue(max)))
        average?.let { add(stringResource(R.string.chart_stat_format, stringResource(R.string.chart_average), formatChartValue(it))) }
        if (showToday) add(stringResource(R.string.chart_stat_format, stringResource(R.string.chart_today), today?.let(::formatChartValue) ?: missing))
    }
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stats.joinToString(SEPARATOR), style = AppTypography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trend?.let { InfoPill(trendText(it), tone = Tone.Neutral, icon = R.drawable.ic_trend) }
    }
}

@Composable
private fun trendText(direction: TrendDirection): String = stringResource(
    when (direction) {
        TrendDirection.RISING -> R.string.chart_trend_rising
        TrendDirection.FALLING -> R.string.chart_trend_falling
        TrendDirection.FLAT -> R.string.chart_trend_flat
    },
)

private const val SEPARATOR = " · "
