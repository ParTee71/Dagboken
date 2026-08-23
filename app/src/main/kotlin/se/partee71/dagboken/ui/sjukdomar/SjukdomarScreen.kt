package se.partee71.dagboken.ui.sjukdomar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.pagaende
import se.partee71.dagboken.domain.model.varaktighetDagar
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.DagbokenEntryCard
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.EntryAction

@Composable
fun SjukdomarScreen(
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onDetail: (String) -> Unit,
    onEdit: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: SjukdomarViewModel = hiltViewModel(),
) {
    val all     by vm.all.collectAsStateWithLifecycle()
    val notes   by vm.episodNotes.collectAsStateWithLifecycle()
    val snackbar by vm.snackbar.collectAsStateWithLifecycle()

    LaunchedEffect(snackbar) {
        snackbar?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    var deleteTarget by remember { mutableStateOf<SjukdomsEpisod?>(null) }

    DagbokenScaffold(
        title        = stringResource(R.string.sjukdomar_title),
        onBack       = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_new))
            }
        },
    ) { innerPadding ->
        if (all.isEmpty()) {
            EmptyState(
                icon     = Icons.Filled.LocalHospital,
                title    = stringResource(R.string.sjukdomar_empty_title),
                body     = stringResource(R.string.sjukdomar_empty_body),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            val pagaende  = all.filter { it.pagaende }
            val avslutade = all.filter { !it.pagaende }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(innerPadding),
            ) {
                if (pagaende.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.sjukdom_pagaende),
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.error,
                            modifier   = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(pagaende, key = { it.id }) { episod ->
                        EpisodCardSwipeable(
                            episod   = episod,
                            note     = notes[episod.id].orEmpty(),
                            onClick  = { onDetail(episod.id) },
                            onEdit   = { onEdit(episod.id) },
                            onDelete = { deleteTarget = episod },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (avslutade.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.sjukdom_avslutad),
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier   = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(avslutade, key = { it.id }) { episod ->
                        EpisodCardSwipeable(
                            episod   = episod,
                            note     = notes[episod.id].orEmpty(),
                            onClick  = { onDetail(episod.id) },
                            onEdit   = { onEdit(episod.id) },
                            onDelete = { deleteTarget = episod },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title     = stringResource(R.string.delete_sjukdom_title),
            text      = stringResource(R.string.format_delete_sjukdom_confirm, target.typ),
            onConfirm = { vm.delete(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun EpisodCardSwipeable(
    episod: SjukdomsEpisod,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    note: String = "",
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    DagbokenEntryCard(
        title        = episod.typ,
        onClick      = onClick,
        modifier     = modifier,
        subtitle     = buildStatusText(episod),
        leadingIcon  = Icons.Filled.LocalHospital,
        accentColor  = if (episod.pagaende) cs.error else null,
        trailingChip = { StatusChip(pagaende = episod.pagaende) },
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

/** Statuschip för en episod — "Pågår" respektive "Avslutad" (SJ-5). */
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

private fun buildStatusText(episod: SjukdomsEpisod): String {
    return if (episod.pagaende) {
        "Sedan ${episod.startDatum}"
    } else {
        val dagar = episod.varaktighetDagar()
        if (dagar != null) "${episod.startDatum} · $dagar dag(ar)"
        else episod.startDatum
    }
}
