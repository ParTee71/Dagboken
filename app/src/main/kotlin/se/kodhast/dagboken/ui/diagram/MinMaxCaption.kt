package se.kodhast.dagboken.ui.diagram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import se.kodhast.dagboken.R
import se.kodhast.dagboken.core.engine.TrendDirection
import se.kodhast.dagboken.core.engine.formatChartValue
import se.kodhast.dagboken.ui.common.label
import se.kodhast.dagboken.ui.theme.AppTypography
import se.kodhast.dagboken.ui.theme.Neutral
import se.kodhast.ui.components.InfoPill
import se.kodhast.ui.theme.Spacing
import se.kodhast.ui.theme.Tone

/**
 * Raden under **varje** diagram (TRD-9): datats faktiska lägsta och högsta värde som text – oavsett hur
 * y-axeln avrundats – och, när de finns, [average] ("Snitt") och dagens värde ([today], "Idag" i
 * sparklinen; [showToday] visar "Idag —" när dagen saknar värde). [trend] står som pill ("Trend
 * uppåt"), så att riktningen aldrig bara bärs av den streckade linjens färg. [scope] sätter vilken serie
 * raden gäller när diagrammet har flera slags serier ("Händelser: Lägst 4 · Högst 7", TRD-21).
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
    scope: String? = null
) {
    val resources = LocalResources.current
    val missing = stringResource(R.string.value_missing)
    val stats = buildList {
        add(chartStatText(resources, R.string.chart_min, min))
        add(chartStatText(resources, R.string.chart_max, max))
        average?.let { add(chartStatText(resources, R.string.chart_average, it)) }
        if (showToday) add(stringResource(R.string.chart_stat_format, stringResource(R.string.chart_today), today?.let(::formatChartValue) ?: missing))
    }
    val text = stats.joinToString(CHART_SEPARATOR).let { if (scope == null) it else stringResource(R.string.chart_caption_scope_format, scope, it) }
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = AppTypography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trend?.let { InfoPill(stringResource(it.label()), tone = Tone.Neutral, icon = R.drawable.ic_trend) }
    }
}
