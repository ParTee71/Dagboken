package se.partee71.dagboken.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.BuildConfig
import se.partee71.dagboken.ui.components.AccountBottomSheet
import se.partee71.dagboken.ui.components.AccountBubble
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.StatPill
import se.partee71.dagboken.ui.theme.energyLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAktiviteter: () -> Unit,
    onNavigateToMediciner: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagram: () -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: HomeViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsState()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showAccountSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    AccountBubble(
                        email       = uiState.googleEmail,
                        photoUrl    = uiState.googlePhotoUrl,
                        displayName = uiState.googleDisplayName,
                        onClick     = { showAccountSheet = true },
                    )
                },
                title = {
                    Text(
                        text  = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
                actions = {
                    Text(
                        text     = formattedDate(),
                        style    = MaterialTheme.typography.labelMedium,
                        color    = cs.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start  = 16.dp,
                end    = 16.dp,
                top    = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero greeting banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(cs.primaryContainer, cs.secondaryContainer),
                            )
                        )
                        .padding(20.dp),
                ) {
                    Column {
                        Text(
                            text       = greeting(),
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color      = cs.onPrimaryContainer,
                        )
                        if (uiState.googleDisplayName != null) {
                            Text(
                                text  = uiState.googleDisplayName!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            // Overdue card — visas när meds eller screening passerat tiden
            val overdueTotal = uiState.overdueMediciner.size + uiState.overdueScreeningTimes.size
            if (overdueTotal > 0) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = MaterialTheme.shapes.large,
                        color    = cs.errorContainer,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier              = Modifier.padding(bottom = 8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint     = cs.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "Försenat",
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = cs.onErrorContainer,
                                )
                            }

                            uiState.overdueMediciner.forEachIndexed { i, med ->
                                if (i > 0) HorizontalDivider(color = cs.onErrorContainer.copy(alpha = 0.12f))
                                ListItem(
                                    headlineContent   = { Text(med.namn, color = cs.onErrorContainer) },
                                    supportingContent = {
                                        Text(
                                            "${med.dos} ${med.enhet}  ·  ${med.tidpunkt}",
                                            color = cs.onErrorContainer.copy(alpha = 0.7f),
                                        )
                                    },
                                    leadingContent  = {
                                        Icon(Icons.Filled.Medication, null, tint = cs.error)
                                    },
                                    trailingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(cs.onErrorContainer.copy(alpha = 0.15f))
                                                .clickable { vm.toggleMedicinTagen(med) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = "Markera ${med.namn} som tagen",
                                                tint     = cs.onErrorContainer,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }

                            uiState.overdueScreeningTimes.forEachIndexed { i, time ->
                                val showDivider = i > 0 || uiState.overdueMediciner.isNotEmpty()
                                if (showDivider) HorizontalDivider(color = cs.onErrorContainer.copy(alpha = 0.12f))
                                ListItem(
                                    headlineContent   = { Text("Daglig screening", color = cs.onErrorContainer) },
                                    supportingContent = {
                                        Text(
                                            "Påminnelse var: $time",
                                            color = cs.onErrorContainer.copy(alpha = 0.7f),
                                        )
                                    },
                                    leadingContent  = {
                                        Icon(Icons.Filled.Bolt, null, tint = cs.error)
                                    },
                                    trailingContent = {
                                        TextButton(onClick = onNavigateToAktiviteter) {
                                            Text("Logga →", color = cs.onErrorContainer)
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }

            // Diagram / Screening card
            item {
                DagbokenCard(title = "Energi senaste 7 dagarna") {
                    if (uiState.screeningPoints.size >= 2) {
                        SparklineChart(
                            points   = uiState.screeningPoints,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (uiState.screeningLabels.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                uiState.screeningLabels.forEach { label ->
                                    Text(
                                        text  = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    } else {
                        Text(
                            "Logga din första screening för att se trender",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    TextButton(
                        onClick  = onNavigateToDiagram,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Visa diagram →") }
                }
            }

            // Quick actions
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    ElevatedCard(
                        onClick  = onNavigateToAktiviteter,
                        modifier = Modifier.weight(1f),
                        colors   = CardDefaults.elevatedCardColors(containerColor = cs.primary),
                    ) {
                        Column(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalAlignment   = Alignment.CenterHorizontally,
                            verticalArrangement   = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.Bolt, null, tint = cs.onPrimary, modifier = Modifier.size(28.dp))
                            Text("Logga aktivitet", style = MaterialTheme.typography.titleSmall, color = cs.onPrimary)
                        }
                    }
                    ElevatedCard(
                        onClick  = onNavigateToMediciner,
                        modifier = Modifier.weight(1f),
                        colors   = CardDefaults.elevatedCardColors(containerColor = cs.secondary),
                    ) {
                        Column(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalAlignment   = Alignment.CenterHorizontally,
                            verticalArrangement   = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.Medication, null, tint = cs.onSecondary, modifier = Modifier.size(28.dp))
                            Text("Mediciner", style = MaterialTheme.typography.titleSmall, color = cs.onSecondary)
                        }
                    }
                }
            }
        }
    }

    if (showAccountSheet) {
        AccountBottomSheet(
            email       = uiState.googleEmail,
            photoUrl    = uiState.googlePhotoUrl,
            displayName = uiState.googleDisplayName,
            isSigningIn = uiState.isSigningIn,
            onDismiss   = { showAccountSheet = false },
            onSignIn    = { vm.signIn(context) },
            onSignOut   = { vm.signOut() },
            onNavigateToSettings = {
                showAccountSheet = false
                onNavigateToSettings()
            },
        )
    }
}

