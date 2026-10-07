package se.partee71.dagboken.ui.trends

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.COMPARE_AXIS
import se.partee71.dagboken.core.engine.CompareKey
import se.partee71.dagboken.core.engine.EpisodeSpan
import se.partee71.dagboken.core.engine.SLEEP_STAGE_METRICS
import se.partee71.dagboken.core.engine.SeriesSummary
import se.partee71.dagboken.core.engine.SleepQualityKind
import se.partee71.dagboken.core.engine.StackedPoint
import se.partee71.dagboken.core.engine.StressSeries
import se.partee71.dagboken.core.engine.TrendDirection
import se.partee71.dagboken.core.engine.TrendRange
import se.partee71.dagboken.core.engine.TrendSerie
import se.partee71.dagboken.core.engine.WatchMetric
import se.partee71.dagboken.core.engine.WatchUnit
import se.partee71.dagboken.core.engine.computeTrendLine
import se.partee71.dagboken.core.engine.formatChartValue
import se.partee71.dagboken.core.engine.stackTotals
import se.partee71.dagboken.core.engine.summarize
import se.partee71.dagboken.core.engine.summarizeIntervals
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.data.health.HealthStatus
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.distinctSeriesColors
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.common.qualifiedLabel
import se.partee71.dagboken.ui.common.title
import se.partee71.dagboken.ui.components.AccountAvatar
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppMenuItem
import se.partee71.dagboken.ui.components.AppSegmentedChoice
import se.partee71.dagboken.ui.components.EntityDetailScreen
import se.partee71.dagboken.ui.components.Foldout
import se.partee71.dagboken.ui.components.SectionHeader
import se.partee71.dagboken.ui.components.SwitchRow
import se.partee71.dagboken.ui.diagram.CHART_SEPARATOR
import se.partee71.dagboken.ui.health.ClockStatus
import se.partee71.dagboken.ui.health.ClockUiState
import se.partee71.dagboken.ui.health.ClockViewModel
import se.partee71.dagboken.ui.health.HealthEvent
import se.partee71.dagboken.ui.health.HealthTodaySection
import se.partee71.dagboken.ui.diagram.ChartBand
import se.partee71.dagboken.ui.diagram.ChartSeries
import se.partee71.dagboken.ui.diagram.chartStatText
import se.partee71.dagboken.ui.diagram.CompactDropdownButton
import se.partee71.dagboken.ui.diagram.IntervalBarChart
import se.partee71.dagboken.ui.diagram.LineChart
import se.partee71.dagboken.ui.diagram.StackSegment
import se.partee71.dagboken.ui.diagram.StackedBarChart
import se.partee71.dagboken.ui.diagram.sleepStageColors
import se.partee71.dagboken.ui.diagram.sleepStageSegments
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

@Composable
fun TrendsRoute(
    account: AuthUser?,
    onAccount: () -> Unit,
    viewModel: TrendsViewModel = hiltViewModel(),
    clockViewModel: ClockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Hälsa idag läses bara medan Klocka visas – byter man grupp slutar läsningen (TRD-15).
    val clock = if (state.group == TrendGroup.WATCH) clockViewModel.state.collectAsStateWithLifecycle().value else null
    TrendsScreen(state, viewModel::onEvent, clock = clock, onClockEvent = clockViewModel::onEvent) {
        AccountAvatar(account?.name ?: account?.email, onAccount, photoUrl = account?.photoUrl)
    }
}

/**
 * Fliken Trender (TRD-19) på `EntityDetailScreen` i flikläge: stor rubrik med avataren (NAV-9) som övriga flikar,
 * segmentknappen Mående · Klocka · Jämför och, i varje grupp, ett ihopfällbart kort per diagram (TRD-14, NFR-18) med
 * periodväljare, serieval, "Föregående period", diagram och sammanfattning bara i utfällt läge. Klocka har Health
 * Connect-statusen överst ur [clock] – den enda källan för läget (`ClockStatus`: kopplad, ej kopplad, saknas,
 * uppdatering krävs, saknade valfria behörigheter – HLS-4, HLS-14, TRD-20; `null` = okänt, ingen status) –, med klockan
 * kopplad "Hälsa idag" (HLS-6) och sedan korten under rubriken Trender;
 * [onClockEvent] tar "Ge åtkomst", "Installera" och "Uppdatera". Jämför är ett enda kort (TRD-17).
 */
