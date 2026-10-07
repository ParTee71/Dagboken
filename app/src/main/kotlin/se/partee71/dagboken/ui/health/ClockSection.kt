package se.partee71.dagboken.ui.health

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlin.time.Duration
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.SleepFlag
import se.partee71.dagboken.core.engine.WatchMetric
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric
import se.partee71.dagboken.core.model.SleepStages
import se.partee71.dagboken.data.health.HealthStatus
import se.partee71.dagboken.ui.common.durationText
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.components.NoticeBanner
import se.partee71.dagboken.ui.components.SectionHeader
import se.partee71.dagboken.ui.components.StatPill
import se.partee71.dagboken.ui.diagram.CHART_SEPARATOR
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Health Connect-statusen överst i Trender → Klocka (TRD-20, HLS-3, HLS-4, HLS-14) som `NoticeBanner` i lägets ton:
 * kopplad (grön, bara meddelande), ej kopplad med "Ge åtkomst", saknas med "Installera" och uppdatering krävs med
 * "Uppdatera". När klockan är kopplad och valfria behörigheter saknas ([missing]) står en klickbar `StatPill` under
 * med vilka mått det gäller och "Ge åtkomst" – bara när något saknas, och inte alls när läget inte gick att läsa
 * (`null`). Okänt läge ([status] `null`) visar ingenting.
 */
@Composable
fun ClockStatus(status: HealthStatus?, missing: Set<OptionalHealthMetric>?, onEvent: (HealthEvent) -> Unit) {
    val grant = { onEvent(HealthEvent.GrantAccess) }
    val open = { onEvent(HealthEvent.OpenHealthConnect) }
    when (status) {
        HealthStatus.AVAILABLE -> NoticeBanner(
            stringResource(R.string.health_connected),
            R.drawable.ic_watch,
            onClick = null,
            tone = Tone.Positive,
            detail = stringResource(R.string.health_connected_detail),
        )
        HealthStatus.PERMISSIONS_MISSING -> StatusBanner(R.string.health_not_connected, R.string.health_not_connected_detail, R.string.health_grant_access, Tone.Neutral, grant)
        HealthStatus.UNAVAILABLE -> StatusBanner(R.string.trends_health_missing, R.string.trends_health_missing_detail, R.string.health_install, Tone.Warning, open)
        HealthStatus.UPDATE_REQUIRED -> StatusBanner(R.string.health_update_required, R.string.health_update_required_detail, R.string.health_update, Tone.Sun, open)
        null -> Unit
    }
    if (status == HealthStatus.AVAILABLE && !missing.isNullOrEmpty()) {
        // Måtten i enumets ordning, inte mängdens – samma uppräkning varje gång.
        val names = OptionalHealthMetric.entries.filter { it in missing }.map { stringResource(it.label()) }
        StatPill(
            R.drawable.ic_lock,
            stringResource(R.string.health_missing_permissions_format, missing.size),
            names.joinToString(CHART_SEPARATOR),
            Modifier.fillMaxWidth(),
            tone = Tone.Sun,
            onClick = grant,
            action = stringResource(R.string.health_grant_access),
        )
    }
}

@Composable
private fun StatusBanner(title: Int, detail: Int, action: Int, tone: Tone, onClick: () -> Unit) {
    NoticeBanner(stringResource(title), R.drawable.ic_watch, onClick, tone = tone, detail = stringResource(detail), action = stringResource(action))
}

/**
 * "Hälsa idag" (HLS-6, HLS-8, HLS-10, HLS-11) under `SectionHeader`: klockans alla mått för idag som `StatPill`, två
 * per rad – steg, snittpuls, vilopuls; sömnen i natt med stadierna under (bara när natten har stadier); sömnkvaliteten
 * – eller uppmaningen att fylla i födelseår i Profil, aldrig en poäng mot fel norm – med nattens varningsrader under
 * ([SleepFlagNotices]); träning, aktiva kalorier, sträcka och syremättnad. Ett mått utan värde (eller utan åtkomst, se [ClockStatus]) är "—".
 */
