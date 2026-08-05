package se.partee71.dagboken.ui.health

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.HealthData
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.SectionHeader
import se.partee71.dagboken.ui.components.StatPill
import java.time.Duration
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HealthScreen(
    onBack: () -> Unit,
    vm: HealthViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { _ -> vm.refresh() }

    HealthScreenContent(
        state              = state,
        onBack             = onBack,
        onGrantPermissions = { permissionLauncher.launch(vm.permissions) },
        onRetry            = { vm.refresh() },
        onOpenHealthConnect = {
            // Öppna Health Connects egen yta (installera/uppdatera). Faller tyst
            // om ingen aktivitet hanterar intentet.
            runCatching {
                context.startActivity(
                    Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS),
                )
            }
        },
    )
}

@Composable
internal fun HealthScreenContent(
    state: HealthUiState,
    onBack: () -> Unit,
    onGrantPermissions: () -> Unit,
    onRetry: () -> Unit,
    onOpenHealthConnect: () -> Unit,
) {
    DagbokenScaffold(
        title  = stringResource(R.string.halsa_title),
        onBack = onBack,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is HealthUiState.Loading -> CircularProgressIndicator()

                is HealthUiState.Unavailable -> EmptyState(
                    icon  = Icons.Filled.MonitorHeart,
                    title = stringResource(
                        if (state.updateRequired) R.string.halsa_hc_update_title
                        else R.string.halsa_hc_missing_title,
                    ),
                    body  = stringResource(
                        if (state.updateRequired) R.string.halsa_hc_update_body
                        else R.string.halsa_hc_missing_body,
                    ),
                    action = {
                        Button(onClick = onOpenHealthConnect) {
                            Text(stringResource(R.string.halsa_hc_open))
                        }
                    },
                )

                is HealthUiState.PermissionsRequired -> EmptyState(
                    icon  = Icons.Filled.MonitorHeart,
                    title = stringResource(R.string.halsa_permission_title),
                    body  = stringResource(R.string.halsa_permission_body),
                    action = {
                        Button(onClick = onGrantPermissions) {
                            Text(stringResource(R.string.halsa_permission_grant))
                        }
                    },
                )

                is HealthUiState.Error -> EmptyState(
                    icon  = Icons.Filled.MonitorHeart,
                    title = stringResource(R.string.halsa_error_title),
                    body  = stringResource(R.string.halsa_error_body),
                    action = {
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.halsa_retry))
                        }
                    },
                )

                is HealthUiState.Data -> HealthDataContent(state.health)
            }
        }
    }
}

@Composable
private fun HealthDataContent(health: HealthData) {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(stringResource(R.string.halsa_section_today))
        HealthPill(
            icon  = Icons.Filled.DirectionsWalk,
            value = health.steps?.toString(),
            label = stringResource(R.string.halsa_steps),
            containerColor = cs.primaryContainer,
            contentColor   = cs.onPrimaryContainer,
        )
        HealthPill(
            icon  = Icons.Filled.Favorite,
            value = health.heartRateAvg?.let { stringResource(R.string.halsa_bpm, it) },
            label = stringResource(R.string.halsa_heart_rate),
            containerColor = cs.secondaryContainer,
            contentColor   = cs.onSecondaryContainer,
        )

        SectionHeader(stringResource(R.string.halsa_section_sleep))
        HealthPill(
            icon  = Icons.Filled.Bedtime,
            value = health.sleepDuration?.let { formatDuration(it) },
            label = stringResource(R.string.halsa_sleep_total),
            containerColor = cs.tertiaryContainer,
            contentColor   = cs.onTertiaryContainer,
        )
        // Stadierna visas bara när Health Connect faktiskt har dem — en natt utan
        // klocka på armen ger bara en total sömnlängd (HLS-8).
        if (!health.sleepStages.isEmpty) {
            SleepStagePill(R.string.halsa_sleep_deep, health.sleepStages.deep)
            SleepStagePill(R.string.halsa_sleep_rem, health.sleepStages.rem)
            SleepStagePill(R.string.halsa_sleep_light, health.sleepStages.light)
            SleepStagePill(R.string.halsa_sleep_awake, health.sleepStages.awake)
        }

        SectionHeader(stringResource(R.string.halsa_section_exercise))
        HealthPill(
            icon  = Icons.Filled.FitnessCenter,
            value = health.exerciseDuration?.let { formatDuration(it) },
            label = stringResource(R.string.halsa_exercise, health.exerciseSessions),
            containerColor = cs.primaryContainer,
            contentColor   = cs.onPrimaryContainer,
        )
        HealthPill(
            icon  = Icons.Filled.LocalFireDepartment,
            value = health.activeEnergyKcal?.let { stringResource(R.string.halsa_kcal, it.roundToInt()) },
            label = stringResource(R.string.halsa_active_energy),
            containerColor = cs.secondaryContainer,
            contentColor   = cs.onSecondaryContainer,
        )
        HealthPill(
            icon  = Icons.Filled.Straighten,
            value = health.distanceMeters?.let { stringResource(R.string.halsa_km, formatKilometers(it)) },
            label = stringResource(R.string.halsa_distance),
            containerColor = cs.tertiaryContainer,
            contentColor   = cs.onTertiaryContainer,
        )

        SectionHeader(stringResource(R.string.halsa_section_vitals))
        HealthPill(
            icon  = Icons.Filled.Air,
            value = health.oxygenSaturationAvg?.let { stringResource(R.string.halsa_percent, it.roundToInt()) },
            label = stringResource(R.string.halsa_oxygen_saturation),
            containerColor = cs.primaryContainer,
            contentColor   = cs.onPrimaryContainer,
        )
        HealthPill(
            icon  = Icons.Filled.MonitorHeart,
            value = health.bloodPressure?.let {
                stringResource(R.string.halsa_mmhg, it.systolic, it.diastolic)
            },
            label = stringResource(R.string.halsa_blood_pressure),
            containerColor = cs.secondaryContainer,
            contentColor   = cs.onSecondaryContainer,
        )
    }
}

/** [StatPill] (regel 4) med appens "—" för en datapunkt som saknas. */
@Composable
private fun HealthPill(
    icon: ImageVector,
    value: String?,
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    StatPill(
        icon           = icon,
        value          = value ?: stringResource(R.string.halsa_no_value),
        label          = label,
        containerColor = containerColor,
        contentColor   = contentColor,
        modifier       = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SleepStagePill(@StringRes label: Int, duration: Duration?) {
    val cs = MaterialTheme.colorScheme
    HealthPill(
        icon  = Icons.Filled.Bedtime,
        value = duration?.let { formatDuration(it) },
        label = stringResource(label),
        containerColor = cs.surfaceVariant,
        contentColor   = cs.onSurfaceVariant,
    )
}

@Composable
private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return if (hours > 0) {
        stringResource(R.string.halsa_duration_h_min, hours, minutes)
    } else {
        stringResource(R.string.halsa_duration_min, minutes)
    }
}

/**
 * Sträcka i kilometer med en decimal, i användarens lokala talformat (`5,2` på
 * svenska, `5.2` på engelska). Ren funktion så formatet kan enhetstestas per
 * lokal utan att rendera skärmen (regel 2).
 */
internal fun formatKilometers(meters: Double, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%.1f", meters / 1000.0)
