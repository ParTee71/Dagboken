package se.kodhast.dagboken.ui.health

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import se.kodhast.dagboken.R
import se.kodhast.dagboken.core.engine.WatchMetric
import se.kodhast.dagboken.core.engine.WatchUnit
import se.kodhast.dagboken.core.engine.formatChartValue
import se.kodhast.dagboken.core.model.DailyHealth
import se.kodhast.dagboken.core.time.MINUTES_PER_HOUR
import se.kodhast.dagboken.ui.common.label
import se.kodhast.dagboken.ui.theme.Neutral
import se.kodhast.ui.common.DateFormat
import se.kodhast.ui.components.StatPill
import se.kodhast.ui.theme.Spacing
import se.kodhast.ui.theme.Tone

/**
 * Ett klockmåtts värde för ett dygn som text (HLS-6, HLS-8): "7 842", "58", "7 tim 12 min", "412 kcal", "5,8 km",
 * "97 %" – tusental med hårt mellanslag (`formatChartValue`, samma som diagrammen) – och "—" (`value_missing`, som `StatPill` och `DayDoneCard`) när dygnet saknar måttet eller inte lästs än.
 * Värdet kommer ur `WatchMetric.value`, samma som Trenders diagram, så att Hälsa idag och korten aldrig skiljer sig.
 */
@Composable
@ReadOnlyComposable
internal fun metricValue(metric: WatchMetric, day: DailyHealth?): String {
    val value = day?.let(metric::value) ?: return stringResource(R.string.value_missing)
    return when (metric.unit) {
        WatchUnit.STEPS, WatchUnit.BPM -> formatChartValue(value.roundToInt().toFloat())
        WatchUnit.HOURS -> DateFormat.duration((value * MINUTES_PER_HOUR).roundToInt())
        WatchUnit.MINUTES -> DateFormat.duration(value.roundToInt())
        WatchUnit.KCAL -> withUnit(formatChartValue(value.roundToInt().toFloat()), metric.unit)
        WatchUnit.KM -> withUnit(formatChartValue(value), metric.unit)
        else -> withUnit(value.roundToInt().toString(), metric.unit)
    }
}

/** Ett mätvärde i Hälsa idag och på Idag: ikon, värdet som text och etikett. */
internal class Metric(@param:DrawableRes val icon: Int, val value: String, val label: String)

/** Mätvärdena som `StatPill` två och två (HLS-6, HEM-15); ett ensamt sist tar hela bredden. */
@Composable
internal fun PillRows(metrics: List<Metric>) {
    metrics.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            row.forEach { StatPill(it.icon, it.value, it.label, Tone.Neutral, Modifier.weight(1f)) }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun withUnit(value: String, unit: WatchUnit): String = stringResource(R.string.value_with_unit_format, value, stringResource(unit.label()))
