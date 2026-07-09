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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.GradientSliderRow
import se.partee71.dagboken.ui.components.NoteIndicatorIcon
import se.partee71.dagboken.ui.mediciner.MedicinerViewModel
import se.partee71.dagboken.ui.theme.DagbokenAnimSpec
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToTrender: () -> Unit,
    onNavigateToSjukdomar: () -> Unit,
    onAddAktivitet: () -> Unit,
    onAddMedicin: () -> Unit,
    onAddHandelse: () -> Unit,
    onAddFavorit: () -> Unit,
    onEditFavorit: (String) -> Unit,
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

            // Veckosammanfattning (visas i början av veckan, sön/mån)
            weekSummary?.let { summary ->
                item { WeekSummaryCard(summary) }
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
                        events                = uiState.screeningEvents,
                        vm                    = screeningVm,
                        initialExpandedLabel  = initialExpandedScreeningLabel,
                        onInitialConsumed     = onScreeningLabelConsumed,
                    )
                }
            }

            // Vid behov — favoritmarkerade mediciner (tryck loggar en dos direkt)
            if (allFavoriter.isNotEmpty()) {
                item {
                    VidBehovChecklistCard(vm = medicinerVm, onEdit = onEditFavorit)
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
private fun VidBehovChecklistCard(
    vm: MedicinerViewModel,
    onEdit: (String) -> Unit,
) {
    val favoriter    by vm.favoriteFavoriter.collectAsState()
    val others       by vm.otherFavoriter.collectAsState()
    val favoritNotes by vm.favoritNotes.collectAsState()
    var deleteTarget by remember { mutableStateOf<Favorit?>(null) }

    DagbokenCard {
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
private fun ScreeningChecklistCard(
    events: List<ScreeningEventStatus>,
    vm: AktiviteterViewModel,
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
