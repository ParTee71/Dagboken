package se.partee71.dagboken.ui.home

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.ui.theme.Emerald400

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
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = greeting(),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = formattedDate(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor        = colorScheme.primaryContainer,
                    scrolledContainerColor = colorScheme.surface,
                    titleContentColor     = colorScheme.onPrimaryContainer,
                    actionIconContentColor = colorScheme.onPrimaryContainer,
                ),
                actions = {
                    GoogleAccountBtn(
                        email       = uiState.googleEmail,
                        photoUrl    = uiState.googlePhotoUrl,
                        displayName = uiState.googleDisplayName,
                        onSignIn    = { vm.signIn(context) },
                        onSignOut   = { vm.signOut() },
                    )
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.08f))
                            .clickable(onClick = onNavigateToSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Inställningar",
                            modifier = Modifier.size(17.dp),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start  = 16.dp,
                end    = 16.dp,
                top    = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sparkline card
            if (uiState.screeningPoints.size >= 2) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Energi senaste 7 dagar",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            SparklineChart(points = uiState.screeningPoints)
                        }
                    }
                }
            }

            // Stat pills
            if (uiState.todayMediciner.isNotEmpty() || uiState.lastAktivitet != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (uiState.todayMediciner.isNotEmpty()) {
                            val allTaken = uiState.tagenCount == uiState.todayMediciner.size
                            val pillColor = if (allTaken) colorScheme.tertiary else colorScheme.secondary
                            val pillContainer = if (allTaken) colorScheme.tertiaryContainer else colorScheme.secondaryContainer
                            Surface(
                                color    = pillContainer,
                                shape    = MaterialTheme.shapes.large,
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = if (allTaken) Icons.Default.CheckCircle else Icons.Default.Medication,
                                        contentDescription = null,
                                        tint = pillColor,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Column {
                                        Text(
                                            "${uiState.tagenCount}/${uiState.todayMediciner.size}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = pillColor,
                                        )
                                        Text(
                                            "mediciner",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        uiState.lastAktivitet?.let { a ->
                            val (energyColor, energyContainer) = energyColorPair(a.energy)
                            Surface(
                                color    = energyContainer,
                                shape    = MaterialTheme.shapes.large,
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = energyColor,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Column {
                                        Text(
                                            if (a.energy > 0) "+${a.energy}" else "${a.energy}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = energyColor,
                                        )
                                        Text(
                                            "energi",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick action cards
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ElevatedCard(
                        onClick = onNavigateToAktiviteter,
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.primary),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                "Logga aktivitet",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onPrimary,
                            )
                        }
                    }
                    ElevatedCard(
                        onClick = onNavigateToMediciner,
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.secondary),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.Medication,
                                contentDescription = null,
                                tint = colorScheme.onSecondary,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                "Mediciner",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSecondary,
                            )
                        }
                    }
                }
            }

            // Medicine adherence card
            if (uiState.todayMediciner.isNotEmpty()) {
                item {
                    val allTaken = uiState.tagenCount == uiState.todayMediciner.size
                    val progressColor = if (allTaken) colorScheme.tertiary else colorScheme.secondary
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Mediciner idag",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${uiState.tagenCount} av ${uiState.todayMediciner.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = progressColor,
                                )
                            }
                            LinearProgressIndicator(
                                progress = {
                                    if (uiState.todayMediciner.isEmpty()) 0f
                                    else uiState.tagenCount.toFloat() / uiState.todayMediciner.size
                                },
                                modifier   = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color      = progressColor,
                                trackColor = progressColor.copy(alpha = 0.2f),
                            )
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            uiState.todayMediciner.forEach { medicin ->
                                MedicinRow(
                                    medicin  = medicin,
                                    onToggle = { vm.toggleMedicinTagen(medicin) },
                                )
                            }
                        }
                    }
                }
            }

            // Last activity
            uiState.lastAktivitet?.let { a ->
                item {
                    val (energyColor, energyContainer) = energyColorPair(a.energy)
                    val energyLabel = if (a.energy > 0) "+${a.energy}" else "${a.energy}"
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent   = { Text(a.aktivitet) },
                            supportingContent = {
                                Text(
                                    "${a.datum} ${a.tid}  •  ⚡ $energyLabel  •  😰 ${a.stress}",
                                    color = colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(energyContainer, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = energyColor,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun energyColorPair(energy: Int): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when {
        energy >= 5  -> cs.tertiary         to cs.tertiaryContainer
        energy >= 1  -> cs.secondary        to cs.secondaryContainer
        energy >= -1 -> cs.onSurfaceVariant to cs.surfaceVariant
        else         -> cs.error            to cs.errorContainer
    }
}

@Composable
private fun MedicinRow(medicin: Medicin, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        headlineContent = {
            Text(
                text = medicin.namn,
                textDecoration = if (medicin.tagen) TextDecoration.LineThrough else null,
                modifier = if (medicin.tagen) Modifier.alpha(0.5f) else Modifier,
            )
        },
        supportingContent = {
            Text(
                text = "${medicin.dos} ${medicin.enhet}  •  ${medicin.tidpunkt}",
                modifier = if (medicin.tagen) Modifier.alpha(0.5f) else Modifier,
            )
        },
        trailingContent = {
            Checkbox(checked = medicin.tagen, onCheckedChange = { onToggle() })
        },
        colors = if (medicin.tagen) {
            ListItemDefaults.colors(containerColor = cs.surfaceVariant.copy(alpha = 0.4f))
        } else {
            ListItemDefaults.colors()
        },
    )
}

@Composable
private fun GoogleAccountBtn(
    email: String?,
    photoUrl: String?,
    displayName: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(200), label = "acct_alpha")
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(34.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.08f))
            .clickable { if (email != null) showDialog = true else onSignIn() },
        contentAlignment = Alignment.Center,
    ) {
        if (email != null && photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = displayName ?: "Profilbild",
                modifier = Modifier.size(22.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = if (email != null) "Inloggad" else "Logga in",
                modifier = Modifier.size(17.dp),
                tint = if (email != null) cs.tertiary else cs.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        if (email != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(9.dp)
                    .background(cs.surface, CircleShape)
                    .padding(1.5.dp)
                    .background(Emerald400, CircleShape),
            )
        }
    }

    if (showDialog && email != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title   = { Text(displayName ?: email) },
            text    = { Text("Inloggad — data säkerhetskopieras dagligen till Google Drive.") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onSignOut()
                }) {
                    Text("Logga ut", color = cs.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Avbryt") }
            },
        )
    }
}
