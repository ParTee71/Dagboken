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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
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
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel
import se.partee71.dagboken.ui.components.AccountBottomSheet
import se.partee71.dagboken.ui.components.AccountBubble
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.domain.model.WeeklyHealth
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.StatPill
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.NoteIndicatorIcon
import se.partee71.dagboken.ui.components.StepwiseScreeningForm
import se.partee71.dagboken.ui.formatDisplayDate
import se.partee71.dagboken.ui.formatWeekdayShort
import se.partee71.dagboken.ui.mediciner.MedicinerViewModel
import se.partee71.dagboken.ui.theme.DagbokenAnimSpec
import java.time.LocalDate
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToTrender: () -> Unit,
    onNavigateToSjukdomar: () -> Unit,
    onAddAktivitet: () -> Unit,
    onAddMedicin: () -> Unit,
    onAddHandelse: (LocalDate) -> Unit,
    onAddFavorit: () -> Unit,
    onEditFavorit: (String) -> Unit,
    onOpenHalsa: () -> Unit,
    snackbarHostState: SnackbarHostState,
    initialExpandedScreeningLabel: String? = null,
    onScreeningLabelConsumed: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
    screeningVm: AktiviteterViewModel = hiltViewModel(),
    medicinerVm: MedicinerViewModel = hiltViewModel(),
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

    val medicinerSnackbar by medicinerVm.snackbar.collectAsState()
    LaunchedEffect(medicinerSnackbar) {
        medicinerSnackbar?.let { snackbarHostState.showSnackbar(it); medicinerVm.clearSnackbar() }
    }
    val allFavoriter by medicinerVm.allFavoriter.collectAsState()
    val cooldownWarning by medicinerVm.cooldownWarning.collectAsState()
    val weekSummary by vm.weekSummary.collectAsState()
    val healthCard by vm.healthCard.collectAsState()

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
                        text        = { Text(stringResource(R.string.log_single_dose)) },
                        leadingIcon = { Icon(Icons.Filled.Medication, contentDescription = null) },
                        onClick     = { fabMenuExpanded = false; onAddMedicin() },
                    )
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.home_fab_new_favorit)) },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                        onClick     = { fabMenuExpanded = false; onAddFavorit() },
                    )
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.handelse_new)) },
                        leadingIcon = { Icon(Icons.Outlined.MonitorHeart, contentDescription = null) },
                        onClick     = { fabMenuExpanded = false; onAddHandelse(uiState.selectedDate) },
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

            // Veckosammanfattning (visas i början av veckan, sön/mån) — ovanför datumnavigeringen
            weekSummary?.let { summary ->
                item { WeekSummaryCard(summary) }
            }

            // Idag-kortet — datumnavigering (#114) + dagens checklistor (mediciner, screening,
            // vid behov), grupperade i ett gemensamt kort eftersom alla styrs av samma valda dag
            item {
                IdagChecklistCard(
                    selectedDate                  = uiState.selectedDate,
                    isToday                       = uiState.isToday,
                    onPreviousDay                 = vm::previousDay,
                    onNextDay                     = vm::nextDay,
                    mediciner                     = uiState.todayMediciner,
                    tagenCount                    = uiState.tagenCount,
                    medicinerOverdue              = uiState.overdueMediciner.isNotEmpty(),
                    onToggleMedicin               = vm::toggleMedicinTagen,
                    screeningEvents               = uiState.screeningEvents,
                    screeningVm                   = screeningVm,
                    initialExpandedScreeningLabel = initialExpandedScreeningLabel,
                    onScreeningLabelConsumed      = onScreeningLabelConsumed,
                    allFavoriter                  = allFavoriter,
                    medicinerVm                   = medicinerVm,
                    onEditFavorit                 = onEditFavorit,
                )
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

            // Hälsokort (Health Connect: stegtrend 7 dagar + vilopuls) — HLS-7.
            // Placerat här, direkt ovanför energidiagrammet, eftersom båda hör
            // tematiskt ihop (trender över tid) snarare än med dagens checklistor.
            when (val hc = healthCard) {
                is HealthCardUiState.Data ->
                    if (hc.weekly.hasAnyData) item { HealthTrendCard(hc.weekly) }
                HealthCardUiState.NotConnected ->
                    item { HealthConnectPrompt(onClick = onOpenHalsa) }
                HealthCardUiState.Loading -> Unit
            }

            // Diagram / Screening card
            item {
                DagbokenCard(title = stringResource(R.string.home_energy_chart_title)) {
                    if (uiState.screeningPoints.size >= 2) {
                        SparklineChart(
                            points   = uiState.screeningPoints,
                            xLabels  = uiState.screeningLabels,
                            modifier = Modifier.padding(top = 8.dp),
                        )
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
                        onClick  = onNavigateToTrender,
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

    cooldownWarning?.let { warning ->
        val h = warning.remainingHours.toInt()
        val m = ((warning.remainingHours - h) * 60).toInt()
        AlertDialog(
            onDismissRequest = { medicinerVm.dismissCooldownWarning() },
            title = { Text(stringResource(R.string.cooldown_warning_title)) },
            text  = {
                Text(
                    stringResource(
                        R.string.format_cooldown_warning_body,
                        String.format(Locale.ROOT, "%d", h),
                        String.format(Locale.ROOT, "%02d", m),
                        warning.favorit.namn,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { medicinerVm.forceDos(warning.favorit) }) {
                    Text(stringResource(R.string.cooldown_take_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { medicinerVm.dismissCooldownWarning() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Datumnavigering (#114) — bläddra Idag-checklistan till en tidigare dag. */
@Composable
private fun DateNavRow(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.home_previous_day),
                tint = cs.onSurfaceVariant,
            )
        }
        Text(
            text       = formatDisplayDate(selectedDate.toString()),
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color      = cs.onSurface,
        )
        IconButton(onClick = onNext, enabled = !isToday) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.home_next_day),
                tint = if (isToday) cs.onSurfaceVariant.copy(alpha = 0.3f) else cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekSummaryCard(summary: WeekSummary) {
    val cs = MaterialTheme.colorScheme
    val energyText = when (summary.energyTrend) {
        EnergyTrend.UP   -> stringResource(R.string.home_week_energy_up)
        EnergyTrend.DOWN -> stringResource(R.string.home_week_energy_down)
        EnergyTrend.FLAT -> stringResource(R.string.home_week_energy_flat)
    }
    DagbokenCard(title = stringResource(R.string.home_week_summary_title)) {
        Text(energyText, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
        Text(
            stringResource(R.string.format_home_week_doses, summary.dosesTakenPercent),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
internal fun HealthTrendCard(weekly: WeeklyHealth) {
    val cs = MaterialTheme.colorScheme
    val dash = stringResource(R.string.halsa_no_value)

    DagbokenCard(title = stringResource(R.string.home_health_title)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatPill(
                icon           = Icons.Filled.DirectionsWalk,
                value          = weekly.stepsToday?.toString() ?: dash,
                label          = stringResource(R.string.home_health_steps_today),
                containerColor = cs.primaryContainer,
                contentColor   = cs.onPrimaryContainer,
                modifier       = Modifier.weight(1f),
            )
            StatPill(
                icon           = Icons.Filled.MonitorHeart,
                value          = weekly.restingHeartRate?.let { stringResource(R.string.halsa_bpm, it) } ?: dash,
                label          = stringResource(R.string.home_health_resting_hr),
                containerColor = cs.secondaryContainer,
                contentColor   = cs.onSecondaryContainer,
                modifier       = Modifier.weight(1f),
            )
        }
        if (weekly.hasStepTrend) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.home_health_steps_trend),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
            SparklineChart(
                points   = weekly.dailySteps.map { it.steps.toFloat() },
                xLabels  = weekly.dailySteps.map { formatWeekdayShort(it.date) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (weekly.hasRestingHeartRateTrend) {
            val known = weekly.dailyRestingHeartRate.filter { it.bpm != null }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.home_health_resting_hr_trend),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
            SparklineChart(
                points   = known.map { it.bpm!!.toFloat() },
                xLabels  = known.map { formatWeekdayShort(it.date) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun HealthConnectPrompt(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    DagbokenCard(onClick = onClick) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.MonitorHeart,
                contentDescription = null,
                tint     = cs.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_health_connect_title),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = cs.onSurface,
                )
                Text(
                    stringResource(R.string.home_health_connect_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint     = cs.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
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

/**
 * Idag-kortet (HEM-16): datumnavigering + dagens checklistor (mediciner, screening,
 * vid behov) i ett gemensamt kort — allt styrs av samma valda dag (HEM-14), så de
 * grupperas visuellt i stället för att visas som fristående kort.
 */
@Composable
private fun IdagChecklistCard(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    mediciner: List<Medicin>,
    tagenCount: Int,
    medicinerOverdue: Boolean,
    onToggleMedicin: (Medicin) -> Unit,
    screeningEvents: List<ScreeningEventStatus>,
    screeningVm: AktiviteterViewModel,
    initialExpandedScreeningLabel: String?,
    onScreeningLabelConsumed: () -> Unit,
    allFavoriter: List<Favorit>,
    medicinerVm: MedicinerViewModel,
    onEditFavorit: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val screeningOverdue = screeningEvents.any { it.overdue }
    val hasOverdue = medicinerOverdue || screeningOverdue

    DagbokenCard(accentColor = if (hasOverdue) cs.error else null) {
        DateNavRow(
            selectedDate = selectedDate,
            isToday      = isToday,
            onPrevious   = onPreviousDay,
            onNext       = onNextDay,
        )
        if (mediciner.isNotEmpty()) {
            HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(vertical = 16.dp))
            MedicinChecklistSection(
                mediciner  = mediciner,
                tagenCount = tagenCount,
                hasOverdue = medicinerOverdue,
                onToggle   = onToggleMedicin,
            )
        }
        if (screeningEvents.isNotEmpty()) {
            HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(vertical = 16.dp))
            ScreeningChecklistSection(
                events               = screeningEvents,
                vm                   = screeningVm,
                selectedDate         = selectedDate,
                initialExpandedLabel = initialExpandedScreeningLabel,
                onInitialConsumed    = onScreeningLabelConsumed,
            )
        }
        if (allFavoriter.isNotEmpty()) {
            HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(vertical = 16.dp))
            VidBehovChecklistSection(vm = medicinerVm, onEdit = onEditFavorit)
        }
    }
}

@Composable
private fun MedicinChecklistSection(
    mediciner: List<Medicin>,
    tagenCount: Int,
    hasOverdue: Boolean,
    onToggle: (Medicin) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var showTaken by remember { mutableStateOf(false) }
    val visible = mediciner.filter { !it.tagen || showTaken }

    Column {
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
private fun VidBehovChecklistSection(
    vm: MedicinerViewModel,
    onEdit: (String) -> Unit,
) {
    val favoriter    by vm.favoriteFavoriter.collectAsState()
    val others       by vm.otherFavoriter.collectAsState()
    val favoritNotes by vm.favoritNotes.collectAsState()
    var deleteTarget by remember { mutableStateOf<Favorit?>(null) }

    Column {
        ChecklistCardHeader(title = stringResource(R.string.home_checklist_vidbehov_title), hasOverdue = false)
        FavoriterRow(
            favoriter        = favoriter,
            others           = others,
            onTap            = { vm.quickDos(it) },
            onEdit           = onEdit,
            onDelete         = { deleteTarget = it },
            onToggleFavorite = { vm.toggleFavoritFavorite(it) },
            notes            = favoritNotes,
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title     = stringResource(R.string.delete_favorit_title),
            text      = stringResource(R.string.format_delete_favorit_confirm, target.namn),
            onConfirm = { vm.deleteFavorit(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FavoriterRow(
    favoriter: List<Favorit>,
    others: List<Favorit> = emptyList(),
    onTap: (Favorit) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: ((Favorit) -> Unit)? = null,
    onToggleFavorite: ((Favorit) -> Unit)? = null,
    notes: Map<String, String> = emptyMap(),
) {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        favoriter.forEach { fav ->
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                DagbokenCard(
                    onClick        = { onTap(fav) },
                    onLongClick    = { if (onDelete != null) menuExpanded = true else onEdit(fav.id) },
                    containerColor = cs.secondaryContainer,
                    contentPadding = PaddingValues(0.dp),
                    fillMaxWidth   = false,
                ) {
                    Row(
                        modifier          = Modifier.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                fav.namn,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSecondaryContainer,
                            )
                            Text(
                                "${fav.dos} ${fav.enhet}",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                        }
                        NoteIndicatorIcon(noteText = notes[fav.id].orEmpty(), dialogTitle = fav.namn)
                    }
                }
                if (onDelete != null) {
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; onEdit(fav.id) },
                        )
                        if (onToggleFavorite != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (fav.isFavorite) stringResource(R.string.favorit_unmark_favorite)
                                        else stringResource(R.string.favorit_mark_favorite),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (fav.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    )
                                },
                                onClick = { menuExpanded = false; onToggleFavorite(fav) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = cs.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = cs.error) },
                            onClick = { menuExpanded = false; onDelete(fav) },
                        )
                    }
                }
            }
        }

        if (others.isNotEmpty()) {
            var showMore by remember { mutableStateOf(false) }
            Box {
                AssistChip(
                    onClick = { showMore = true },
                    label   = { Text(stringResource(R.string.favorit_more_label, others.size)) },
                )
                DropdownMenu(
                    expanded = showMore,
                    onDismissRequest = { showMore = false },
                ) {
                    others.forEach { fav ->
                        DropdownMenuItem(
                            text = { Text("${fav.namn} — ${fav.dos} ${fav.enhet}") },
                            onClick = { showMore = false; onTap(fav) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreeningChecklistSection(
    events: List<ScreeningEventStatus>,
    vm: AktiviteterViewModel,
    selectedDate: LocalDate,
    initialExpandedLabel: String? = null,
    onInitialConsumed: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    var expandedLabel by remember { mutableStateOf<String?>(null) }
    val hasOverdue = events.any { it.overdue }

    // Pre-expand the event the screening "Logga nu"-notisåtgärd pointed at, once its
    // (still-unlogged) card exists, then consume the signal so it fires only once.
    LaunchedEffect(initialExpandedLabel, events) {
        val label = initialExpandedLabel ?: return@LaunchedEffect
        val target = events.firstOrNull { it.label == label } ?: return@LaunchedEffect
        if (!target.logged) expandedLabel = label
        onInitialConsumed()
    }

    Column {
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
                    label        = event.label,
                    vm           = vm,
                    selectedDate = selectedDate,
                    onSaved      = { expandedLabel = null },
                )
            }
        }
    }
}

@Composable
private fun InlineScreeningForm(
    label: String,
    vm: AktiviteterViewModel,
    selectedDate: LocalDate,
    onSaved: () -> Unit,
) {
    val form by vm.form.collectAsState()
    val symptomOptions by vm.symptomOptions.collectAsState()

    LaunchedEffect(label, selectedDate) { vm.startScreening(label, selectedDate) }

    // saveEnabled is always true here: energy=0/stress=0 are legitimate values
    // (not placeholders), so a fresh inline screening is already save-worthy —
    // unlike text-entry forms, there's no "unsaved-from-blank" state to gate on.
    StepwiseScreeningForm(
        energy                  = form.energy,
        onEnergyChange          = { vm.updateForm { copy(energy = it) } },
        stress                  = form.stress,
        onStressChange          = { vm.updateForm { copy(stress = it) } },
        symptomOptions          = symptomOptions,
        symptomScores           = form.symptomScores,
        onScoresChange          = { vm.updateForm { copy(symptomScores = it) } },
        onToggleSymptomFavorite = vm::toggleSymptomFavorite,
        onSave                  = { vm.save(onSaved) },
        modifier                = Modifier.padding(bottom = 12.dp),
    )
}
