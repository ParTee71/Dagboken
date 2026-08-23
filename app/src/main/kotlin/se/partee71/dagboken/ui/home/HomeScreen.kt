package se.partee71.dagboken.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.BuildConfig
import se.partee71.dagboken.R
import se.partee71.dagboken.data.datastore.SCREENING_EVENT_LABELS
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel
import se.partee71.dagboken.ui.components.AccountBottomSheet
import se.partee71.dagboken.ui.components.AccountBubble
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.formatDayDate
import se.partee71.dagboken.ui.mediciner.MedicinerViewModel
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
    onLogEfterhand: (String) -> Unit,
    onOpenHalsa: () -> Unit,
    snackbarHostState: SnackbarHostState,
    initialExpandedScreeningLabel: String? = null,
    onScreeningLabelConsumed: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
    screeningVm: AktiviteterViewModel = hiltViewModel(),
    medicinerVm: MedicinerViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showAccountSheet by remember { mutableStateOf(false) }
    var fabMenuExpanded by remember { mutableStateOf(false) }
    var showScreeningLabelPicker by remember { mutableStateOf(false) }
    var adHocScreeningLabel by remember { mutableStateOf<String?>(null) }

    val screeningSnackbar by screeningVm.snackbar.collectAsStateWithLifecycle()
    LaunchedEffect(screeningSnackbar) {
        screeningSnackbar?.let { snackbarHostState.showSnackbar(it); screeningVm.clearSnackbar() }
    }

    val medicinerSnackbar by medicinerVm.snackbar.collectAsStateWithLifecycle()
    LaunchedEffect(medicinerSnackbar) {
        medicinerSnackbar?.let { snackbarHostState.showSnackbar(it); medicinerVm.clearSnackbar() }
    }
    val cooldownWarning by medicinerVm.cooldownWarning.collectAsStateWithLifecycle()
    val weekSummary by vm.weekSummary.collectAsStateWithLifecycle()
    val healthCard by vm.healthCard.collectAsStateWithLifecycle()

    // ViewModels stannar här — korten under tar tillstånd och lambdor, se IdagBindings.
    val screeningForm by screeningVm.form.collectAsStateWithLifecycle()
    val symptomOptions by screeningVm.symptomOptions.collectAsStateWithLifecycle()
    val screening = ScreeningFormBinding(
        energy                  = screeningForm.energy,
        stress                  = screeningForm.stress,
        symptomScores           = screeningForm.symptomScores,
        symptomOptions          = symptomOptions,
        onStart                 = screeningVm::startScreening,
        onEnergyChange          = { screeningVm.updateForm { copy(energy = it) } },
        onStressChange          = { screeningVm.updateForm { copy(stress = it) } },
        onScoresChange          = { screeningVm.updateForm { copy(symptomScores = it) } },
        onToggleSymptomFavorite = screeningVm::toggleSymptomFavorite,
        onSave                  = screeningVm::save,
    )

    val favoriteFavoriter by medicinerVm.favoriteFavoriter.collectAsStateWithLifecycle()
    val otherFavoriter by medicinerVm.otherFavoriter.collectAsStateWithLifecycle()
    val favoritNotes by medicinerVm.favoritNotes.collectAsStateWithLifecycle()
    val vidBehov = VidBehovBinding(
        favoriter        = favoriteFavoriter,
        others           = otherFavoriter,
        notes            = favoritNotes,
        onTap            = medicinerVm::quickDos,
        onEdit           = onEditFavorit,
        onDelete         = medicinerVm::deleteFavorit,
        onToggleFavorite = medicinerVm::toggleFavoritFavorite,
        onLogEfterhand   = onLogEfterhand,
    )

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
                text     = stringResource(
                    R.string.format_home_date_week,
                    formatDayDate(LocalDate.now()),
                    isoWeekNumber(),
                ),
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
                        text        = { Text(stringResource(R.string.fab_logga_screening)) },
                        leadingIcon = { Icon(Icons.Filled.Assignment, contentDescription = null) },
                        onClick     = { fabMenuExpanded = false; showScreeningLabelPicker = true },
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
                            text       = stringResource(greetingRes()),
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
                    kommandeMediciner             = uiState.kommandeMediciner,
                    snartMediciner                = uiState.snartMediciner,
                    medicinerOverdue              = uiState.overdueMediciner.isNotEmpty(),
                    onToggleMedicin               = vm::toggleMedicinTagen,
                    screeningEvents               = uiState.screeningEvents,
                    screening                     = screening,
                    initialExpandedScreeningLabel = initialExpandedScreeningLabel,
                    onScreeningLabelConsumed      = onScreeningLabelConsumed,
                    vidBehov                      = vidBehov,
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

            // Hälsokort (Health Connect: steg + vilopuls för vald dag) — HLS-7, HEM-15.
            // Placerat här, direkt ovanför det gemensamma trenddiagrammet (HEM-17), eftersom
            // båda hör tematiskt ihop (hälsa) snarare än med dagens checklistor.
            when (val hc = healthCard) {
                is HealthCardUiState.Data ->
                    if (hc.weekly.hasAnyData) {
                        item { HealthStatsCard(hc.weekly, uiState.selectedDate, uiState.isToday) }
                    }
                HealthCardUiState.NotConnected ->
                    item { HealthConnectPrompt(onClick = onOpenHalsa) }
                HealthCardUiState.Loading -> Unit
            }

            // Gemensamt diagramkort (HEM-17): steg-, vilopuls- och energitrend, i den ordningen.
            item {
                HealthTrendsCard(
                    weekly              = (healthCard as? HealthCardUiState.Data)?.weekly,
                    screeningPoints     = uiState.screeningPoints,
                    screeningLabels     = uiState.screeningLabels,
                    onNavigateToTrender = onNavigateToTrender,
                )
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

    // Fristående screening-loggning från "+"-FAB (#146) — till skillnad från
    // ScreeningChecklistSection kräver den inte att tillfället är schemalagt/
    // ologgat, så användaren kan logga en extra eller ett tillfälle utan påminnelse.
    if (showScreeningLabelPicker) {
        AlertDialog(
            onDismissRequest = { showScreeningLabelPicker = false },
            title            = { Text(stringResource(R.string.fab_logga_screening)) },
            text             = {
                Column {
                    SCREENING_EVENT_LABELS.forEach { label ->
                        TextButton(
                            onClick  = { showScreeningLabelPicker = false; adHocScreeningLabel = label },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScreeningLabelPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    adHocScreeningLabel?.let { label ->
        AlertDialog(
            onDismissRequest = { adHocScreeningLabel = null },
            title            = { Text(label) },
            text             = {
                InlineScreeningForm(
                    label        = label,
                    screening    = screening,
                    selectedDate = uiState.selectedDate,
                    onSaved      = { adHocScreeningLabel = null },
                )
            },
            confirmButton = {
                TextButton(onClick = { adHocScreeningLabel = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
