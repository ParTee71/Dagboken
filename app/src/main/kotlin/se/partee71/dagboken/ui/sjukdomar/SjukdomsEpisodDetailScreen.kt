package se.partee71.dagboken.ui.sjukdomar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import se.partee71.dagboken.domain.model.pagaende
import se.partee71.dagboken.domain.usecase.SymptomUtils
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.DagbokenEntryCard
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.EntryAction

@Composable
fun SjukdomsEpisodDetailScreen(
    onBack: () -> Unit,
    onAddIncheckning: (String) -> Unit,
    onEditEpisod: (String) -> Unit,
    onEditIncheckning: (episodId: String, incheckningId: String) -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: SjukdomsEpisodViewModel = hiltViewModel(),
) {
    val episod       by vm.episod.collectAsStateWithLifecycle()
    val episodNote   by vm.episodNote.collectAsStateWithLifecycle()
    val incheckningar by vm.incheckningar.collectAsStateWithLifecycle()
    val incheckningNotes by vm.incheckningNotes.collectAsStateWithLifecycle()
    val snackbar     by vm.snackbar.collectAsStateWithLifecycle()

    LaunchedEffect(snackbar) {
        snackbar?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    var deleteInchTarget by remember { mutableStateOf<SjukdomsIncheckning?>(null) }
    var showMarkFriskDialog by remember { mutableStateOf(false) }

    DagbokenScaffold(
        title        = episod?.typ ?: "",
        onBack       = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            episod?.let { ep ->
                FloatingActionButton(onClick = { onAddIncheckning(ep.id) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sjukdom_incheckning_title))
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start  = 16.dp,
                end    = 16.dp,
                top    = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                episod?.let { ep ->
                    val startLabel = stringResource(R.string.sjukdom_label_start)
                    val slutLabel  = stringResource(R.string.sjukdom_label_slut)
                    val datumRad   = if (ep.slutDatum.isBlank()) {
                        "$startLabel: ${ep.startDatum}"
                    } else {
                        "$startLabel: ${ep.startDatum}  •  $slutLabel: ${ep.slutDatum}"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DagbokenEntryCard(
                            title        = ep.typ,
                            onClick      = { onEditEpisod(ep.id) },
                            subtitle     = datumRad,
                            accentColor  = if (ep.pagaende) MaterialTheme.colorScheme.error else null,
                            trailingChip = { StatusChip(pagaende = ep.pagaende) },
                            noteText     = episodNote,
                            actions      = listOf(
                                EntryAction(
                                    label   = stringResource(R.string.edit),
                                    icon    = Icons.Filled.Edit,
                                    onClick = { onEditEpisod(ep.id) },
                                ),
                            ),
                        )
                        if (ep.pagaende) {
                            Button(
                                onClick  = { showMarkFriskDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.sjukdom_markera_frisk))
                            }
                        }
                    }
                }
            }

            if (incheckningar.isEmpty()) {
                item {
                    EmptyState(
                        icon     = Icons.Filled.Checklist,
                        title    = stringResource(R.string.empty_incheckningar_title),
                        body     = stringResource(R.string.empty_incheckningar_body),
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            } else {
                items(incheckningar, key = { it.id }) { incheckning ->
                    IncheckningCard(
                        incheckning = incheckning,
                        note        = incheckningNotes[incheckning.id].orEmpty(),
                        onEdit      = { onEditIncheckning(incheckning.episodId, incheckning.id) },
                        onDelete    = { deleteInchTarget = incheckning },
                        modifier    = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    deleteInchTarget?.let { target ->
        ConfirmDialog(
            title     = stringResource(R.string.delete_incheckning_title),
            text      = stringResource(R.string.delete_incheckning_confirm),
            onConfirm = { vm.deleteIncheckning(target); deleteInchTarget = null },
            onDismiss = { deleteInchTarget = null },
        )
    }

    if (showMarkFriskDialog) {
        AlertDialog(
            onDismissRequest = { showMarkFriskDialog = false },
            title            = { Text(stringResource(R.string.sjukdom_markera_frisk)) },
            text             = { Text(stringResource(R.string.sjukdom_markera_frisk_confirm)) },
            confirmButton    = {
                TextButton(onClick = {
                    episod?.let { vm.markFrisk(it) }
                    showMarkFriskDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton    = {
                TextButton(onClick = { showMarkFriskDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun StatusChip(pagaende: Boolean) {
    val cs = MaterialTheme.colorScheme
    val (containerColor, labelColor, text) = if (pagaende) {
        Triple(cs.errorContainer, cs.onErrorContainer, stringResource(R.string.sjukdom_pagaende))
    } else {
        Triple(cs.primaryContainer, cs.onPrimaryContainer, stringResource(R.string.sjukdom_avslutad))
    }
    Surface(color = containerColor, shape = MaterialTheme.shapes.small) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.labelSmall,
            color    = labelColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun IncheckningCard(
    incheckning: SjukdomsIncheckning,
    note: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val symptoms = SymptomUtils.decode(incheckning.symptom)
    DagbokenEntryCard(
        title        = "${incheckning.datum}  ${incheckning.tid}",
        onClick      = onEdit,
        modifier     = modifier,
        subtitle     = symptoms.entries
            .joinToString(", ") { "${it.key}: ${it.value}" }
            .takeIf { it.isNotBlank() },
        trailingChip = { SeverityChip(incheckning.svarighetsgrad) },
        noteText     = note,
        actions      = listOf(
            EntryAction(
                label   = stringResource(R.string.edit),
                icon    = Icons.Filled.Edit,
                onClick = onEdit,
            ),
        ),
        onDelete     = onDelete,
    )
}

@Composable
private fun SeverityChip(value: Int) {
    val cs = MaterialTheme.colorScheme
    val (containerColor, labelColor) = when {
        value <= 3 -> cs.primaryContainer to cs.onPrimaryContainer
        value <= 6 -> cs.tertiaryContainer to cs.onTertiaryContainer
        else       -> cs.errorContainer to cs.onErrorContainer
    }
    Surface(color = containerColor, shape = MaterialTheme.shapes.small) {
        Text(
            text     = "$value/10",
            style    = MaterialTheme.typography.labelSmall,
            color    = labelColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
