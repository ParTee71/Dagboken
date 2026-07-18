package se.partee71.dagboken.ui.trender

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.diagram.DiagramLayout
import se.partee71.dagboken.ui.diagram.LineChartCanvas
import se.partee71.dagboken.ui.diagram.computeSmartYRange

@Composable
fun TrenderScreen(
    onBack: (() -> Unit)? = null,
    vm: TrenderViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val ranges = listOf(7, 14, 30, 90)

    val allValues = state.series.flatMap { it.points }.filterNotNull()
    val yRange = computeSmartYRange(allValues)

    DiagramLayout(
        title  = stringResource(R.string.trender_title),
        onBack = onBack,
        selector = {
            var showMenu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.diagram_show_label), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(
                        onClick  = { showMenu = true },
                        modifier = Modifier.testTag("trender_series_selector"),
                    ) {
                        val label = state.allSeriesLabels
                            .filter { it in state.selectedSeries }
                            .joinToString(", ")
                            .ifEmpty { "–" }
                        Text(label, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        state.allSeriesLabels.forEach { name ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked         = name in state.selectedSeries,
                                            onCheckedChange = { vm.toggleSeries(name) },
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(trenderSeriesColor(name, state.symptomLabels)),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(name)
                                    }
                                },
                                onClick = { vm.toggleSeries(name) },
                            )
                        }
                    }
                }
            }
        },
        rangeChips = {
            ranges.forEach { d ->
                FilterChip(
                    selected = state.rangeDays == d,
                    onClick  = { vm.setRange(d) },
                    label    = { Text(stringResource(R.string.format_range_days, d)) },
                )
            }
        },
        chart = { chartModifier ->
            if (state.series.isEmpty()) {
                EmptyState(
                    icon     = Icons.Outlined.TrendingUp,
                    title    = stringResource(R.string.diagram_no_series),
                    modifier = chartModifier,
                )
            } else {
                LineChartCanvas(
                    series   = state.series,
                    dates    = state.dates,
                    minValue = yRange.start,
                    maxValue = yRange.endInclusive,
                    modifier = chartModifier,
                )
            }
        },
        legend = {
            state.series.forEach { s ->
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(s.color),
                    )
                    Text(s.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
    )
}
