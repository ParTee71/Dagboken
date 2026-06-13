package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiagramScreen(vm: DiagramViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val tabs = listOf("Energi", "Stress")
    val ranges = listOf(7, 14, 30)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Diagram") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Series selector — SegmentedButton is correct M3 for binary choice
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = state.selectedSeries == label,
                        onClick  = { vm.setSeries(label) },
                        shape    = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        label    = { Text(label) },
                    )
                }
            }

            // Range chips
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ranges.forEach { d ->
                    FilterChip(
                        selected = state.rangeDays == d,
                        onClick  = { vm.setRange(d) },
                        label    = { Text("$d dagar") },
                    )
                }
            }

            // Chart card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                if (state.stats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ingen data för vald period",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    val points = state.stats.map { s ->
                        when (state.selectedSeries) {
                            "Stress" -> s.avgStress
                            else     -> s.avgEnergy
                        }
                    }
                    val (minV, maxV) = when (state.selectedSeries) {
                        "Stress" -> 0f to 10f
                        else     -> -10f to 10f
                    }
                    LineChartCanvas(
                        series = listOf(
                            ChartSeries(
                                label  = state.selectedSeries,
                                color  = if (state.selectedSeries == "Stress")
                                    MaterialTheme.colorScheme.secondary
                                else
                                    MaterialTheme.colorScheme.primary,
                                points = points,
                            )
                        ),
                        minValue = minV,
                        maxValue = maxV,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }

            // Stats summary
            if (state.stats.isNotEmpty()) {
                val values = state.stats.mapNotNull {
                    if (state.selectedSeries == "Stress") it.avgStress else it.avgEnergy
                }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Sammanfattning", style = MaterialTheme.typography.titleSmall)
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StatItem(label = "Snitt", value = "%.1f".format(values.average()))
                            StatItem(label = "Min", value = "%.1f".format(values.min()))
                            StatItem(label = "Max", value = "%.1f".format(values.max()))
                            StatItem(label = "Dagar", value = values.size.toString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
