package se.partee71.dagboken.ui.migration

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.R

@Composable
fun MigrationScreen(
    onMigrationComplete: () -> Unit,
    vm: MigrationViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { vm.importFromFile(it) } }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { vm.startMigration() }

    LaunchedEffect(state) {
        when (val s = state) {
            is MigrationState.Done -> onMigrationComplete()
            is MigrationState.NeedsAuthorization -> {
                driveAuthLauncher.launch(
                    IntentSenderRequest.Builder(s.pendingIntent.intentSender).build(),
                )
            }
            else -> Unit
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "migration_state",
            ) { s ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (s) {
                        is MigrationState.Idle -> {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.migration_welcome_title),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.migration_welcome_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = vm::startMigration,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.migration_import_from_drive))
                            }
                            OutlinedButton(
                                onClick = { fileLauncher.launch("application/json") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.migration_choose_file))
                            }
                            OutlinedButton(
                                onClick = vm::skipMigration,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.migration_start_fresh))
                            }
                        }

                        is MigrationState.CheckingDrive -> {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.migration_checking))
                        }

                        is MigrationState.NoAccountSignedIn -> {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.migration_needs_account_title),
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.migration_needs_account_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { vm.signInAndMigrate(context) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.sign_in_with_google))
                            }
                            OutlinedButton(
                                onClick = vm::skipMigration,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.migration_skip))
                            }
                        }

                        is MigrationState.NeedsAuthorization -> {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.migration_requesting_access))
                        }

                        is MigrationState.NoBackupFound -> {
                            Text(
                                stringResource(R.string.migration_no_backup),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = { fileLauncher.launch("application/json") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.migration_choose_file))
                            }
                            OutlinedButton(
                                onClick = vm::skipMigration,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.migration_skip))
                            }
                        }

                        is MigrationState.Downloading -> {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.migration_downloading))
                        }

                        is MigrationState.Importing -> {
                            Text(stringResource(R.string.migration_importing))
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { s.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        is MigrationState.Done -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                stringResource(R.string.migration_done_title),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                stringResource(R.string.format_migration_done_counts, s.aktiviteter, s.mediciner),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        is MigrationState.Error -> {
                            Text(
                                stringResource(R.string.format_migration_error, s.message),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = { vm.signInAndMigrate(context) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.sign_in_with_google))
                            }
                            OutlinedButton(
                                onClick = vm::startMigration,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.migration_retry))
                            }
                            OutlinedButton(
                                onClick = vm::skipMigration,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.migration_skip_error))
                            }
                        }
                    }
                }
            }
        }
    }
}
