package se.partee71.dagboken.ui.migration

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

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

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            task.getResult(ApiException::class.java)
            vm.onSignedIn()
        } catch (e: ApiException) {
            val msg = when (e.statusCode) {
                12501 -> null // user cancelled — silent
                10    -> "Konfigureringsfel (SHA-1 ej registrerat i Cloud Console)"
                else  -> "Inloggning misslyckades (kod: ${e.statusCode})"
            }
            if (msg != null) vm.onSignInFailed(msg)
        }
    }

    val signInClient = remember(context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    LaunchedEffect(state) {
        if (state is MigrationState.Done) {
            onMigrationComplete()
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
                                text = "Välkommen till Dagboken",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Importera din data från en befintlig säkerhetskopia.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = vm::startMigration,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Importera från Google Drive")
                            }
                            OutlinedButton(
                                onClick = { fileLauncher.launch("application/json") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Välj säkerhetskopia från fil")
                            }
                            OutlinedButton(
                                onClick = vm::skipMigration,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Börja från början")
                            }
                        }

                        is MigrationState.CheckingDrive -> {
                            CircularProgressIndicator()
                            Text("Söker efter säkerhetskopia…")
                        }

                        is MigrationState.NoAccountSignedIn -> {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Logga in med Google",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "För att importera din säkerhetskopia behöver appen komma åt Google Drive.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { signInLauncher.launch(signInClient.signInIntent) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Logga in med Google")
                            }
                            OutlinedButton(
                                onClick = vm::skipMigration,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Fortsätt utan import")
                            }
                        }

                        is MigrationState.NoBackupFound -> {
                            Text(
                                "Ingen säkerhetskopia hittades på Google Drive.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = { fileLauncher.launch("application/json") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Välj säkerhetskopia från fil")
                            }
                            OutlinedButton(
                                onClick = vm::skipMigration,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Fortsätt utan import")
                            }
                        }

                        is MigrationState.Downloading -> {
                            CircularProgressIndicator()
                            Text("Laddar ner säkerhetskopia…")
                        }

                        is MigrationState.Importing -> {
                            Text("Importerar data…")
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
                                "Import klar!",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                "${s.aktiviteter} aktiviteter och ${s.mediciner} medicinlogg importerade.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        is MigrationState.Error -> {
                            Text(
                                "Fel: ${s.message}",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Button(onClick = { signInLauncher.launch(signInClient.signInIntent) }) {
                                Text("Logga in med Google")
                            }
                            OutlinedButton(onClick = vm::startMigration) { Text("Försök igen") }
                            OutlinedButton(onClick = vm::skipMigration) { Text("Hoppa över") }
                        }
                    }
                }
            }
        }
    }
}
