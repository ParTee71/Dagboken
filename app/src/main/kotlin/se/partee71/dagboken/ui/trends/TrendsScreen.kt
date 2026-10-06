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
import se.partee71.dagboken.core.engine.EpisodeSpan
import se.partee71.dagboken.core.engine.SeriesSummary
import se.partee71.dagboken.core.engine.StackedPoint
import se.partee71.dagboken.core.engine.StressSeries
import se.partee71.dagboken.core.engine.TrendDirection
import se.partee71.dagboken.core.engine.TrendRange
import se.partee71.dagboken.core.engine.TrendSerie
import se.partee71.dagboken.core.engine.computeTrendLine
import se.partee71.dagboken.core.engine.formatChartValue
import se.partee71.dagboken.core.engine.summarize
import se.partee71.dagboken.core.engine.summarizeIntervals
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.common.title
import se.partee71.dagboken.ui.components.AccountAvatar
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppMenuItem
import se.partee71.dagboken.ui.components.AppSegmentedChoice
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.EntityDetailScreen
import se.partee71.dagboken.ui.components.Foldout
import se.partee71.dagboken.ui.components.SwitchRow
import se.partee71.dagboken.ui.diagram.CHART_SEPARATOR
import se.partee71.dagboken.ui.diagram.ChartBand
import se.partee71.dagboken.ui.diagram.ChartSeries
import se.partee71.dagboken.ui.diagram.chartStatText
import se.partee71.dagboken.ui.diagram.CompactDropdownButton
import se.partee71.dagboken.ui.diagram.IntervalBarChart
import se.partee71.dagboken.ui.diagram.LineChart
import se.partee71.dagboken.ui.diagram.StackSegment
import se.partee71.dagboken.ui.diagram.StackedBarChart
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

@Composable
fun TrendsRoute(account: AuthUser?, onAccount: () -> Unit, viewModel: TrendsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrendsScreen(state, viewModel::onEvent) {
        AccountAvatar(account?.name ?: account?.email, onAccount, photoUrl = account?.photoUrl)
    }
}

/**
 * Fliken Trender (TRD-19) på `EntityDetailScreen` i flikläge: stor rubrik med avataren (NAV-9) som övriga flikar,
 * segmentknappen Mående · Klocka · Jämför och – i Mående – ett ihopfällbart kort per diagram (TRD-14, NFR-18) med
 * periodväljare, serieval, "Föregående period", diagram och sammanfattning bara i utfällt läge. Klocka och Jämför
 * visar "Snart här" tills #267.
 */
@Composable
fun TrendsScreen(state: TrendsUiState, onEvent: (TrendsEvent) -> Unit, modifier: Modifier = Modifier, avatar: @Composable () -> Unit = {}) {
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
        when (content.group) {
            TrendGroup.MOOD -> TrendCard.entries.forEach { card -> TrendCardView(card, content.cards.getValue(card), onEvent) }
            TrendGroup.WATCH -> EmptyState(R.drawable.ic_clock, stringResource(R.string.tab_upcoming_title), stringResource(R.string.trends_watch_upcoming))
            TrendGroup.COMPARE -> EmptyState(R.drawable.ic_trend, stringResource(R.string.tab_upcoming_title), stringResource(R.string.trends_compare_upcoming))
        }
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
            if (card.hasSeriesPicker && data is CardData.Lines) SeriesPicker(card, data.available, controls.selected, onEvent)
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

/** Seriervalet "Visa:" (TRD-2, TRD-12): kryssrader i menyn, de valda i knappen. */
@Composable
private fun SeriesPicker(card: TrendCard, available: List<SeriesInfo>, selected: Set<String>, onEvent: (TrendsEvent) -> Unit) {
    val names = available.associate { it.key to seriesName(card, it) }
    val chosen = available.filter { it.key in selected }.joinToString(CHART_SEPARATOR) { names.getValue(it.key) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.trends_show), style = AppTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CompactDropdownButton(
            chosen.ifEmpty { stringResource(R.string.trends_no_series) },
            available.map { info -> AppMenuItem(names.getValue(info.key), { onEvent(TrendsEvent.ToggleSeries(card, info.key)) }, checked = info.key in selected) },
        )
    }
}

/** Seriens namn: tillfället och stresserierna ur strängarna, symptomen ur Listor (`SeriesInfo.name`) – aldrig ett rått id. */
@Composable
private fun seriesName(card: TrendCard, info: SeriesInfo): String = when (card) {
    TrendCard.ENERGY_OCCASION -> stringResource(Occasion.entries.firstOrNull { it.wire == info.key }?.label() ?: R.string.log_mood)
    TrendCard.STRESS -> stringResource(StressSeries.entries.firstOrNull { it.name == info.key }?.label ?: R.string.trends_series_stress)
    else -> info.name ?: stringResource(R.string.symptoms)
}

@get:StringRes
private val StressSeries.label: Int
    get() = when (this) {
        StressSeries.STRESS -> R.string.trends_series_stress
        StressSeries.SOMATIC -> R.string.trends_series_somatic
        StressSeries.RECOVERING -> R.string.trends_series_recovering
        StressSeries.DRAIN -> R.string.trends_series_drain
    }

@Composable
private fun Chart(card: TrendCard, data: CardData) {
    val title = stringResource(card.title)
    val xLabels = data.days.map(DateFormat::short)
    when (data) {
        is CardData.EnergyDay -> IntervalBarChart(data.points, xLabels = xLabels, label = title)
        is CardData.Lines -> {
            // Med flera serier får varje serie sin färg ur paletten, efter sin plats bland kortets serier; en ensam är teal.
            val colorOf: (TrendSerie) -> Color? = { serie -> if (data.shown.size > 1) AppColors.swatch(data.available.indexOfFirst { it.key == serie.key }.coerceAtLeast(0)) else null }
            val names = data.available.associate { it.key to seriesName(card, it) }
            val fallback = stringResource(R.string.symptoms)
            fun chartSeries(series: List<TrendSerie>) = series.map { ChartSeries(names[it.key] ?: fallback, it.points, colorOf(it)) }
            LineChart(
                chartSeries(data.shown),
                xLabels = xLabels,
                previous = chartSeries(data.previous),
                label = title,
                emptyHint = stringResource(if (data.shown.isEmpty()) R.string.trends_pick_series else R.string.chart_empty_hint),
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
