package se.partee71.dagboken.ui.sjukdomar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SjukdomarScreen(
    onAddNew: () -> Unit,
    onDetail: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: SjukdomarViewModel = hiltViewModel(),
) {
    val all     by vm.all.collectAsStateWithLifecycle()
    val snackbar by vm.snackbar.collectAsStateWithLifecycle()

    LaunchedEffect(snackbar) {
        snackbar?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    var deleteTarget by remember { mutableStateOf<SjukdomsEpisod?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.sjukdomar_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_new))
            }
        },
    ) { innerPadding ->
        if (all.isEmpty()) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.LocalHospital,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.sjukdomar_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.sjukdomar_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                            onClick  = { onDetail(episod.id) },
                            onDelete = { deleteTarget = episod },
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
                            onClick  = { onDetail(episod.id) },
                            onDelete = { deleteTarget = episod },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title            = { Text(stringResource(R.string.delete_sjukdom_title)) },
            text             = { Text(stringResource(R.string.format_delete_sjukdom_confirm, target.typ)) },
            confirmButton    = {
                TextButton(onClick = { vm.delete(target); deleteTarget = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton    = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodCardSwipeable(
    episod: SjukdomsEpisod,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        }
    )
    SwipeToDismissBox(
        state              = dismissState,
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
        EpisodCard(episod = episod, onClick = onClick)
    }
}

@Composable
private fun EpisodCard(
    episod: SjukdomsEpisod,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
