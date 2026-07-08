package se.partee71.dagboken.ui.mediciner

import se.partee71.dagboken.ui.formatDisplayDate

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.NoteIndicatorIcon

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HistorikTab(
    vm: MedicinerViewModel,
    onEdit: (id: String) -> Unit,
) {
    val entries by vm.filteredHistory.collectAsState()
    val filter by vm.historyFilter.collectAsState()
    val notes by vm.medicationNotes.collectAsState()
    var deleteTarget by remember { mutableStateOf<Medicin?>(null) }
    val cs = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        FlowRow(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = "recept" in filter,
                onClick  = { vm.toggleHistoryFilter("recept") },
                label    = { Text(stringResource(R.string.filter_recept)) },
            )
            FilterChip(
                selected = "vid_behov" in filter,
                onClick  = { vm.toggleHistoryFilter("vid_behov") },
                label    = { Text(stringResource(R.string.filter_vid_behov)) },
            )
        }
        HorizontalDivider()

        if (entries.isEmpty()) {
            EmptyState(
                icon  = Icons.Outlined.Medication,
                title = stringResource(R.string.empty_medicin_history_title),
                body  = stringResource(R.string.empty_medicin_history_body),
            )
        } else {
            val grouped = entries
                .sortedByDescending { it.timestamp }
                .groupBy { it.datum }
                .entries
                .sortedByDescending { it.key }

            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                grouped.forEach { (datum, dayEntries) ->
                    stickyHeader(key = "header_$datum") {
                        Surface(
                            color    = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text       = formatDisplayDate(datum),
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.primary,
                                modifier   = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 16.dp, bottom = 6.dp),
                            )
                        }
                    }
                    items(dayEntries, key = { it.id }) { medicin ->
                        MedicinHistorikCard(
                            medicin  = medicin,
                            onEdit   = { onEdit(medicin.id) },
                            onDelete = { deleteTarget = medicin },
                            modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 4.dp),
                            noteText = notes[medicin.id].orEmpty(),
                        )
                    }
                    item(key = "spacer_$datum") { Spacer(Modifier.height(4.dp)) }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title        = if (target.receptId != null) stringResource(R.string.idag_skip_dose_title)
                           else stringResource(R.string.idag_delete_one_title),
            text         = if (target.receptId != null) stringResource(R.string.format_idag_skip_body, target.namn)
                           else stringResource(R.string.format_delete_aktivitet_confirm, target.namn),
            confirmLabel = if (target.receptId != null) stringResource(R.string.idag_skip_button)
                           else stringResource(R.string.delete),
            onConfirm    = { vm.deleteMedicin(target); deleteTarget = null },
            onDismiss    = { deleteTarget = null },
        )
    }
}

@Composable
private fun MedicinHistorikCard(
    medicin: Medicin,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    noteText: String = "",
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val statusColor = when {
        medicin.skipped -> cs.error
        medicin.tagen   -> cs.primary
        else            -> cs.onSurfaceVariant
    }
    val statusText = when {
        medicin.skipped -> stringResource(R.string.medicin_status_hoppad)
        medicin.tagen   -> stringResource(R.string.medicin_status_tagen)
        else            -> stringResource(R.string.medicin_status_ej_tagen)
    }

    DagbokenCard(
        modifier       = modifier,
        contentPadding = PaddingValues(12.dp),
        accentColor    = statusColor,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(medicin.namn, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = "${medicin.tid}  •  ${medicin.dos} ${medicin.enhet}  •  $statusText",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            NoteIndicatorIcon(noteText = noteText, dialogTitle = medicin.namn)

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector        = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.alternatives),
                        modifier           = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded         = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick     = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = cs.error) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = cs.error)
                        },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}
