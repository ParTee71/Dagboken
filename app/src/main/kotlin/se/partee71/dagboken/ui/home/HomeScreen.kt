package se.partee71.dagboken.ui.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import se.partee71.dagboken.domain.model.Medicin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAktiviteter: () -> Unit,
    onNavigateToMediciner: () -> Unit,
    onNavigateToSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: HomeViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    var refreshKey by remember { mutableIntStateOf(0) }
    val googleAccount = remember(refreshKey) { GoogleSignIn.getLastSignedInAccount(context) }

    val signInClient = remember(context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try { task.getResult(ApiException::class.java) } catch (_: ApiException) { }
        refreshKey++
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = greeting(),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            text = formattedDate(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    GoogleAccountBtn(
                        account    = googleAccount,
                        signInClient = signInClient,
                        onSignIn   = { signInLauncher.launch(signInClient.signInIntent) },
                        onSignOut  = { refreshKey++ },
                    )
                    // Settings pill button — matches GoogleAccountBtn size
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x1A808080))
                            .clickable(onClick = onNavigateToSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Inställningar",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            // Sparkline card
            if (uiState.screeningPoints.size >= 2) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Energi senaste 7 dagarna",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            SparklineChart(points = uiState.screeningPoints)
                        }
                    }
                }
            }

            // Quick action buttons
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalButton(
                        onClick  = onNavigateToAktiviteter,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Logga aktivitet")
                    }
                    FilledTonalButton(
                        onClick  = onNavigateToMediciner,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Medication, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Ta medicin")
                    }
                }
            }

            // Today's medicine
            if (uiState.todayMediciner.isNotEmpty()) {
                item {
                    Text(
                        text  = "Idag — ${uiState.tagenCount}/${uiState.todayMediciner.size} tagna",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(uiState.todayMediciner) { medicin ->
                    MedicinRow(medicin = medicin, onToggle = { vm.toggleMedicinTagen(medicin) })
                }
            }

            // Last activity
            uiState.lastAktivitet?.let { a ->
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent  = { Text(a.aktivitet) },
                            supportingContent = { Text("${a.datum} ${a.tid}  •  Energi ${a.energy}  •  Stress ${a.stress}") },
                            leadingContent   = { Icon(Icons.Default.Bolt, contentDescription = null) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun GoogleAccountBtn(
    account: GoogleSignInAccount?,
    signInClient: GoogleSignInClient,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(200),
        label = "account_btn_alpha",
    )
    val teal = MaterialTheme.colorScheme.tertiary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val surface = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(34.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(Color(0x1A808080))
            .clickable {
                if (account != null) showDialog = true else onSignIn()
            },
        contentAlignment = Alignment.Center,
    ) {
        val photoUrl = account?.photoUrl?.toString()
        if (account != null && photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = account.displayName ?: "Profilbild",
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = if (account != null) "Inloggad" else "Logga in",
                modifier = Modifier.size(17.dp),
                tint = if (account != null) teal else muted,
            )
        }

        // Green "connected" dot with surface-color ring
        if (account != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(9.dp)
                    .background(surface, CircleShape)
                    .padding(1.5.dp)
                    .background(Color(0xFF22C55E), CircleShape),
            )
        }
    }

    if (showDialog && account != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(account.displayName ?: "Google-konto") },
            text = { Text("Inloggad — data säkerhetskopieras till Google Drive.") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    signInClient.signOut().addOnCompleteListener { onSignOut() }
                }) {
                    Text("Logga ut", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Avbryt")
                }
            },
        )
    }
}

@Composable
private fun MedicinRow(medicin: Medicin, onToggle: () -> Unit) {
    ListItem(
        headlineContent  = { Text(medicin.namn) },
        supportingContent = { Text("${medicin.dos} ${medicin.enhet}  •  ${medicin.tidpunkt}") },
        trailingContent  = {
            Checkbox(checked = medicin.tagen, onCheckedChange = { onToggle() })
        },
    )
}
