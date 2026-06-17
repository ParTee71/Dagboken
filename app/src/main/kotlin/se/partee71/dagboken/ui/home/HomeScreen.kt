package se.partee71.dagboken.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.ui.components.AccountBottomSheet
import se.partee71.dagboken.ui.components.AccountBubble
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.StatPill
import se.partee71.dagboken.ui.theme.Emerald400
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
                title = {},
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

            // Stat pills row
            if (uiState.todayMediciner.isNotEmpty() || uiState.lastAktivitet != null) {
                item {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (uiState.todayMediciner.isNotEmpty()) {
                            val allTaken = uiState.tagenCount == uiState.todayMediciner.size
                            StatPill(
                                icon           = if (allTaken) Icons.Filled.CheckCircle else Icons.Filled.Medication,
                                value          = "${uiState.tagenCount}/${uiState.todayMediciner.size}",
                                label          = "mediciner",
                                containerColor = if (allTaken) cs.tertiaryContainer else cs.secondaryContainer,
                                contentColor   = if (allTaken) cs.onTertiaryContainer else cs.onSecondaryContainer,
                                modifier       = Modifier.weight(1f),
                            )
                        }
                        uiState.lastAktivitet?.let { a ->
                            val isPositive = a.energy > 0
                            StatPill(
                                icon           = Icons.Filled.Bolt,
                                value          = energyLabel(a.energy),
                                label          = "energi",
                                containerColor = if (isPositive) cs.tertiaryContainer else cs.errorContainer,
                                contentColor   = if (isPositive) cs.onTertiaryContainer else cs.onErrorContainer,
                                modifier       = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Mediciner idag card
            if (uiState.todayMediciner.isNotEmpty()) {
                item {
                    val allTaken      = uiState.tagenCount == uiState.todayMediciner.size
                    val progressColor by animateColorAsState(
                        targetValue = if (allTaken) cs.tertiary else cs.secondary,
                        animationSpec = tween(600),
                        label = "progress_color",
                    )
                    DagbokenCard(title = "Mediciner idag") {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                text  = "${uiState.tagenCount} av ${uiState.todayMediciner.size} tagna",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress      = {
                                if (uiState.todayMediciner.isEmpty()) 0f
                                else uiState.tagenCount.toFloat() / uiState.todayMediciner.size
                            },
                            modifier      = Modifier.fillMaxWidth(),
                            color         = progressColor,
                            trackColor    = progressColor.copy(alpha = 0.2f),
                        )
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        uiState.todayMediciner.take(3).forEach { medicin ->
                            HomeMedicinRow(
                                medicin  = medicin,
                                onToggle = { vm.toggleMedicinTagen(medicin) },
                            )
                        }
                        if (uiState.todayMediciner.size > 3) {
                            TextButton(
                                onClick  = onNavigateToMediciner,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Visa alla ${uiState.todayMediciner.size} mediciner →") }
                        }
                    }
                }
            }

            // Diagram / Screening card
            item {
                DagbokenCard(title = "Energi senaste 7 dagarna") {
                    if (uiState.screeningPoints.size >= 2) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            uiState.screeningPoints.forEachIndexed { i, pt ->
                                val fraction = pt / 10f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height((20 + (40 * fraction)).dp)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(cs.primary, cs.primaryContainer),
                                            )
                                        ),
                                )
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

@Composable
private fun HomeMedicinRow(medicin: Medicin, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        headlineContent = {
            Text(
                text           = medicin.namn,
                textDecoration = if (medicin.tagen) TextDecoration.LineThrough else null,
                modifier       = if (medicin.tagen) Modifier.alpha(0.5f) else Modifier,
            )
        },
        supportingContent = {
            Text(
                text     = "${medicin.dos} ${medicin.enhet}  •  ${medicin.tidpunkt}",
                modifier = if (medicin.tagen) Modifier.alpha(0.5f) else Modifier,
            )
        },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (medicin.tagen) Emerald400 else cs.surfaceVariant
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (medicin.tagen) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint     = cs.surface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        colors = if (medicin.tagen) {
            ListItemDefaults.colors(containerColor = cs.surfaceVariant.copy(alpha = 0.4f))
        } else {
            ListItemDefaults.colors()
        },
    )
}
