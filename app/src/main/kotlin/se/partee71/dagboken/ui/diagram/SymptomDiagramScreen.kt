package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.R

private val SYMPTOM_GRID_VALUES = listOf(0f, 1f, 2f, 3f, 4f, 5f)

@Composable
fun SymptomDiagramScreen(
    onBack: () -> Unit = {},
    vm: SymptomDiagramViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val ranges = listOf(7, 14, 30, 90)

    DiagramLayout(
        title  = stringResource(R.string.symptom_diagram_title),
        onBack = onBack,
        selector = {
            var showDropdown by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_symptom), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(onClick = { showDropdown = true }) {
                        val label = state.allSymptoms
                            .filter { it in state.selectedSymptoms }
                            .joinToString(", ")
                            .ifEmpty { "–" }
                        Text(label, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                        state.allSymptoms.forEach { name ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked         = name in state.selectedSymptoms,
                                            onCheckedChange = { vm.toggleSymptom(name) },
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(symptomColor(name, state.allSymptoms)),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(name)
                                    }
                                },
                                onClick = { vm.toggleSymptom(name) },
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
            if (state.days.isEmpty() || state.selectedSymptoms.isEmpty()) {
                Box(modifier = chartModifier, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector        = Icons.Outlined.Healing,
                            contentDescription = null,
                            modifier           = Modifier.size(40.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = if (state.days.isEmpty()) stringResource(R.string.symptom_diagram_no_data)
                                    else stringResource(R.string.symptom_diagram_no_series),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LineChartCanvas(
                    series     = state.series,
                    dates      = state.days,
                    minValue   = 0f,
                    maxValue   = 5f,
                    gridValues = SYMPTOM_GRID_VALUES,
                    modifier   = chartModifier,
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
        portraitExtras = if (state.series.isNotEmpty() && state.days.isNotEmpty()) {
            {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier            = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.diagram_summary), style = MaterialTheme.typography.titleSmall)
                        HorizontalDivider()
                        state.series.forEachIndexed { index, s ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            val values = s.points.filterNotNull()
                            if (values.isNotEmpty()) {
                                Text(s.label, style = MaterialTheme.typography.labelMedium, color = s.color)
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    SymptomStatItem(stringResource(R.string.diagram_stat_avg), "%.1f".format(values.average()))
                                    SymptomStatItem(stringResource(R.string.diagram_stat_min), "%.1f".format(values.min()))
                                    SymptomStatItem(stringResource(R.string.diagram_stat_max), "%.1f".format(values.max()))
                                    SymptomStatItem(stringResource(R.string.diagram_stat_days), values.size.toString())
                                }
                            }
                        }
                    }
                }
            }
        } else null,
    )
}

@Composable
private fun SymptomStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