@Composable
fun TrendsScreen(
    state: TrendsUiState,
    onEvent: (TrendsEvent) -> Unit,
    modifier: Modifier = Modifier,
    clock: ClockUiState? = null,
    onClockEvent: (HealthEvent) -> Unit = {},
    avatar: @Composable () -> Unit = {},
) {
    EntityDetailScreen(
        state = DetailUiState.Content(state),
        header = null,
        onBack = null,
        modifier = modifier,
        title = stringResource(R.string.tab_trends),
        actions = { avatar() },
    ) { content ->
        AppSegmentedChoice(
            options = TrendGroup.entries.map { stringResource(it.label) },
            selectedIndex = content.group.ordinal,
            onSelect = { onEvent(TrendsEvent.ShowGroup(TrendGroup.entries[it])) },
        )
        if (content.group == TrendGroup.WATCH && clock != null) {
            ClockStatus(clock.status, clock.missing, onClockEvent)
            if (clock.status == HealthStatus.AVAILABLE) {
                HealthTodaySection(clock)
                SectionHeader(stringResource(R.string.tab_trends))
            }
        }
        TrendCard.inGroup(content.group).forEach { card -> TrendCardView(card, content.cards.getValue(card), onEvent) }
    }
}

@get:StringRes
private val TrendGroup.label: Int
    get() = when (this) {
        TrendGroup.MOOD -> R.string.trends_group_mood
        TrendGroup.WATCH -> R.string.trends_group_watch
        TrendGroup.COMPARE -> R.string.trends_group_compare
    }

@get:StringRes
private val TrendCard.title: Int
    get() = when (this) {
        TrendCard.ENERGY_DAY -> R.string.trends_card_energy_day
        TrendCard.ENERGY_OCCASION -> R.string.trends_card_energy_occasion
        TrendCard.STRESS -> R.string.trends_card_stress
        TrendCard.SYMPTOMS -> R.string.symptoms
        TrendCard.EVENTS_ILLNESS -> R.string.trends_card_events
        TrendCard.STEPS -> R.string.trends_card_steps
        TrendCard.HEART_RATE -> R.string.trends_card_heart_rate
        TrendCard.SLEEP -> R.string.trends_card_sleep
        TrendCard.SLEEP_STAGES -> R.string.trends_card_sleep_stages
        TrendCard.SLEEP_QUALITY -> R.string.trends_card_sleep_quality
        TrendCard.EXERCISE -> R.string.trends_card_exercise
        TrendCard.CALORIES -> R.string.trends_card_calories
        TrendCard.DISTANCE -> R.string.trends_card_distance
        TrendCard.OXYGEN -> R.string.trends_card_oxygen
        TrendCard.COMPARE -> R.string.trends_group_compare
    }

/** Det tomma lägets uppmaning per kort (TRD-11, TRD-15, som 3.x); dagbokens kort delar diagrammets allmänna. */
@get:StringRes
private val TrendCard.emptyHint: Int
    get() = when (this) {
        TrendCard.STEPS -> R.string.trends_no_steps_data
        TrendCard.HEART_RATE -> R.string.trends_no_heart_rate_data
        TrendCard.SLEEP -> R.string.trends_no_sleep_data
        TrendCard.SLEEP_STAGES -> R.string.trends_no_sleep_stages_data
        TrendCard.SLEEP_QUALITY -> R.string.trends_no_sleep_quality_data
        TrendCard.EXERCISE -> R.string.trends_no_exercise_data
        TrendCard.CALORIES -> R.string.trends_no_calories_data
        TrendCard.DISTANCE -> R.string.trends_no_distance_data
        TrendCard.OXYGEN -> R.string.trends_no_oxygen_data
        TrendCard.COMPARE -> R.string.trends_compare_hint
        else -> R.string.chart_empty_hint
    }

@get:StringRes
private val TrendRange.label: Int
    get() = when (this) {
        TrendRange.SEVEN_DAYS -> R.string.trends_range_7_days
        TrendRange.FOURTEEN_DAYS -> R.string.trends_range_14_days
        TrendRange.MONTH -> R.string.trends_range_month
        TrendRange.THREE_MONTHS -> R.string.trends_range_3_months
        TrendRange.ALL -> R.string.trends_range_all
    }

/**
 * Ett diagramkort (TRD-14): stängt bara titeln, chevronen och – när kortet lästs – sammanfattningen; utfällt
 * periodväljaren i titelraden (TRD-3), serieval (TRD-12), "Föregående period" (TRD-18), diagrammet och
 * sammanfattningen. Ett stängt kort komponerar inget diagram (TRD-15).
 */
