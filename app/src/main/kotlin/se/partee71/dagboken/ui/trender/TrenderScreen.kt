package se.partee71.dagboken.ui.trender

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.diagram.CompactDropdownButton
import se.partee71.dagboken.ui.diagram.DiagramLayout
import se.partee71.dagboken.ui.diagram.DiagramSection
import se.partee71.dagboken.ui.diagram.IntervalBarChart
import se.partee71.dagboken.ui.diagram.IntervalPoint
import se.partee71.dagboken.ui.diagram.LineChartCanvas
import se.partee71.dagboken.ui.diagram.MinMaxCaption
import se.partee71.dagboken.ui.diagram.StackSegment
import se.partee71.dagboken.ui.diagram.StackedBarChart
import se.partee71.dagboken.ui.diagram.StackedPoint
import se.partee71.dagboken.ui.diagram.stackTotals
import se.partee71.dagboken.ui.diagram.computeSmartYAxis
import se.partee71.dagboken.ui.diagram.computeTrendLine

@Composable
fun TrenderScreen(
    onBack: (() -> Unit)? = null,
    vm: TrenderViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    DiagramLayout(
        title  = stringResource(R.string.trender_title),
        onBack = onBack,
        sections = listOf(
            energyDailySection(state, vm),
            categorySection(
                title    = stringResource(R.string.trender_section_energy_slots),
                category = TrenderCategory.ENERGI_TILLFALLE,
                section  = TrenderSection.ENERGI_TILLFALLE,
                state    = state,
                vm       = vm,
                testTag  = "trender_series_selector_energy",
            ),
            categorySection(
                title    = stringResource(R.string.trender_section_stress),
                category = TrenderCategory.STRESS_BELASTNING,
                section  = TrenderSection.STRESS_BELASTNING,
                state    = state,
                vm       = vm,
                testTag  = "trender_series_selector_stress",
            ),
            categorySection(
                title    = stringResource(R.string.trender_section_symptom),
                category = TrenderCategory.SYMPTOM,
                section  = TrenderSection.SYMPTOM,
                state    = state,
                vm       = vm,
                testTag  = "trender_series_selector_symptom",
            ),
            // Hälsodiagrammen (TRD-11/TRD-15) sist — ett diagram per enhet, så serier med
            // olika enheter aldrig delar y-skala och plattar ut varandra.
            healthSection(TrenderSection.STEG, R.string.trender_section_steps, R.string.trender_no_steps_data, state, vm),
            healthSection(TrenderSection.VILOPULS, R.string.trender_section_resting_hr, R.string.trender_no_resting_hr_data, state, vm),
            healthSection(TrenderSection.SOMN, R.string.trender_section_sleep, R.string.trender_no_sleep_data, state, vm),
            sleepStagesSection(state, vm),
            healthSection(TrenderSection.SOMNKVALITET, R.string.trender_section_sleep_quality, R.string.trender_no_sleep_quality_data, state, vm),
            healthSection(TrenderSection.TRANING, R.string.trender_section_exercise, R.string.trender_no_exercise_data, state, vm),
            healthSection(TrenderSection.KALORIER, R.string.trender_section_kcal, R.string.trender_no_kcal_data, state, vm),
            healthSection(TrenderSection.STRACKA, R.string.trender_section_distance, R.string.trender_no_distance_data, state, vm),
            healthSection(TrenderSection.SYREMATTNAD, R.string.trender_section_spo2, R.string.trender_no_spo2_data, state, vm),
            healthSection(TrenderSection.BLODTRYCK, R.string.trender_section_blood_pressure, R.string.trender_no_blood_pressure_data, state, vm),
            comparisonSection(state, vm),
        ),
    )
}

/**
 * Ett hälsodiagram (TRD-11/TRD-15). Alla nio delar samma byggare — de skiljer sig bara i
 * vilka serier de visar, och den skillnaden bor i [TrenderViewModel]s [HealthTrend]. Formen
 * är densamma som dagboksdiagrammens: egen periodväljare (TRD-3), serieväljare när
 * diagrammet har flera serier, smart y-axel (TRD-7), min/max (TRD-9), trendlinje (TRD-13)
 * och tomläge när perioden saknar data (#146).
 */