@Composable
fun HealthTodaySection(state: ClockUiState) {
    val day = state.day
    SectionHeader(stringResource(R.string.health_today_title))
    PillRows(
        listOf(
            Metric(R.drawable.ic_steps, metricValue(WatchMetric.STEPS, day), stringResource(WatchMetric.STEPS.label())),
            Metric(R.drawable.ic_heart, metricValue(WatchMetric.HEART_RATE_AVG, day), stringResource(R.string.health_heart_rate_avg)),
            Metric(R.drawable.ic_heart, metricValue(WatchMetric.RESTING_HEART_RATE, day), stringResource(WatchMetric.RESTING_HEART_RATE.label())),
        ),
    )
    StatPill(R.drawable.ic_moon, metricValue(WatchMetric.SLEEP_TOTAL, day), stringResource(R.string.health_sleep_last_night), Modifier.fillMaxWidth())
    day?.sleepStages?.takeUnless { it.isEmpty }?.let { stages ->
        Text(stagesText(stages), Modifier.padding(horizontal = Spacing.l), style = AppTypography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val score = state.sleepScore
    StatPill(
        R.drawable.ic_star,
        score?.toString() ?: stringResource(R.string.value_missing),
        stringResource(if (state.needsBirthYear) R.string.health_sleep_quality_needs_birth_year else R.string.trends_card_sleep_quality),
        Modifier.fillMaxWidth(),
        tone = when {
            state.needsBirthYear -> Tone.Sun
            score != null -> Tone.Positive
            else -> Tone.Neutral
        },
    )
    SleepFlagNotices(state.sleepFlags)
    PillRows(
        listOf(
            Metric(R.drawable.ic_run, metricValue(WatchMetric.EXERCISE, day), stringResource(R.string.health_exercise_today)),
            Metric(R.drawable.ic_flame, metricValue(WatchMetric.ACTIVE_CALORIES, day), stringResource(WatchMetric.ACTIVE_CALORIES.label())),
            Metric(R.drawable.ic_route, metricValue(WatchMetric.DISTANCE, day), stringResource(WatchMetric.DISTANCE.label())),
            Metric(R.drawable.ic_oxygen, metricValue(WatchMetric.OXYGEN_SATURATION, day), stringResource(WatchMetric.OXYGEN_SATURATION.label())),
        ),
    )
}

/**
 * Sömnkvalitetens varningsrader (HLS-10): en `NoticeBanner` per varning – låg syremättnad och sovpuls över den vakna
 * baslinjen – i varningston, utan åtgärd. De visas vid sidan av poängen och dras aldrig av från den, bara i Hälsa idag
 * (som i 3.x).
 */
@Composable
internal fun SleepFlagNotices(flags: List<SleepFlag>) {
    flags.forEach { flag ->
        val icon = when (flag) {
            SleepFlag.LOW_OXYGEN_SATURATION -> R.drawable.ic_oxygen
            SleepFlag.ELEVATED_SLEEPING_HEART_RATE -> R.drawable.ic_heart
        }
        NoticeBanner(stringResource(flag.label()), icon, onClick = null, Modifier.fillMaxWidth())
    }
}

/** "Djup 1 tim 12 min · REM 1 tim 35 min · Lätt 3 tim 48 min · Vaken 37 min" – ett stadium utan tid utelämnas. */
@Composable
private fun stagesText(stages: SleepStages): String {
    val parts = listOf(
        WatchMetric.SLEEP_DEEP to stages.deep,
        WatchMetric.SLEEP_REM to stages.rem,
        WatchMetric.SLEEP_LIGHT to stages.light,
        WatchMetric.SLEEP_AWAKE to stages.awake,
    ).mapNotNull { (metric, time) -> time?.let { stageText(metric, it) } }
    return parts.joinToString(CHART_SEPARATOR)
}

@Composable
private fun stageText(metric: WatchMetric, time: Duration): String =
    stringResource(R.string.chart_stat_format, stringResource(metric.label()), durationText(time.inWholeMinutes.toInt()))
