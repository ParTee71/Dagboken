package se.partee71.dagboken.ui.trender

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import se.partee71.dagboken.ui.diagram.ChartSeries
import se.partee71.dagboken.ui.diagram.DiagramLayout
import se.partee71.dagboken.ui.diagram.DiagramSection
import se.partee71.dagboken.ui.diagram.IntervalBarChart
import se.partee71.dagboken.ui.diagram.IntervalPoint
import se.partee71.dagboken.ui.diagram.LineChartCanvas
import se.partee71.dagboken.ui.diagram.MinMaxCaption
import se.partee71.dagboken.ui.diagram.computeSmartYAxis
import se.partee71.dagboken.ui.diagram.computeSmartYRange

@Composable
fun TrenderScreen(
    onBack: (() -> Unit)? = null,
    vm: TrenderViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    DiagramLayout(
        title  = stringResource(R.string.trender_title),
        onBack = onBack,
        periodSelector = {
            RangeSelector(selected = state.range, onSelect = vm::setRange)
        },
        sections = listOf(
            energyDailySection(state),
            categorySection(
                title    = stringResource(R.string.trender_section_energy_slots),
                category = TrenderCategory.ENERGI_TILLFALLE,
                state    = state,
                vm       = vm,
                testTag  = "trender_series_selector_energy",
            ),
            categorySection(
                title    = stringResource(R.string.trender_section_stress),
                category = TrenderCategory.STRESS_BELASTNING,
                state    = state,
                vm       = vm,
                testTag  = "trender_series_selector_stress",
            ),
            categorySection(
                title    = stringResource(R.string.trender_section_symptom),
                category = TrenderCategory.SYMPTOM,
                state    = state,
                vm       = vm,
                testTag  = "trender_series_selector_symptom",
            ),
            stepsSection(state),
            restingHeartRateSection(state),
        ),
    )
}

/** Stegdiagram (TRD-11, Health Connect) — samma data som Idag-kortets sparkline, ingen väljare. */
@Composable
private fun stepsSection(state: TrenderUiState): DiagramSection {
    val points = state.dailySteps.filter { it.steps > 0 }
    return DiagramSection(
        title = stringResource(R.string.trender_section_steps),
        chart = { chartModifier ->
            if (points.size < 2) {
                EmptyState(
                    icon     = Icons.Outlined.TrendingUp,
                    title    = stringResource(R.string.trender_no_steps_data),
                    modifier = chartModifier.height(200.dp),
                )
            } else {
                val values = points.map { it.steps.toFloat() }
                val yRange = remember(values) { computeSmartYRange(values) }
                LineChartCanvas(
                    series = listOf(
                        ChartSeries(
                            label  = stringResource(R.string.trender_section_steps),
                            color  = HEALTH_STEPS_COLOR,
                            points = values,
                        ),
                    ),
                    dates    = points.map { it.date.toString() },
                    minValue = yRange.start,
                    maxValue = yRange.endInclusive,
                    modifier = chartModifier.height(200.dp),
                )
            }
        },
        minMax = if (points.isEmpty()) null else {
            { MinMaxCaption(min = points.minOf { it.steps }.toFloat(), max = points.maxOf { it.steps }.toFloat()) }
        },
    )
}

/** Vilopulsdiagram (TRD-11, Health Connect) — samma data som Idag-kortets sparkline, ingen väljare. */
@Composable
private fun restingHeartRateSection(state: TrenderUiState): DiagramSection {
    val points = state.dailyRestingHeartRate.filter { it.bpm != null }
    return DiagramSection(
        title = stringResource(R.string.trender_section_resting_hr),
        chart = { chartModifier ->
            if (points.size < 2) {
                EmptyState(
                    icon     = Icons.Outlined.TrendingUp,
                    title    = stringResource(R.string.trender_no_resting_hr_data),
                    modifier = chartModifier.height(200.dp),
                )
            } else {
                val values = points.map { it.bpm!!.toFloat() }
                val yRange = remember(values) { computeSmartYRange(values) }
                LineChartCanvas(
                    series = listOf(
                        ChartSeries(
                            label  = stringResource(R.string.trender_section_resting_hr),
                            color  = HEALTH_RESTING_HR_COLOR,
                            points = values,
                        ),
                    ),
                    dates    = points.map { it.date.toString() },
                    minValue = yRange.start,
                    maxValue = yRange.endInclusive,
                    modifier = chartModifier.height(200.dp),
                )
            }
        },
        minMax = if (points.isEmpty()) null else {
            { MinMaxCaption(min = points.minOf { it.bpm!! }.toFloat(), max = points.maxOf { it.bpm!! }.toFloat()) }
        },
    )
}

