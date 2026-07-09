package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.SectionHeader
import se.partee71.dagboken.ui.formatShortDate
import se.partee71.dagboken.ui.formatShortDateYear
import java.time.LocalDate

private val ALL_SERIES = listOf(
    "Energi Frukost", "Energi Lunch", "Energi Kvällsmat", "Energi Läggdags",
    "Stress", "Somatiska", "Återhämtande", "Energitjuv",
)

private val SERIES_PALETTE = listOf(
    Color(0xFF60a5fa),  // blue-400      (Energi Frukost)
    Color(0xFF34d399),  // emerald-400   (Energi Lunch)
    Color(0xFFfbbf24),  // amber-400     (Energi Kvällsmat)
    Color(0xFFa78bfa),  // violet-400    (Energi Läggdags)
    Color(0xFFfb923c),  // orange-400    (Stress)
    Color(0xFF4ade80),  // green-400     (Somatiska)
    Color(0xFFe879f9),  // fuchsia-400   (Återhämtande)
    Color(0xFFf472b6),  // pink-400      (Energitjuv)
)

private fun seriesColor(name: String): Color =
    SERIES_PALETTE.getOrElse(ALL_SERIES.indexOf(name)) { SERIES_PALETTE.last() }

@Composable
fun DiagramScreen(
    source: String = "hem",
    onBack: () -> Unit = {},
    vm: DiagramViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val ranges = listOf(7, 14, 30, 90)

    val screenTitle = when (source) {
        "aktiviteter" -> stringResource(R.string.diagram_title_aktiviteter)
        "mediciner"   -> stringResource(R.string.diagram_title_mediciner)
        else          -> stringResource(R.string.diagram_title)
    }

    val chartSeries = ALL_SERIES
        .filter { it in state.visibleSeries }
        .map { name ->
            ChartSeries(
                label  = name,
                color  = seriesColor(name),
                points = state.stats.map { day ->
                    when (name) {
                        "Energi Frukost"   -> day.avgEnergyFrukost
                        "Energi Lunch"     -> day.avgEnergyLunch
                        "Energi Kvällsmat" -> day.avgEnergyKvallsmat
                        "Energi Läggdags"  -> day.avgEnergyLaggdags
                        "Stress"           -> day.avgStress
                        "Somatiska"        -> day.avgSomatiska
                        "Återhämtande"     -> day.avgAterhamtande
                        "Energitjuv"       -> day.avgEnergitjuv
                        else               -> null
                    }
                },
            )
        }
    val allValues = chartSeries.flatMap { it.points }.filterNotNull()
    val minV = if (allValues.isEmpty()) 0f
               else minOf(0f, kotlin.math.floor(allValues.min().toDouble()).toFloat())
    val maxV = if (allValues.isEmpty()) 10f
               else maxOf(minV + 1f, kotlin.math.ceil(allValues.max().toDouble()).toFloat())
    val intGridValues = (minV.toInt()..maxV.toInt()).map { it.toFloat() }

    val dates = state.stats.map { it.datum }
    val periodText = remember(dates, state.rangeDays) {
        if (dates.isEmpty()) null
        else {
            val first = formatShortDate(LocalDate.parse(dates.first()))
            val last  = formatShortDateYear(LocalDate.parse(dates.last()))
            "${state.rangeDays} dagar  ·  $first – $last"
        }
    }

    DiagramLayout(
        title  = screenTitle,
        onBack = onBack,
        selector = {
            var showMenu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.diagram_show_label), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(onClick = { showMenu = true }) {
                        val label = ALL_SERIES
                            .filter { it in state.visibleSeries }
                            .joinToString(", ")
                            .ifEmpty { "–" }
                        Text(label)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        ALL_SERIES.forEach { series ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked         = series in state.visibleSeries,
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
            if (state.stats.isEmpty() || chartSeries.isEmpty()) {
                EmptyState(
                    icon     = Icons.Outlined.BarChart,
                    title    = if (state.stats.isEmpty()) stringResource(R.string.diagram_no_data)
                               else stringResource(R.string.diagram_no_series),
                    modifier = chartModifier,
                )
            } else {
                LineChartCanvas(
                    series     = chartSeries,
                    dates      = dates,
                    minValue   = minV,
                    maxValue   = maxV,
                    gridValues = intGridValues,
                    modifier   = chartModifier,
                )
            }
        },
        periodLabel = periodText?.let { text ->
            { Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        legend = {
            chartSeries.forEach { s ->
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
        portraitExtras = if (state.stats.isNotEmpty() && state.visibleSeries.isNotEmpty()) {
            {
                DagbokenCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionHeader(stringResource(R.string.diagram_summary))
                        HorizontalDivider()
                        ALL_SERIES.filter { it in state.visibleSeries }.forEachIndexed { i, series ->
                            if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            val values = state.stats.mapNotNull { day ->
                                when (series) {
                                    "Energi Frukost"   -> day.avgEnergyFrukost
                                    "Energi Lunch"     -> day.avgEnergyLunch
                                    "Energi Kvällsmat" -> day.avgEnergyKvallsmat
                                    "Energi Läggdags"  -> day.avgEnergyLaggdags
                                    "Stress"           -> day.avgStress
                                    "Somatiska"        -> day.avgSomatiska
                                    "Återhämtande"     -> day.avgAterhamtande
                                    "Energitjuv"       -> day.avgEnergitjuv
                                    else               -> null
                                }
                            }
                            if (values.isNotEmpty()) {
                                Text(series, style = MaterialTheme.typography.labelMedium, color = seriesColor(series))
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    StatItem(stringResource(R.string.diagram_stat_avg), "%.1f".format(values.average()))
                                    StatItem(stringResource(R.string.diagram_stat_min), "%.1f".format(values.min()))
                                    StatItem(stringResource(R.string.diagram_stat_max), "%.1f".format(values.max()))
                                    StatItem(stringResource(R.string.diagram_stat_days), values.size.toString())
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
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
