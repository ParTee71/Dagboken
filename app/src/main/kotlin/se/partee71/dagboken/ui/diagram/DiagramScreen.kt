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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private val ALL_SERIES = listOf("Energi", "Stress")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiagramScreen(
    source: String = "hem",
    onBack: () -> Unit = {},
    vm: DiagramViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val ranges = listOf(7, 14, 30, 90)

    val screenTitle = when (source) {
        "aktiviteter" -> "Aktiviteter — Diagram"
        "mediciner"   -> "Mediciner — Diagram"
        else          -> "Diagram"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tillbaka")
                    }
                },
                title = { Text(screenTitle) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Multi-select series filter
            var showSeriesMenu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Visa:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(onClick = { showSeriesMenu = true }) {
                        val label = ALL_SERIES
                            .filter { it in state.visibleSeries }
                            .joinToString(", ")
                            .ifEmpty { "–" }
                        Text(label)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded        = showSeriesMenu,
                        onDismissRequest = { showSeriesMenu = false },
                    ) {
                        ALL_SERIES.forEach { series ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked        = series in state.visibleSeries,
                                            onCheckedChange = { vm.toggleSeries(series) },
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(series)
                                    }
                                },
                                onClick = { vm.toggleSeries(series) },
                            )
                        }
                    }
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
                val chartSeries = buildList {
                    if ("Energi" in state.visibleSeries) {
                        add(ChartSeries(
                            label  = "Energi",
                            color  = MaterialTheme.colorScheme.primary,
                            points = state.stats.map { it.avgEnergy },
                        ))
                    }
                    if ("Stress" in state.visibleSeries) {
                        add(ChartSeries(
                            label  = "Stress",
                            color  = MaterialTheme.colorScheme.secondary,
                            points = state.stats.map { it.avgStress },
                        ))
                    }
                }
                val minV = if ("Energi" in state.visibleSeries) -10f else 0f

                if (state.stats.isEmpty() || chartSeries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector        = Icons.Outlined.BarChart,
                                contentDescription = null,
                                modifier           = Modifier.size(40.dp),
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (state.stats.isEmpty()) "Ingen data för vald period"
                                else "Välj minst en dataserie",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LineChartCanvas(
                        series   = chartSeries,
                        minValue = minV,
                        maxValue = 10f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(280.dp),
                    )
                }
            }

            // Stats summary — one block per visible series
            if (state.stats.isNotEmpty() && state.visibleSeries.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier            = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Sammanfattning", style = MaterialTheme.typography.titleSmall)
                        HorizontalDivider()
                        ALL_SERIES.filter { it in state.visibleSeries }.forEachIndexed { i, series ->
                            if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            val values = state.stats.mapNotNull {
                                if (series == "Stress") it.avgStress else it.avgEnergy
                            }
                            if (values.isNotEmpty()) {
                                val seriesColor = if (series == "Stress")
                                    MaterialTheme.colorScheme.secondary
                                else
                                    MaterialTheme.colorScheme.primary
                                Text(series, style = MaterialTheme.typography.labelMedium,
                                    color = seriesColor)
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    StatItem(label = "Snitt", value = "%.1f".format(values.average()))
                                    StatItem(label = "Min",   value = "%.1f".format(values.min()))
                                    StatItem(label = "Max",   value = "%.1f".format(values.max()))
                                    StatItem(label = "Dagar", value = values.size.toString())
                                }
                            }
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
            text  = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