@Composable
private fun energyDailySection(state: TrenderUiState): DiagramSection {
    val daily = state.dailyEnergy
    return DiagramSection(
        title = stringResource(R.string.trender_section_energy_daily),
        chart = { chartModifier ->
            if (daily.isEmpty()) {
                EmptyState(
                    icon     = Icons.Outlined.TrendingUp,
                    title    = stringResource(R.string.trender_no_energy_data),
                    modifier = chartModifier.height(200.dp),
                )
            } else {
                val yAxis = remember(daily) { computeSmartYAxis(daily.flatMap { listOf(it.min, it.max) }) }
                IntervalBarChart(
                    points   = daily.map { IntervalPoint(min = it.min, value = it.avg, max = it.max) },
                    dates    = daily.map { it.datum },
                    minValue = yAxis.range.start,
                    maxValue = yAxis.range.endInclusive,
                    gridStep = yAxis.step,
                    modifier = chartModifier,
                )
            }
        },
        minMax = if (daily.isEmpty()) null else {
            { MinMaxCaption(min = daily.minOf { it.min }, max = daily.maxOf { it.max }) }
        },
    )
}

@Composable
private fun categorySection(
    title: String,
    category: TrenderCategory,
    state: TrenderUiState,
    vm: TrenderViewModel,
    testTag: String,
): DiagramSection {
    val categoryLabels = state.allSeriesLabels.filter { categoryOf(it) == category }
    val sectionSeries = state.seriesFor(category)
    val allValues = sectionSeries.flatMap { it.points }.filterNotNull()

    return DiagramSection(
        title = title,
        selector = {
            SeriesSelector(
                labels        = categoryLabels,
                selected      = state.selectedSeries,
                symptomLabels = state.symptomLabels,
                onToggle      = vm::toggleSeries,
                testTag       = testTag,
            )
        },
        chart = { chartModifier ->
            if (sectionSeries.isEmpty()) {
                EmptyState(
                    icon     = Icons.Outlined.TrendingUp,
                    title    = stringResource(R.string.diagram_no_series),
                    modifier = chartModifier.height(280.dp),
                )
            } else {
                val yRange = remember(allValues) { computeSmartYRange(allValues) }
                LineChartCanvas(
                    series   = sectionSeries,
                    dates    = state.dates,
                    minValue = yRange.start,
                    maxValue = yRange.endInclusive,
                    modifier = chartModifier.height(280.dp),
                )
            }
        },
        legend = if (sectionSeries.isEmpty()) null else {
            {
                sectionSeries.forEach { s ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        // Väljarknappens etikett kan sammanfalla textmässigt med legendens
                        // (t.ex. exakt "Yrsel" i båda när det är den enda valda serien i
                        // kategorin) — egen testTag så legendraden går att peka ut entydigt.
                        modifier = Modifier.testTag("trender_legend_item_${s.label}"),
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
            }
        },
        minMax = if (allValues.isEmpty()) null else {
            { MinMaxCaption(min = allValues.min(), max = allValues.max()) }
        },
    )
}

@Composable
private fun RangeSelector(
    selected: TrenderRange,
    onSelect: (TrenderRange) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick  = { showMenu = true },
            modifier = Modifier.testTag("trender_range_selector"),
        ) {
            Text(stringResource(selected.labelRes), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            TrenderRange.entries.forEach { range ->
                DropdownMenuItem(
                    text = { Text(stringResource(range.labelRes)) },
                    onClick = {
                        onSelect(range)
                        showMenu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SeriesSelector(
    labels: List<String>,
    selected: Set<String>,
    symptomLabels: List<String>,
    onToggle: (String) -> Unit,
    testTag: String,
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.diagram_show_label), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Box {
            OutlinedButton(
                onClick  = { showMenu = true },
                modifier = Modifier.testTag(testTag),
            ) {
                val label = labels
                    .filter { it in selected }
                    .joinToString(", ")
                    .ifEmpty { "–" }
                Text(label, maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                labels.forEach { name ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked         = name in selected,
                                    onCheckedChange = { onToggle(name) },
                                )
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(trenderSeriesColor(name, symptomLabels)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(name)
                            }
                        },
                        onClick = { onToggle(name) },
                    )
                }
            }
        }
    }
}
