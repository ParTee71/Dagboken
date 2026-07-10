package se.partee71.dagboken.ui.historik

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
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.formatDisplayDate

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HistorikScreen(
    onBack: (() -> Unit)? = null,
    onEditAktivitet: (id: String, type: String) -> Unit,
    onEditMedicin: (id: String) -> Unit,
    onEditHandelse: (id: String) -> Unit,
    onOpenSjukdomsEpisod: (episodId: String) -> Unit,
    vm: HistorikViewModel = hiltViewModel(),
) {
    val entries by vm.filteredEntries.collectAsState()
    val filter by vm.typeFilter.collectAsState()
    var deleteTarget by remember { mutableStateOf<HistorikEntry?>(null) }

    DagbokenScaffold(
        title  = stringResource(R.string.tab_historik),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FlowRow(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistorikType.entries.forEach { type ->
                    FilterChip(
                        selected = type in filter,
                        onClick  = { vm.toggleFilter(type) },
                        label    = { Text(stringResource(labelRes(type))) },
                    )
                }
            }
            HorizontalDivider()

            if (entries.isEmpty()) {
                EmptyState(
                    icon  = Icons.AutoMirrored.Outlined.EventNote,
                    title = stringResource(R.string.empty_historik_title),
                    body  = stringResource(R.string.empty_historik_body),
                )
            } else {
                val grouped = entries
                    .sortedByDescending { "${it.datum}T${it.tid}" }
                    .groupBy { it.datum }
                    .entries
                    .sortedByDescending { it.key }

                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(bottom = 24.dp),
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
                        items(dayEntries, key = { it.id }) { entry ->
                            HistorikEntryCard(
                                entry    = entry,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                onClick  = {
                                    when (entry) {
                                        is HistorikEntry.AktivitetEntry ->
                                            onEditAktivitet(entry.aktivitet.id, entry.aktivitet.type)
                                        is HistorikEntry.MedicinEntry ->
                                            onEditMedicin(entry.medicin.id)
                                        is HistorikEntry.HandelseEntry ->
                                            onEditHandelse(entry.handelse.id)
                                        is HistorikEntry.IncheckningEntry ->
                                            onOpenSjukdomsEpisod(entry.incheckning.episodId)
                                    }
                                },
                                onDelete = { deleteTarget = entry },
                            )
                        }
                        item(key = "spacer_$datum") { Spacer(Modifier.height(4.dp)) }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title     = stringResource(R.string.historik_delete_title),
            text      = stringResource(R.string.format_historik_delete_confirm, entryTitle(target)),
            onConfirm = { vm.delete(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

private fun labelRes(type: HistorikType): Int = when (type) {
    HistorikType.AKTIVITET -> R.string.nav_tab_aktivitet
    HistorikType.SCREENING -> R.string.filter_screening
    HistorikType.MEDICIN   -> R.string.nav_tab_mediciner
    HistorikType.HANDELSE  -> R.string.nav_tab_handelser
    HistorikType.SJUKDOM   -> R.string.nav_tab_sjukdomar
}

private fun iconFor(type: HistorikType): ImageVector = when (type) {
    HistorikType.AKTIVITET -> Icons.Filled.Bolt
    HistorikType.SCREENING -> Icons.Filled.MonitorHeart
    HistorikType.MEDICIN   -> Icons.Filled.Medication
    HistorikType.HANDELSE  -> Icons.Outlined.MonitorHeart
    HistorikType.SJUKDOM   -> Icons.Filled.LocalHospital
}

private fun entryTitle(entry: HistorikEntry): String = when (entry) {
    is HistorikEntry.AktivitetEntry -> entry.aktivitet.aktivitet
    is HistorikEntry.MedicinEntry -> entry.medicin.namn
    is HistorikEntry.HandelseEntry -> entry.handelse.typ
    is HistorikEntry.IncheckningEntry -> entry.episodTyp
}

private fun entrySubtitle(entry: HistorikEntry): String = when (entry) {
    is HistorikEntry.AktivitetEntry ->
        "${entry.aktivitet.tid}  •  ⚡ ${entry.aktivitet.energy}  •  😰 ${entry.aktivitet.stress}"
    is HistorikEntry.MedicinEntry ->
        "${entry.medicin.tid}  •  ${entry.medicin.dos} ${entry.medicin.enhet}"
    is HistorikEntry.HandelseEntry -> entry.handelse.tid
    is HistorikEntry.IncheckningEntry ->
        "${entry.incheckning.tid}  •  Svårighetsgrad ${entry.incheckning.svarighetsgrad}"
}

@Composable
private fun HistorikEntryCard(
    entry: HistorikEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        DagbokenCard(
            onClick        = onClick,
            onLongClick    = { menuExpanded = true },
            contentPadding = PaddingValues(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    iconFor(entry.entryType),
                    contentDescription = null,
                    tint     = cs.onSurfaceVariant,
                    modifier = Modifier.size(24.dp).padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(entryTitle(entry), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text  = entrySubtitle(entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(
            expanded         = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text        = { Text(stringResource(R.string.delete), color = cs.error) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = cs.error) },
                onClick     = { menuExpanded = false; onDelete() },
            )
        }
    }
}