@Composable
private fun healthSection(
    section: TrenderSection,
    @StringRes titleRes: Int,
    @StringRes emptyRes: Int,
    state: TrenderUiState,
    vm: TrenderViewModel,
): DiagramSection {
    val trend = state.healthTrends[section] ?: HealthTrend()
    val values = trend.series.flatMap { it.points }.filterNotNull()
    val loading = section in state.healthLoading
    val tag = section.name.lowercase()

    return DiagramSection(
        title = stringResource(titleRes),
        expanded = state.expanded.getValue(section),
        onToggleExpanded = { vm.setExpanded(section, !state.expanded.getValue(section)) },
        periodSelector = { sectionRangeSelector(section, state, vm) },
        selector = if (trend.labels.isEmpty()) {
            null
        } else {
            {
                HealthSeriesSelector(
                    labels   = trend.labels,
                    selected = state.selectedHealthSeries[section].orEmpty(),
                    onToggle = { vm.toggleHealthSeries(section, it) },
                    colorOf  = { label -> trend.colorFor(label, section) },
                    testTag  = "trender_series_selector_$tag",
                )
            }
        },
        chart = { chartModifier ->
            when {
                loading -> Box(
                    modifier = chartModifier.height(200.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                values.size < 2 -> EmptyState(
                    icon     = Icons.Outlined.TrendingUp,
                    title    = stringResource(emptyRes),
                    modifier = chartModifier.height(200.dp),
                )

                else -> {
                    val yAxis = remember(values) { computeSmartYAxis(values) }
                    LineChartCanvas(
                        series   = trend.series,
                        dates    = trend.dates,
                        minValue = yAxis.range.start,
                        maxValue = yAxis.range.endInclusive,
                        gridStep = yAxis.step,
                        modifier = chartModifier.height(if (trend.labels.isEmpty()) 200.dp else 280.dp),
                    )
                }
            }
        },
        legend = if (trend.series.size < 2) {
            null
        } else {
            {
                trend.series.forEach { serie ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("trender_legend_item_${serie.label}"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(serie.color),
                        )
                        Text(serie.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        minMax = if (values.isEmpty() || loading) null else {
            { MinMaxCaption(min = values.min(), max = values.max()) }
        },
    )
}

/**
 * Jämförelsediagrammet (TRD-17) — två eller flera valfria serier ur hela appen i samma
 * diagram, klockdata och dagboksserier om vartannat.
 *
 * Serierna har olika enheter, så var och en **indexeras 0–100 mot sitt eget** min/max i
 * perioden; en rak överlagring skulle platta ut den mindre serien mot botten. Y-axeln visar
 * index, men legenden bär varje series **verkliga** lägsta och högsta värde med enhet — ett
 * indexerat diagram som inte visar vad kurvorna betyder vore missvisande, och det här är
 * hälsodata.
 */
@Composable
private fun comparisonSection(state: TrenderUiState, vm: TrenderViewModel): DiagramSection {
    val section = TrenderSection.JAMFOR
    val comparison = state.comparison
    val selected = state.selectedHealthSeries[section].orEmpty()

    return DiagramSection(
        title = stringResource(R.string.trender_section_compare),
        expanded = state.expanded.getValue(section),
        onToggleExpanded = { vm.setExpanded(section, !state.expanded.getValue(section)) },
        periodSelector = { sectionRangeSelector(section, state, vm) },
        selector = {
            HealthSeriesSelector(
                labels   = comparison.labels,
                selected = selected,
                onToggle = { vm.toggleHealthSeries(section, it) },
                colorOf  = { label -> comparison.colorFor(label) },
                testTag  = "trender_series_selector_jamfor",
            )
        },
        chart = { chartModifier ->
            if (comparison.series.count { it.points.any { point -> point != null } } < 2) {
                EmptyState(
                    icon     = Icons.Outlined.TrendingUp,
                    title    = stringResource(R.string.trender_compare_pick_two),
                    modifier = chartModifier.height(280.dp),
                )
            } else {
                LineChartCanvas(
                    series   = comparison.series,
                    dates    = comparison.dates,
                    // Fast 0–100: axeln visar index, inte enheter. Att smart-skala den vore
                    // meningslöst när varje serie redan är indexerad mot sitt eget spann.
                    minValue = 0f,
                    maxValue = 100f,
                    gridStep = 25f,
                    modifier = chartModifier.height(280.dp),
                )
            }
        },
        legend = if (comparison.legend.size < 2) null else {
            {
                comparison.legend.forEach { item ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("trender_compare_legend_${item.label}"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(item.color),
                        )
                        Text(
                            text  = stringResource(
                                R.string.trender_compare_legend_range,
                                item.label,
                                formatCompareValue(item.min),
                                formatCompareValue(item.max),
                                item.unit,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        },
    )
}

/** "5" för heltal, annars en decimal — samma format som diagrammens övriga etiketter. */
private fun formatCompareValue(value: Float): String =
    if (value == value.toInt().toFloat()) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.ROOT, "%.1f", value)
    }

private fun ComparisonTrend.colorFor(label: String): Color =
    series.firstOrNull { it.label == label }?.color
        ?: legend.firstOrNull { it.label == label }?.color
        ?: Color.Gray

/**
 * Sömnstadier som staplat diagram (TRD-16) — en stapel per natt, delad i djup, REM, lätt och
 * vaken. Egen byggare i stället för [healthSection], eftersom sammansättningen kräver en
 * staplad stapel och inte överlagrade linjer: två lika långa nätter kan ha helt olika
 * arkitektur, och det syns i stapeln men inte i fyra linjer ovanpå varandra.
 *
 * Datat kommer från samma [HealthTrend] som övriga hälsodiagram — de fyra stadieserierna
 * transponeras till en [StackedPoint] per natt. Stapelns total är därmed **tiden i säng**
 * (vaken tid ingår), medan Sömn-diagrammets "Total" är sömnlängden.
 */
@Composable
private fun sleepStagesSection(state: TrenderUiState, vm: TrenderViewModel): DiagramSection {
    val section = TrenderSection.SOMNSTADIER
    val trend = state.healthTrends[section] ?: HealthTrend()
    val loading = section in state.healthLoading

    val nights = remember(trend) {
        trend.dates.indices.map { i -> StackedPoint(trend.series.map { it.points.getOrNull(i) }) }
    }
    val segments = remember(trend) { trend.series.map { StackSegment(it.label, it.color) } }
    val totals = remember(nights) { stackTotals(nights).filterNotNull() }
    val title = stringResource(R.string.trender_section_sleep_stages)

    return DiagramSection(
        title = title,
        expanded = state.expanded.getValue(section),
        onToggleExpanded = { vm.setExpanded(section, !state.expanded.getValue(section)) },
        periodSelector = { sectionRangeSelector(section, state, vm) },
        chart = { chartModifier ->
            when {
                loading -> Box(
                    modifier = chartModifier.height(200.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                totals.size < 2 -> EmptyState(
                    icon     = Icons.Outlined.TrendingUp,
                    title    = stringResource(R.string.trender_no_sleep_stages_data),
                    modifier = chartModifier.height(200.dp),
                )

                else -> {
                    val yAxis = remember(totals) { computeSmartYAxis(totals) }
                    StackedBarChart(
                        points   = nights,
                        segments = segments,
                        dates    = trend.dates,
                        label    = title,
                        minValue = yAxis.range.start,
                        maxValue = yAxis.range.endInclusive,
                        gridStep = yAxis.step,
                        modifier = chartModifier,
                    )
                }
            }
        },
        legend = if (segments.isEmpty() || loading) null else {
            {
                segments.forEach { segment ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("trender_stage_legend_item_${segment.label}"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(segment.color),
                        )
                        Text(segment.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        minMax = if (totals.isEmpty() || loading) null else {
            { MinMaxCaption(min = totals.min(), max = totals.max()) }
        },
    )
}

/** Färgen för en etikett i väljaren — vald serie har sin egen, ovald tar seriedefinitionens. */
private fun HealthTrend.colorFor(label: String, section: TrenderSection): Color =
    series.firstOrNull { it.label == label }?.color
        ?: healthSeriesFor(section).firstOrNull { it.label == label }?.color
        ?: sleepQualitySeriesColor(label)

/**
 * Serieväljare för ett hälsodiagram — samma kompakta dropdown och kryssrutor som
 * dagboksdiagrammens (TRD-12), fast med sitt eget val per diagram i stället för Trenders
 * gemensamma serieurval (som hör ihop med de loggade kategorierna och deras symptomserier).
 */
@Composable
private fun HealthSeriesSelector(
    labels: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    colorOf: (String) -> Color,
    testTag: String,
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.diagram_show_label), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Box {
            CompactDropdownButton(
                label    = labels.filter { it in selected }.joinToString(", ").ifEmpty { "–" },
                onClick  = { showMenu = true },
                modifier = Modifier.testTag(testTag),
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                labels.forEach { name ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = name in selected, onCheckedChange = { onToggle(name) })
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colorOf(name)),
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

@Composable
private fun energyDailySection(state: TrenderUiState, vm: TrenderViewModel): DiagramSection {
    val daily = state.dailyEnergy
    return DiagramSection(
        title = stringResource(R.string.trender_section_energy_daily),
        expanded = state.expanded.getValue(TrenderSection.ENERGI_DAG),
        onToggleExpanded = { vm.setExpanded(TrenderSection.ENERGI_DAG, !state.expanded.getValue(TrenderSection.ENERGI_DAG)) },
        periodSelector = { sectionRangeSelector(TrenderSection.ENERGI_DAG, state, vm) },
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
    section: TrenderSection,
    state: TrenderUiState,
    vm: TrenderViewModel,
    testTag: String,
): DiagramSection {
    val trend = state.categoryTrends[category] ?: CategoryTrend()
    val symptomLabels = state.categoryTrends[TrenderCategory.SYMPTOM]?.labels.orEmpty()
    val allValues = trend.series.flatMap { it.points }.filterNotNull()

    return DiagramSection(
        title = title,
        expanded = state.expanded.getValue(section),
        onToggleExpanded = { vm.setExpanded(section, !state.expanded.getValue(section)) },
        periodSelector = { sectionRangeSelector(section, state, vm) },
        selector = {
            SeriesSelector(
                labels        = trend.labels,
                selected      = state.selectedSeries,
                symptomLabels = symptomLabels,
                onToggle      = vm::toggleSeries,
                testTag       = testTag,
            )
        },
        chart = { chartModifier ->
            if (trend.series.isEmpty()) {
                EmptyState(
                    icon     = Icons.Outlined.TrendingUp,
                    title    = stringResource(R.string.diagram_no_series),
                    modifier = chartModifier.height(280.dp),
                )
            } else {
                val yAxis = remember(allValues) { computeSmartYAxis(allValues) }
                LineChartCanvas(
                    series   = trend.series,
                    dates    = trend.dates,
                    minValue = yAxis.range.start,
                    maxValue = yAxis.range.endInclusive,
                    gridStep = yAxis.step,
                    modifier = chartModifier.height(280.dp),
                )
            }
        },
        legend = if (trend.series.isEmpty()) null else {
            {
                trend.series.forEach { s ->
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
                // Trendlinjen (TRD-13) ritas i varje serie som har minst 2 kända
                // punkter — en gemensam legendrad räcker för att förklara den streckade linjen.
                if (trend.series.any { computeTrendLine(it.points) != null }) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("trender_legend_item_trend"),
                    ) {
                        Text(
                            stringResource(R.string.diagram_legend_trend),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        minMax = if (allValues.isEmpty()) null else {
            { MinMaxCaption(min = allValues.min(), max = allValues.max()) }
        },
    )
}

/** Periodväljare för en enskild diagramsektion (#149) — visas i kortets övre högra hörn. */
@Composable
private fun sectionRangeSelector(section: TrenderSection, state: TrenderUiState, vm: TrenderViewModel) {
    RangeSelector(
        selected = state.ranges.getValue(section),
        onSelect = { vm.setRange(section, it) },
        testTag  = "trender_range_selector_${section.name.lowercase()}",
    )
}

@Composable
private fun RangeSelector(
    selected: TrenderRange,
    onSelect: (TrenderRange) -> Unit,
    testTag: String,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        CompactDropdownButton(
            label    = stringResource(selected.labelRes),
            onClick  = { showMenu = true },
            modifier = Modifier.testTag(testTag),
        )
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
            val label = labels
                .filter { it in selected }
                .joinToString(", ")
                .ifEmpty { "–" }
            CompactDropdownButton(
                label    = label,
                onClick  = { showMenu = true },
                modifier = Modifier.testTag(testTag),
            )
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
