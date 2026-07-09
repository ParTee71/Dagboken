package se.partee71.dagboken.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.BuildConfig
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel
import se.partee71.dagboken.ui.components.AccountBottomSheet
import se.partee71.dagboken.ui.components.AccountBubble
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.GradientSliderRow
import se.partee71.dagboken.ui.theme.DagbokenAnimSpec

@Composable
fun HomeScreen(
    onNavigateToAktiviteter: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagram: () -> Unit,
    onNavigateToSjukdomar: () -> Unit,
    onAddAktivitet: () -> Unit,
    onAddMedicin: () -> Unit,
    onAddHandelse: () -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: HomeViewModel = hiltViewModel(),
    screeningVm: AktiviteterViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsState()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showAccountSheet by remember { mutableStateOf(false) }
    var fabMenuExpanded by remember { mutableStateOf(false) }

    val screeningSnackbar by screeningVm.snackbar.collectAsState()
    LaunchedEffect(screeningSnackbar) {
        screeningSnackbar?.let { snackbarHostState.showSnackbar(it); screeningVm.clearSnackbar() }
    }

    DagbokenScaffold(
        navigationIcon = {
            AccountBubble(
                email       = uiState.googleEmail,
                photoUrl    = uiState.googlePhotoUrl,
                displayName = uiState.googleDisplayName,
                onClick     = { showAccountSheet = true },
            )
        },
        titleContent = {
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
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { fabMenuExpanded = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_fab_add))
                }
                DropdownMenu(
                    expanded         = fabMenuExpanded,
                    onDismissRequest = { fabMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.fab_logga_aktivitet)) },
                        leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
                        onClick     = { fabMenuExpanded = false; onAddAktivitet() },
                    )
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.home_fab_log_screening)) },
                        leadingIcon = { Icon(Icons.Filled.MonitorHeart, contentDescription = null) },
                        onClick     = { fabMenuExpanded = false; onNavigateToAktiviteter() },
                    )
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.log_single_dose)) },
                        leadingIcon = { Icon(Icons.Filled.Medication, contentDescription = null) },
                        onClick     = { fabMenuExpanded = false; onAddMedicin() },
                    )
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.handelse_new)) },
                        leadingIcon = { Icon(Icons.Outlined.MonitorHeart, contentDescription = null) },
                        onClick     = { fabMenuExpanded = false; onAddHandelse() },
                    )
                }
            }
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

            // Dagens checklista — mediciner (avbockningsbara direkt)
            if (uiState.todayMediciner.isNotEmpty()) {
                item {
                    MedicinChecklistCard(
                        mediciner = uiState.todayMediciner,
                        tagenCount = uiState.tagenCount,
                        hasOverdue = uiState.overdueMediciner.isNotEmpty(),
                        onToggle = vm::toggleMedicinTagen,
                    )
                }
            }

            // Dagens checklista — screening (inline-loggning per måltidstillfälle)
            if (uiState.screeningEvents.isNotEmpty()) {
                item {
                    ScreeningChecklistCard(
                        events = uiState.screeningEvents,
                        vm     = screeningVm,
                    )
                }
            }

            // Pågående sjukdom-kort
            uiState.pagaendeSjukdom?.let { sjukdom ->
                item {
                    DagbokenCard(
                        onClick     = onNavigateToSjukdomar,
                        accentColor = cs.error,
                    ) {
                        Row(
                            modifier          = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Filled.LocalHospital,
                                contentDescription = null,
                                tint     = cs.error,
                                modifier = Modifier.size(28.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.sjukdom_hem_card_pagaende),
                                    style      = MaterialTheme.typography.labelSmall,
                                    color      = cs.onSurfaceVariant,
                                )
                                Text(
                                    sjukdom.typ,
                                    style      = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = cs.onSurface,
                                )
                                Text(
                                    stringResource(R.string.format_sjukdom_hem_sedan, sjukdom.startDatum),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Diagram / Screening card
            item {
                DagbokenCard(title = stringResource(R.string.home_energy_chart_title)) {
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
                            stringResource(R.string.home_no_screening_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    TextButton(
                        onClick  = onNavigateToDiagram,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.home_view_diagram)) }
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
private fun ChecklistCardHeader(title: String, hasOverdue: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier.padding(bottom = 4.dp),
    ) {
        Text(
            title,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color      = cs.onSurface,
        )
        if (hasOverdue) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint     = cs.error,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(R.string.home_overdue_title),
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color      = cs.error,
            )
        }
    }
}

@Composable
private fun MedicinChecklistCard(
    mediciner: List<Medicin>,
    tagenCount: Int,
    hasOverdue: Boolean,
    onToggle: (Medicin) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var showTaken by remember { mutableStateOf(false) }
    val visible = mediciner.filter { !it.tagen || showTaken }

    DagbokenCard(accentColor = if (hasOverdue) cs.error else null) {
        ChecklistCardHeader(
            title      = stringResource(R.string.home_checklist_mediciner_title),
            hasOverdue = hasOverdue,
        )
        visible.forEachIndexed { i, med ->
            if (i > 0) HorizontalDivider(color = cs.outlineVariant)
            val isTagen = med.tagen
            ListItem(
                headlineContent = {
                    Text(
                        text           = med.namn,
                        textDecoration = if (isTagen) TextDecoration.LineThrough else null,
                    )
                },
                supportingContent = {
                    Text("${med.dos} ${med.enhet}  ·  ${med.tidpunkt}")
                },
                leadingContent  = {
                    Icon(Icons.Filled.Medication, contentDescription = null, tint = cs.onSurfaceVariant)
                },
                trailingContent = {
                    IconButton(onClick = { onToggle(med) }) {
                        Icon(
                            imageVector = if (isTagen) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (isTagen)
                                stringResource(R.string.format_mark_as_untaken, med.namn)
                            else
                                stringResource(R.string.format_mark_as_taken, med.namn),
                            tint = if (isTagen) cs.primary else cs.onSurfaceVariant,
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (tagenCount > 0) {
            TextButton(onClick = { showTaken = !showTaken }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (showTaken) stringResource(R.string.idag_dolj_tagna)
                    else stringResource(R.string.idag_visa_tagna_count, tagenCount),
                )
            }
        }
    }
}

@Composable
private fun ScreeningChecklistCard(
    events: List<ScreeningEventStatus>,
    vm: AktiviteterViewModel,
) {
    val cs = MaterialTheme.colorScheme
    var expandedLabel by remember { mutableStateOf<String?>(null) }
    val hasOverdue = events.any { it.overdue }

    DagbokenCard(accentColor = if (hasOverdue) cs.error else null) {
        ChecklistCardHeader(
            title      = stringResource(R.string.home_daily_screening),
            hasOverdue = hasOverdue,
        )
        events.forEachIndexed { i, event ->
            if (i > 0) HorizontalDivider(color = cs.outlineVariant)
            val expanded = expandedLabel == event.label
            val rotation by animateFloatAsState(
                targetValue   = if (expanded) 180f else 0f,
                animationSpec = DagbokenAnimSpec.springNormal,
                label         = "screening_chevron_${event.label}",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !event.logged) {
                        expandedLabel = if (expanded) null else event.label
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                    Text(
                        text = when {
                            event.logged  -> stringResource(R.string.home_checklist_screening_logged)
                            event.overdue -> stringResource(R.string.format_home_screening_reminder, event.time)
                            else          -> stringResource(R.string.home_checklist_screening_upcoming)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (event.overdue) cs.error else cs.onSurfaceVariant,
                    )
                }
                when {
                    event.logged -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = cs.primary)
                    else -> Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.rotate(rotation),
                        tint = cs.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && !event.logged,
                enter   = expandVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
                exit    = shrinkVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
            ) {
                InlineScreeningForm(
                    label = event.label,
                    vm    = vm,
                    onSaved = { expandedLabel = null },
                )
            }
        }
    }
}

@Composable
private fun InlineScreeningForm(
    label: String,
    vm: AktiviteterViewModel,
    onSaved: () -> Unit,
) {
    val form by vm.form.collectAsState()

    LaunchedEffect(label) {
        vm.updateForm { copy(aktivitet = label, type = "screening", energy = 0, stress = 0) }
    }

    Column(
        modifier = Modifier.padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GradientSliderRow(
            label          = stringResource(R.string.label_energy),
            emoji          = "⚡",
            value          = form.energy.coerceIn(0, 10).toFloat(),
            onValueChange  = { vm.updateForm { copy(energy = it.toInt()) } },
            valueRange     = 0f..10f,
            steps          = 9,
            startLabel     = "0  😴",
            endLabel       = "😊  10",
        )
        GradientSliderRow(
            label         = stringResource(R.string.label_stress),
            emoji         = "😰",
            value         = form.stress.toFloat(),
            onValueChange = { vm.updateForm { copy(stress = it.toInt()) } },
            valueRange    = 0f..10f,
            steps         = 9,
            startLabel    = "0  😌",
            endLabel      = "😰  10",
            reverseColors = true,
        )
        Button(
            onClick  = { vm.save(onSaved) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save)) }
    }
}
