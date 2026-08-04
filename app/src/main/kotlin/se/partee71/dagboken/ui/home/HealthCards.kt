package se.partee71.dagboken.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.WeeklyHealth
import se.partee71.dagboken.domain.model.statsFor
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.StatPill
import se.partee71.dagboken.ui.diagram.MinMaxCaption
import se.partee71.dagboken.ui.formatShortDate
import se.partee71.dagboken.ui.formatWeekdayShort
import java.time.LocalDate

/**
 * Idag-hälsokortet (HLS-7, HEM-15, #138): steg + vilopuls för [selectedDate] — bläddrar
 * användaren till en annan dag (HEM-14) byter siffrorna med. Trenderna över tid ligger
 * i stället i det gemensamma diagramkortet, se [HealthTrendsCard] (HEM-17).
 */
@Composable
internal fun HealthStatsCard(weekly: WeeklyHealth, selectedDate: LocalDate, isToday: Boolean) {
    val cs = MaterialTheme.colorScheme
    val dash = stringResource(R.string.halsa_no_value)
    val stats = weekly.statsFor(selectedDate, isToday)
    val stepsLabel = if (isToday) {
        stringResource(R.string.home_health_steps_today)
    } else {
        stringResource(R.string.home_health_steps_for_date, formatShortDate(selectedDate))
    }

    DagbokenCard(title = stringResource(R.string.home_health_title)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatPill(
                icon           = Icons.Filled.DirectionsWalk,
                value          = stats.steps?.toString() ?: dash,
                label          = stepsLabel,
                containerColor = cs.primaryContainer,
                contentColor   = cs.onPrimaryContainer,
                modifier       = Modifier.weight(1f),
            )
            StatPill(
                icon           = Icons.Filled.MonitorHeart,
                value          = stats.restingHeartRate?.let { stringResource(R.string.halsa_bpm, it) } ?: dash,
                label          = stringResource(R.string.home_health_resting_hr),
                containerColor = cs.secondaryContainer,
                contentColor   = cs.onSecondaryContainer,
                modifier       = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Gemensamt diagramkort (HEM-17, #138): stegtrend, vilopulstrend (HLS-7) och energitrend
 * (HEM-7) för senaste 7 dagarna, i den ordningen — ersätter de tidigare separata korten.
 * [weekly] är null när Health Connect saknas/ej kopplat, då visas bara energitrenden.
 */
@Composable
internal fun HealthTrendsCard(
    weekly: WeeklyHealth?,
    screeningPoints: List<Float>,
    screeningLabels: List<String>,
    onNavigateToTrender: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    DagbokenCard(title = stringResource(R.string.home_trends_title)) {
        if (weekly?.hasStepTrend == true) {
            Text(
                stringResource(R.string.home_health_steps_trend),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
            val stepsPoints = weekly.dailySteps.map { it.steps.toFloat() }
            SparklineChart(
                points   = stepsPoints,
                xLabels  = weekly.dailySteps.map { formatWeekdayShort(it.date) },
                modifier = Modifier.padding(top = 4.dp),
            )
            MinMaxCaption(min = stepsPoints.min(), max = stepsPoints.max(), modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(12.dp))
        }
        if (weekly?.hasRestingHeartRateTrend == true) {
            val known = weekly.dailyRestingHeartRate.filter { it.bpm != null }
            Text(
                stringResource(R.string.home_health_resting_hr_trend),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
            val bpmPoints = known.map { it.bpm!!.toFloat() }
            SparklineChart(
                points   = bpmPoints,
                xLabels  = known.map { formatWeekdayShort(it.date) },
                modifier = Modifier.padding(top = 4.dp),
            )
            MinMaxCaption(min = bpmPoints.min(), max = bpmPoints.max(), modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(12.dp))
        }
        Text(
            stringResource(R.string.home_energy_chart_title),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
        )
        if (screeningPoints.size >= 2) {
            SparklineChart(
                points   = screeningPoints,
                xLabels  = screeningLabels,
                modifier = Modifier.padding(top = 4.dp),
            )
            MinMaxCaption(
                min      = screeningPoints.min(),
                max      = screeningPoints.max(),
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(4.dp))
        } else {
            Text(
                stringResource(R.string.home_no_screening_body),
                style    = MaterialTheme.typography.bodySmall,
                color    = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
        TextButton(
            onClick  = onNavigateToTrender,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.home_view_diagram)) }
    }
}

@Composable
internal fun HealthConnectPrompt(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    DagbokenCard(onClick = onClick) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.MonitorHeart,
                contentDescription = null,
                tint     = cs.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_health_connect_title),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = cs.onSurface,
                )
                Text(
                    stringResource(R.string.home_health_connect_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint     = cs.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
