package se.partee71.dagboken.ui.aktiviteter

import se.partee71.dagboken.ui.formatDisplayDate

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.ui.components.AktivitetCard
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.EmptyState

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HistorikTab(
    vm: AktiviteterViewModel,
    onEdit: (id: String, type: String) -> Unit,
) {
    val entries by vm.filteredHistory.collectAsState()
    val filter by vm.historyFilter.collectAsState()
    val notes by vm.noteMap.collectAsState()
    var deleteTarget by remember { mutableStateOf<Aktivitet?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        FlowRow(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = "aktivitet" in filter,
                onClick  = { vm.toggleHistoryFilter("aktivitet") },
                label    = { Text(stringResource(R.string.filter_aktivitet)) },
            )
            FilterChip(
                selected = "screening" in filter,
                onClick  = { vm.toggleHistoryFilter("screening") },
                label    = { Text(stringResource(R.string.filter_screening)) },
            )
        }
        HorizontalDivider()

        if (entries.isEmpty()) {
            EmptyState(
                icon  = Icons.Outlined.FitnessCenter,
                title = stringResource(R.string.empty_history_title),
                body  = stringResource(R.string.empty_history_body),
            )
        } else {
            val grouped = entries
                .sortedByDescending { it.timestamp }
                .groupBy { it.datum }
                .entries
                .sortedByDescending { it.key }

            LazyColumn(
                modifier        = Modifier.fillMaxSize(),
                contentPadding  = PaddingValues(bottom = 88.dp),
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
                    items(dayEntries, key = { it.id }) { aktivitet ->
                        AktivitetCard(
                            aktivitet = aktivitet,
                            onEdit    = { onEdit(aktivitet.id, aktivitet.type) },
                            onDelete  = { deleteTarget = aktivitet },
                            modifier  = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            noteText  = notes[aktivitet.id].orEmpty(),
                        )
                    }
                    item(key = "spacer_$datum") { Spacer(Modifier.height(4.dp)) }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title     = stringResource(R.string.delete_aktivitet_title),
            text      = stringResource(R.string.format_delete_aktivitet_confirm, target.aktivitet),
            onConfirm = { vm.delete(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}