@Composable
private fun TrendCardView(card: TrendCard, state: TrendCardState, onEvent: (TrendsEvent) -> Unit) {
    val controls = state.controls
    val summary = state.data?.let { summaryText(card, it) }
    AppCard {
        Foldout(
            title = stringResource(card.title),
            expanded = controls.expanded,
            onToggle = { onEvent(TrendsEvent.Toggle(card)) },
            trailing = if (controls.expanded) ({ RangePicker(card, controls.range, onEvent) }) else null,
            summary = summary,
        ) {
            val data = state.data
            // Serievalet finns först när kortet lästs – en tom meny säger ingenting.
            val available = (data as? CardData.Lines)?.available ?: (data as? CardData.Compare)?.available
            if (card.hasSeriesPicker && available != null) SeriesPicker(card, available, controls.selected, onEvent)
            if (controls.showsPrevious(card)) SwitchRow(stringResource(R.string.trends_previous_period), controls.previous, { onEvent(TrendsEvent.SetPrevious(card, it)) })
            if (data != null) Chart(card, data)
            if (summary != null && card == TrendCard.EVENTS_ILLNESS) {
                Text(summary, style = AppTypography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Periodväljaren (TRD-3, TRD-12): det valda med bock. */
@Composable
private fun RangePicker(card: TrendCard, range: TrendRange, onEvent: (TrendsEvent) -> Unit) {
    val items = TrendRange.entries.map { choice ->
        AppMenuItem(stringResource(choice.label), { onEvent(TrendsEvent.SetRange(card, choice)) }, icon = R.drawable.ic_check.takeIf { choice == range })
    }
    CompactDropdownButton(stringResource(range.label), items)
}

/**
 * Seriervalet "Visa:" (TRD-2, TRD-12): kryssrader i menyn, de valda i knappen. I Jämför (TRD-17) är menyn delad i
 * avsnitten Mående och Klocka.
 */
@Composable
private fun SeriesPicker(card: TrendCard, available: List<SeriesInfo>, selected: Set<String>, onEvent: (TrendsEvent) -> Unit) {
    val names = available.associate { it.key to seriesName(card, it) }
    val chosen = available.filter { it.key in selected }.joinToString(CHART_SEPARATOR) { names.getValue(it.key) }
    val sections = TrendGroup.entries.associateWith { stringResource(it.label) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.trends_show), style = AppTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CompactDropdownButton(
            chosen.ifEmpty { stringResource(R.string.trends_no_series) },
            available.map { info ->
                val section = if (card == TrendCard.COMPARE) sections[if (CompareKey.parse(info.key)?.fromWatch == true) TrendGroup.WATCH else TrendGroup.MOOD] else null
                AppMenuItem(names.getValue(info.key), { onEvent(TrendsEvent.ToggleSeries(card, info.key)) }, section = section, checked = info.key in selected)
            },
        )
    }
}

/**
 * Seriens namn: tillfället, stresserierna, klockmåtten och sömnkvalitetens delpoäng ur strängarna, symptomen ur
 * Listor (`SeriesInfo.name`) – aldrig ett rått id. Jämför kvalificerar klockans namn så de står för sig själva
 * ("Sömnlängd", inte "Total", TRD-17).
 */
@Composable
private fun seriesName(card: TrendCard, info: SeriesInfo): String = when (card) {
    TrendCard.ENERGY_OCCASION -> stringResource(Occasion.entries.firstOrNull { it.wire == info.key }?.label() ?: R.string.log_mood)
    TrendCard.STRESS -> stringResource(StressSeries.entries.firstOrNull { it.name == info.key }?.label ?: R.string.trends_series_stress)
    TrendCard.SYMPTOMS -> info.name ?: stringResource(R.string.symptoms)
    TrendCard.SLEEP_QUALITY -> stringResource(SleepQualityKind.entries.firstOrNull { it.name == info.key }?.label() ?: R.string.trends_series_sleep_score)
    TrendCard.COMPARE -> compareName(info)
    else -> stringResource(WatchMetric.entries.firstOrNull { it.name == info.key }?.label() ?: card.title)
}

@Composable
private fun compareName(info: SeriesInfo): String = when (val key = CompareKey.parse(info.key)) {
    CompareKey.EnergyDay -> stringResource(R.string.trends_compare_energy_day)
    is CompareKey.EnergyOccasion -> stringResource(key.occasion.label())
    is CompareKey.Stress -> stringResource(key.series.label)
    is CompareKey.Symptom -> info.name ?: stringResource(R.string.symptoms)
    is CompareKey.Watch -> stringResource(key.metric.qualifiedLabel())
    CompareKey.SleepQuality -> stringResource(R.string.trends_card_sleep_quality)
    null -> info.name ?: stringResource(R.string.symptoms)
}

@get:StringRes
private val StressSeries.label: Int
    get() = when (this) {
        StressSeries.STRESS -> R.string.trends_series_stress
        StressSeries.SOMATIC -> R.string.trends_series_somatic
        StressSeries.RECOVERING -> R.string.activity_recovering
        StressSeries.DRAIN -> R.string.activity_drain
    }

/**
 * En series färg i sitt eget kort: en ensam serie är teal (`null` = diagrammets kurvfärg); med flera får var och en
 * sin färg ur paletten efter platsen bland kortets serier – utom sömnstadierna, som har samma färger som i
 * Sömnstadier (`sleepStageColors`, TRD-15/TRD-16).
 */
private fun seriesColor(card: TrendCard, key: String, keys: List<String>, shownCount: Int, stageColors: List<Color>): Color? {
    val stage = SLEEP_STAGE_METRICS.indexOfFirst { it.name == key }
    return when {
        card == TrendCard.SLEEP && stage >= 0 -> stageColors[stage]
        shownCount > 1 -> AppColors.swatch(keys.indexOf(key).coerceAtLeast(0))
        else -> null
    }
}

/**
 * Jämför (TRD-17): serien behåller färgen från sitt eget kort, som om det kortet visade alla sina serier – krockar
 * löses av `distinctSeriesColors`. Symptomen räknas bland periodens symptom i menyn, som i Symptom-kortet.
 */
private fun compareColor(key: String, available: List<String>, stageColors: List<Color>): Color? = when (val parsed = CompareKey.parse(key)) {
    is CompareKey.EnergyOccasion -> seriesColor(TrendCard.ENERGY_OCCASION, parsed.occasion.wire, Occasion.entries.map { it.wire }, Occasion.entries.size, stageColors)
    is CompareKey.Stress -> seriesColor(TrendCard.STRESS, parsed.series.name, StressSeries.entries.map { it.name }, StressSeries.entries.size, stageColors)
    is CompareKey.Symptom -> {
        val symptoms = available.filter { CompareKey.parse(it) is CompareKey.Symptom }
        seriesColor(TrendCard.SYMPTOMS, key, symptoms, symptoms.size, stageColors)
    }
    is CompareKey.Watch -> {
        val home = TrendCard.entries.first { parsed.metric in it.metrics }
        seriesColor(home, parsed.metric.name, home.metrics.map { it.name }, home.metrics.size, stageColors)
    }
    CompareKey.EnergyDay, CompareKey.SleepQuality, null -> null
}

@Composable
private fun Chart(card: TrendCard, data: CardData) {
    val title = stringResource(card.title)
    val xLabels = data.days.map(DateFormat::short)
    when (data) {
        is CardData.EnergyDay -> IntervalBarChart(data.points, xLabels = xLabels, label = title)
        is CardData.Lines -> {
            val stageColors = sleepStageColors()
            val colorOf: (TrendSerie) -> Color? = { serie -> seriesColor(card, serie.key, data.available.map { it.key }, data.shown.size, stageColors) }
            val names = data.available.associate { it.key to seriesName(card, it) }
            val fallback = stringResource(R.string.symptoms)
            fun chartSeries(series: List<TrendSerie>) = series.map { ChartSeries(names[it.key] ?: fallback, it.points, colorOf(it)) }
            LineChart(
                chartSeries(data.shown),
                xLabels = xLabels,
                previous = chartSeries(data.previous),
                label = title,
                emptyHint = stringResource(
                    when {
                        data.needsBirthYear -> R.string.trends_sleep_quality_needs_birth_year
                        data.shown.isEmpty() && card.hasSeriesPicker -> R.string.trends_pick_series
                        else -> card.emptyHint
                    },
                ),
            )
        }
        is CardData.Stacked -> StackedBarChart(data.points, sleepStageSegments(), xLabels = xLabels, label = title, emptyHint = stringResource(card.emptyHint))
        is CardData.Compare -> {
            // Varje serie behåller färgen från sitt eget kort, utan krockar; etiketten bär det verkliga spannet med enhet (TRD-17).
            val colors = distinctSeriesColors(data.shown.map { compareColor(it.key, data.available.map { info -> info.key }, sleepStageColors()) }, MaterialTheme.colorScheme.primary)
            val series = data.shown.mapIndexed { i, serie ->
                val info = data.available.firstOrNull { it.key == serie.key } ?: SeriesInfo(serie.key, null)
                val unit = stringResource((CompareKey.parse(serie.key)?.unit ?: WatchUnit.SCALE).label())
                val legend = stringResource(R.string.trends_compare_legend_format, seriesName(card, info), formatChartValue(serie.min), formatChartValue(serie.max), unit)
                ChartSeries(legend, serie.points, colors[i])
            }
            val tooFewSelected = data.selectedCount < 2
            LineChart(
                if (series.size >= 2) series else emptyList(),
                xLabels = xLabels,
                label = title,
                emptyTitle = stringResource(if (tooFewSelected) R.string.trends_compare_pick_two else R.string.trends_compare_too_little),
                emptyHint = stringResource(if (tooFewSelected) card.emptyHint else R.string.trends_compare_too_little_hint),
                axis = COMPARE_AXIS,
                showCaption = false,
                footnote = listOfNotNull(
                    stringResource(R.string.trends_compare_footnote),
                    stringResource(R.string.trends_compare_sleep_quality_needs_birth_year).takeIf { data.needsBirthYear },
                ).joinToString(" "),
            )
        }
        is CardData.EventsIllness -> StackedBarChart(
            points = data.trend.events.map { StackedPoint(listOf(it)) },
            segments = listOf(StackSegment(stringResource(R.string.trends_legend_events), AppColors.tone(Tone.Warning).content)),
            xLabels = xLabels,
            label = title,
            emptyHint = stringResource(R.string.trends_events_hint),
            line = ChartSeries(stringResource(R.string.trends_legend_checkins), data.trend.checkins),
            bands = data.trend.episodes.map { ChartBand(it.from, it.to, it.episode.title()) },
        )
    }
}

/**
 * Kortets sammanfattning – under titeln i stängt läge (TRD-14) och, för Händelser och sjukdom, under diagrammet:
 * "Snitt 6,4 · Trend uppåt" för ett diagram med en serie, antalet valda med flera, och "4 händelser · snitt 5,5 ·
 * Förkylning 30 sep – pågår" (TRD-21). `null` när det inte finns något att säga.
 */
@Composable
private fun summaryText(card: TrendCard, data: CardData): String? = when (data) {
    is CardData.EnergyDay -> statsText(summarizeIntervals(data.points), computeTrendLine(data.points.map { it?.value })?.direction)
    is CardData.Lines -> when (data.shown.size) {
        0 -> if (card.hasSeriesPicker) stringResource(R.string.trends_pick_series) else null
        1 -> data.shown.single().let { statsText(summarize(it.points), computeTrendLine(it.points)?.direction) }
        else -> pluralStringResource(R.plurals.trends_series_count, data.shown.size, data.shown.size)
    }
    is CardData.Stacked -> stackTotals(data.points).let { statsText(summarize(it), computeTrendLine(it)?.direction) }
    is CardData.Compare -> when {
        data.selectedCount < 2 -> stringResource(R.string.trends_compare_pick_two)
        data.shown.size < 2 -> stringResource(R.string.trends_compare_too_little)
        else -> pluralStringResource(R.plurals.trends_series_count, data.shown.size, data.shown.size)
    }
    is CardData.EventsIllness -> {
        val trend = data.trend
        val parts = listOfNotNull(
            pluralStringResource(R.plurals.trends_event_count, trend.eventCount, trend.eventCount).takeIf { trend.eventCount > 0 || trend.episodes.isEmpty() },
            trend.averageSeverity?.let { stringResource(R.string.trends_average_format, formatChartValue(it)) },
        ) + trend.episodes.map { episodeText(it) }
        parts.takeIf { trend.eventCount > 0 || trend.episodes.isNotEmpty() }?.joinToString(CHART_SEPARATOR)
    }
}

/** "Snitt 6,4 · Trend uppåt" ur en serie – samma formatering som `MinMaxCaption`; `null` utan värden. */
@Composable
private fun statsText(summary: SeriesSummary?, trend: TrendDirection?): String? {
    summary ?: return null
    return listOfNotNull(
        chartStatText(LocalResources.current, R.string.chart_average, summary.average),
        trend?.let { stringResource(it.label()) },
    ).joinToString(CHART_SEPARATOR)
}

/** "Förkylning 30 sep – pågår" eller "Migrän 2 okt – 2 okt". */
@Composable
private fun episodeText(span: EpisodeSpan): String {
    val episode = span.episode
    val start = episode.start?.let(DateFormat::short).orEmpty()
    val end = episode.end
    return if (end == null) {
        stringResource(R.string.trends_episode_ongoing_format, episode.title(), start)
    } else {
        stringResource(R.string.trends_episode_ended_format, episode.title(), start, DateFormat.short(end))
    }
}
