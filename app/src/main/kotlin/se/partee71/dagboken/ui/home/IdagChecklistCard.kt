package se.partee71.dagboken.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.usecase.ScreeningEventStatus
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.StepwiseScreeningForm
import se.partee71.dagboken.ui.formatDisplayDate
import se.partee71.dagboken.ui.theme.DagbokenAnimSpec
import java.time.LocalDate

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
internal fun IdagChecklistCard(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    mediciner: List<Medicin>,
    tagenCount: Int,
    kommandeMediciner: List<Medicin>,
    snartMediciner: List<Medicin>,
    medicinerOverdue: Boolean,
    onToggleMedicin: (Medicin) -> Unit,
    screeningEvents: List<ScreeningEventStatus>,
    screening: ScreeningFormBinding,
    initialExpandedScreeningLabel: String?,
    onScreeningLabelConsumed: () -> Unit,
    vidBehov: VidBehovBinding,
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
                mediciner         = mediciner,
                tagenCount        = tagenCount,
                kommandeMediciner = kommandeMediciner,
                snartMediciner    = snartMediciner,
                hasOverdue        = medicinerOverdue,
                onToggle          = onToggleMedicin,
            )
        }
        if (screeningEvents.isNotEmpty()) {
            HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(vertical = 16.dp))
            ScreeningChecklistSection(
                events               = screeningEvents,
                screening            = screening,
                selectedDate         = selectedDate,
                initialExpandedLabel = initialExpandedScreeningLabel,
                onInitialConsumed    = onScreeningLabelConsumed,
            )
        }
        if (vidBehov.hasAny) {
            HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(vertical = 16.dp))
            VidBehovChecklistSection(vidBehov)
        }
    }
}

@Composable
private fun MedicinChecklistSection(
    mediciner: List<Medicin>,
    tagenCount: Int,
    kommandeMediciner: List<Medicin>,
    snartMediciner: List<Medicin>,
    hasOverdue: Boolean,
    onToggle: (Medicin) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var showTaken by remember { mutableStateOf(false) }
    var showKommande by remember { mutableStateOf(false) }
    val kommandeIds = kommandeMediciner.mapTo(HashSet()) { it.id }
    val snartIds = snartMediciner.mapTo(HashSet()) { it.id }
    val visible = mediciner.filter { med ->
        (!med.tagen || showTaken) && (showKommande || med.id !in kommandeIds)
    }

    Column {
        ChecklistCardHeader(
            title      = stringResource(R.string.home_checklist_mediciner_title),
            hasOverdue = hasOverdue,
        )
        visible.forEachIndexed { i, med ->
            if (i > 0) HorizontalDivider(color = cs.outlineVariant)
            val isTagen = med.tagen
            val tagenState = if (isTagen) {
                stringResource(R.string.label_taken)
            } else {
                stringResource(R.string.state_not_taken)
            }
            ListItem(
                // Hela raden är tryckytan (NFR-17) — tidigare var bara den lilla
                // ikonknappen klickbar, trots att raden ser tryckbar ut.
                modifier = Modifier
                    .clickable(
                        role           = Role.Checkbox,
                        onClickLabel   = if (isTagen) {
                            stringResource(R.string.format_mark_as_untaken, med.namn)
                        } else {
                            stringResource(R.string.format_mark_as_taken, med.namn)
                        },
                    ) { onToggle(med) }
                    .semantics { stateDescription = tagenState },
                headlineContent = {
                    Text(
                        text           = med.namn,
                        textDecoration = if (isTagen) TextDecoration.LineThrough else null,
                    )
                },
                supportingContent = {
                    val suffix = if (med.id in snartIds) "  ·  ${stringResource(R.string.idag_snart)}" else ""
                    Text("${med.dos} ${med.enhet}  ·  ${med.tidpunkt}$suffix")
                },
                leadingContent  = {
                    Icon(Icons.Filled.Medication, contentDescription = null, tint = cs.onSurfaceVariant)
                },
                trailingContent = {
                    // Ren tillståndsindikator: raden äger åtgärden och dess etikett, så
                    // ikonen ska inte läsas upp en gång till av skärmläsaren (NFR-17).
                    Icon(
                        imageVector        = if (isTagen) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint               = if (isTagen) cs.primary else cs.onSurfaceVariant,
                        modifier           = Modifier.padding(12.dp),
                    )
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
        if (kommandeMediciner.isNotEmpty()) {
            TextButton(onClick = { showKommande = !showKommande }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (showKommande) stringResource(R.string.idag_dolj_kommande)
                    else stringResource(R.string.idag_visa_kommande_count, kommandeMediciner.size),
                )
            }
        }
    }
}

@Composable
private fun VidBehovChecklistSection(vidBehov: VidBehovBinding) {
    var deleteTarget by remember { mutableStateOf<Favorit?>(null) }

    Column {
        ChecklistCardHeader(title = stringResource(R.string.home_checklist_vidbehov_title), hasOverdue = false)
        FavoriterRow(
            favoriter        = vidBehov.favoriter,
            others           = vidBehov.others,
            onTap            = vidBehov.onTap,
            onEdit           = vidBehov.onEdit,
            onDelete         = { deleteTarget = it },
            onToggleFavorite = vidBehov.onToggleFavorite,
            onLogEfterhand   = { vidBehov.onLogEfterhand(it.id) },
            notes            = vidBehov.notes,
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title     = stringResource(R.string.delete_favorit_title),
            text      = stringResource(R.string.format_delete_favorit_confirm, target.namn),
            onConfirm = { vidBehov.onDelete(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun ScreeningChecklistSection(
    events: List<ScreeningEventStatus>,
    screening: ScreeningFormBinding,
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

            val loggadState = if (event.logged) {
                stringResource(R.string.home_checklist_screening_logged)
            } else {
                stringResource(R.string.state_not_logged)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !event.logged, role = Role.Button) {
                        expandedLabel = if (expanded) null else event.label
                    }
                    .semantics { stateDescription = loggadState }
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
                    screening    = screening,
                    selectedDate = selectedDate,
                    onSaved      = { expandedLabel = null },
                )
            }
        }
    }
}

@Composable
internal fun InlineScreeningForm(
    label: String,
    screening: ScreeningFormBinding,
    selectedDate: LocalDate,
    onSaved: () -> Unit,
) {
    LaunchedEffect(label, selectedDate) { screening.onStart(label, selectedDate) }

    // saveEnabled is always true here: energy=0/stress=0 are legitimate values
    // (not placeholders), so a fresh inline screening is already save-worthy —
    // unlike text-entry forms, there's no "unsaved-from-blank" state to gate on.
    StepwiseScreeningForm(
        energy                  = screening.energy,
        onEnergyChange          = screening.onEnergyChange,
        stress                  = screening.stress,
        onStressChange          = screening.onStressChange,
        symptomOptions          = screening.symptomOptions,
        symptomScores           = screening.symptomScores,
        onScoresChange          = screening.onScoresChange,
        onToggleSymptomFavorite = screening.onToggleSymptomFavorite,
        onSave                  = { screening.onSave(onSaved) },
        modifier                = Modifier.padding(bottom = 12.dp),
    )
}
