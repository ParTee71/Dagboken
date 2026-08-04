package se.partee71.dagboken.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.DagbokenCard

/** Veckosammanfattning (HEM-9) — visas i början av veckan, ovanför Idag-kortet. */
@Composable
internal fun WeekSummaryCard(summary: WeekSummary) {
    val cs = MaterialTheme.colorScheme
    val energyText = when (summary.energyTrend) {
        EnergyTrend.UP   -> stringResource(R.string.home_week_energy_up)
        EnergyTrend.DOWN -> stringResource(R.string.home_week_energy_down)
        EnergyTrend.FLAT -> stringResource(R.string.home_week_energy_flat)
    }
    DagbokenCard(title = stringResource(R.string.home_week_summary_title)) {
        Text(energyText, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
        Text(
            stringResource(R.string.format_home_week_doses, summary.dosesTakenPercent),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
    }
}
