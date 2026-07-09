package se.partee71.dagboken.ui.sjukdomar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.NoteIndicatorIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SjukdomarScreen(
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onDetail: (String) -> Unit,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodCardSwipeable(
    episod: SjukdomsEpisod,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    note: String = "",
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        }
    )
    SwipeToDismissBox(
        state              = dismissState,
        modifier           = modifier,
        backgroundContent  = {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        },
    ) {
        EpisodCard(episod = episod, onClick = onClick, note = note)
    }
}

@Composable
private fun EpisodCard(
    episod: SjukdomsEpisod,
    onClick: () -> Unit,
    note: String = "",
) {
    val cs = MaterialTheme.colorScheme
    DagbokenCard(onClick = onClick) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.LocalHospital,
                contentDescription = null,
                tint     = if (episod.pagaende) cs.error else cs.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(episod.typ, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text  = buildStatusText(episod),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            NoteIndicatorIcon(noteText = note, dialogTitle = episod.typ)
            if (!episod.pagaende) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint     = cs.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Surface(
                    color = cs.errorContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text     = stringResource(R.string.sjukdom_pagaende),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = cs.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
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
