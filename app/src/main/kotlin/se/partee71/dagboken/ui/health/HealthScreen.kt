package se.partee71.dagboken.ui.health

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import se.partee71.dagboken.ui.components.StatPill
import java.time.Duration

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
    val dash = stringResource(R.string.halsa_no_value)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatPill(
            icon           = Icons.Filled.DirectionsWalk,
            value          = health.steps?.toString() ?: dash,
            label          = stringResource(R.string.halsa_steps),
            containerColor = cs.primaryContainer,
            contentColor   = cs.onPrimaryContainer,
            modifier       = Modifier.fillMaxWidth(),
        )
        StatPill(
            icon           = Icons.Filled.Favorite,
            value          = health.heartRateAvg?.let { stringResource(R.string.halsa_bpm, it) } ?: dash,
            label          = stringResource(R.string.halsa_heart_rate),
            containerColor = cs.secondaryContainer,
            contentColor   = cs.onSecondaryContainer,
            modifier       = Modifier.fillMaxWidth(),
        )
        StatPill(
            icon           = Icons.Filled.Bedtime,
            value          = health.sleepDuration?.let { formatDuration(it) } ?: dash,
            label          = stringResource(R.string.halsa_sleep),
            containerColor = cs.tertiaryContainer,
            contentColor   = cs.onTertiaryContainer,
            modifier       = Modifier.fillMaxWidth(),
        )
    }
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
