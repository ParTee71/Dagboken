package se.kodhast.dagboken.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalDate
import se.kodhast.dagboken.R
import se.kodhast.dagboken.core.engine.WatchMetric
import se.kodhast.dagboken.data.health.HealthStatus
import se.kodhast.dagboken.ui.common.label
import se.kodhast.dagboken.ui.diagram.SparklineChart
import se.kodhast.dagboken.ui.theme.AppTypography
import se.kodhast.dagboken.ui.theme.Neutral
import se.kodhast.ui.common.DateFormat
import se.kodhast.ui.components.AppButton
import se.kodhast.ui.components.AppCard
import se.kodhast.ui.components.ButtonVariant
import se.kodhast.ui.components.SectionFoldout
import se.kodhast.ui.components.StatPillGrid
import se.kodhast.ui.components.StatPillItem
import se.kodhast.ui.theme.Spacing
import se.kodhast.ui.theme.Tone

/**
 * Hälsokortet på Idag (HEM-15, HLS-7): steg och vilopuls för den visade dagen som `StatPill` – "—" när dagen saknar
 * värde. Utan behörighet en diskret koppla-rad med "Ge åtkomst" i stället för värdena. Saknas Health Connect, behöver
 * det uppdateras eller är läget okänt visas inget kort – det läget står i Trender → Klocka (TRD-20, HLS-4).
 */
@Composable
fun HealthTodayCard(state: HealthTodayUiState, onEvent: (HealthEvent) -> Unit, expanded: Boolean = true, onToggle: () -> Unit = {}) {
    if (state.status != HealthStatus.AVAILABLE && state.status != HealthStatus.PERMISSIONS_MISSING) return
    AppCard {
        SectionFoldout(stringResource(R.string.today_health_title), expanded, onToggle, icon = R.drawable.ic_watch) {
            if (state.status == HealthStatus.AVAILABLE) {
                StatPillGrid(
                    listOf(
                        StatPillItem(R.drawable.ic_steps, metricValue(WatchMetric.STEPS, state.day), stringResource(WatchMetric.STEPS.label())),
                        StatPillItem(R.drawable.ic_heart, metricValue(WatchMetric.RESTING_HEART_RATE, state.day), stringResource(WatchMetric.RESTING_HEART_RATE.label()))
                    ),
                    Tone.Neutral
                )
            } else {
                Text(stringResource(R.string.today_health_connect), style = AppTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppButton(stringResource(R.string.health_grant_access), { onEvent(HealthEvent.GrantAccess) })
            }
        }
    }
}

/**
 * "Senaste veckan" (HEM-17, HEM-7): en `SparklineChart`-rad per trend i ordningen steg → vilopuls → energi, var och
 * en med rubrik och "Lägst · Högst · Idag" (TRD-9). Steg- och vilopulsraden finns bara när klockan är kopplad och
 * minst två dagar har värde ([health], `null` = ingen klocka); energin ([energy] över [energyDays], samma dagsvärde som
 * Trender) visas alltid och ber om fler loggade dagar när underlaget är för litet. Länken "Visa i Trender" sist (TRD-5).
 */
@Composable
fun WeekTrendsCard(
    energyDays: List<LocalDate>,
    energy: List<Float?>,
    health: HealthTodayUiState?,
    onOpenTrends: () -> Unit,
    expanded: Boolean = true,
    onToggle: () -> Unit = {}
) {
    AppCard {
        SectionFoldout(stringResource(R.string.today_week_trends_title), expanded, onToggle, icon = R.drawable.ic_trend) {
            val healthLabels = health?.weekDays.orEmpty().map(DateFormat::weekdayShort)
            health?.steps?.let { TrendRow(stringResource(WatchMetric.STEPS.label()), it, healthLabels) }
            health?.restingHeartRate?.let { TrendRow(stringResource(WatchMetric.RESTING_HEART_RATE.label()), it, healthLabels) }
            TrendRow(stringResource(R.string.energy), energy, energyDays.map(DateFormat::weekdayShort), stringResource(R.string.today_energy_hint))
            AppButton(stringResource(R.string.chart_open_trends), onOpenTrends, variant = ButtonVariant.Text)
        }
    }
}

/** En trendrad: rubriken och minidiagrammet med sin min/max-text; [emptyHint] när underlaget är för litet. */
@Composable
private fun TrendRow(title: String, points: List<Float?>, xLabels: List<String>, emptyHint: String = stringResource(R.string.chart_empty_hint)) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = AppTypography.itemTitle, color = MaterialTheme.colorScheme.onSurface)
        SparklineChart(points, xLabels = xLabels, label = title, emptyHint = emptyHint)
    }
}
