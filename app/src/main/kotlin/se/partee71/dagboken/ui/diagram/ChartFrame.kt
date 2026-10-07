package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.health.MIN_TREND_POINTS
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.theme.Spacing

/** Så många kända punkter behövs innan ett diagram ritas (TRD-13: en trend kräver två) – samma gräns som Idags trendrader (HEM-17). */
internal const val MIN_CHART_POINTS = MIN_TREND_POINTS

/**
 * Ramen kring varje diagram: med för lite data visas **ett** gemensamt tomt läge ([emptyTitle], "För lite
 * data än" om inte diagrammet säger annat, och [emptyHint] som uppmaning, `EmptyState` i kompakt form) och skärmläsaren
 * får hela budskapet som en text ("<[label]>: för lite data än. <uppmaningen>"); annars ritas [chart] med [footer] under (legend och
 * `MinMaxCaption`, TRD-9).
 */
@Composable
internal fun ChartFrame(
    enoughData: Boolean,
    label: String,
    emptyHint: String,
    modifier: Modifier = Modifier,
    emptyTitle: String = stringResource(R.string.chart_empty_title),
    footer: @Composable () -> Unit = {},
    chart: @Composable () -> Unit,
) {
    if (!enoughData) {
        // En nod för skärmläsaren med hela budskapet: "Vilopuls: för lite data än. <uppmaning>".
        val spoken = stringResource(R.string.chart_a11y_empty, label) + ". " + emptyHint
        EmptyState(
            icon = R.drawable.ic_trend,
            title = emptyTitle,
            message = emptyHint,
            modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
            compact = true,
        )
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        chart()
        footer()
    }
}
