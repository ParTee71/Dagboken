package se.partee71.dagboken.ui.handelser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.formatDisplayDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HandelserScreen(
    onAddNew: () -> Unit,
    onEdit: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: HandelserViewModel = hiltViewModel(),
) {
    val state   by vm.state.collectAsStateWithLifecycle()
    val snackbar by vm.snackbar.collectAsStateWithLifecycle()
    val notes    by vm.handelseNotes.collectAsStateWithLifecycle()

    LaunchedEffect(snackbar) {
        snackbar?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    var deleteTarget by remember { mutableStateOf<Handelse?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.handelser_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.handelser_add))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            DateFilterRow(
                selected  = state.dagFilter,
                onSelect  = vm::setDagFilter,
            )
            if (state.allTyper.isNotEmpty()) {
                TypFilterRow(
                    typer    = state.allTyper,
                    selected = state.typFilter,
                    onSelect = vm::setTypFilter,
                )
            }

            if (state.filteredHandelser.isEmpty()) {
                EmptyState(
                    icon  = Icons.Outlined.MonitorHeart,
                    title = stringResource(R.string.empty_handelser_title),
                    body  = stringResource(R.string.empty_handelser_body),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.filteredHandelser, key = { it.id }) { handelse ->
                        HandelseCard(
                            handelse = handelse,
                            note     = notes[handelse.id].orEmpty(),
                            onEdit   = { onEdit(handelse.id) },
                            onDelete = { deleteTarget = handelse },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title     = stringResource(R.string.delete),
            text      = stringResource(R.string.handelser_delete_confirm, target.typ),
            onConfirm = { vm.delete(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateFilterRow(
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    val options = listOf(null to "Allt", 7 to "7 dagar", 30 to "30 dagar", 90 to "90 dagar")
    FlowRow(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (days, label) ->
            FilterChip(
                selected = selected == days,
                onClick  = { onSelect(days) },
                label    = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypFilterRow(
    typer: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    FlowRow(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick  = { onSelect(null) },
            label    = { Text(stringResource(R.string.handelser_filter_all_types)) },
        )
        typer.forEach { typ ->
            FilterChip(
                selected = selected == typ,
                onClick  = { onSelect(if (selected == typ) null else typ) },
                label    = { Text(typ) },
            )
        }
    }
}

@Composable
private fun HandelseCard(
    handelse: Handelse,
    note: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    DagbokenCard(contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(
            modifier            = Modifier.fillMaxWidth(),
            verticalAlignment   = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment      = Alignment.CenterVertically,
                    horizontalArrangement  = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text       = handelse.typ,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SeverityChip(handelse.svarighetsgrad)
                }
                Text(
                    text  = "${formatDisplayDate(handelse.datum)}  ${handelse.tid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (handelse.varaktighetMinuter > 0) {
                    Text(
                        text  = formatDuration(handelse.varaktighetMinuter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (note.isNotBlank()) {
                    Text(
                        text     = note,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(
                    expanded         = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text         = { Text(stringResource(R.string.edit)) },
                        leadingIcon  = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick      = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text         = { Text(stringResource(R.string.delete)) },
                        leadingIcon  = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick      = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeverityChip(value: Int) {
    val cs = MaterialTheme.colorScheme
    val (containerColor, labelColor) = when {
        value <= 3 -> cs.primaryContainer to cs.onPrimaryContainer
        value <= 6 -> cs.tertiaryContainer to cs.onTertiaryContainer
        else       -> cs.errorContainer to cs.onErrorContainer
    }
    Surface(
        color  = containerColor,
        shape  = MaterialTheme.shapes.small,
    ) {
        Text(
            text     = "$value/10",
            style    = MaterialTheme.typography.labelSmall,
            color    = labelColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h tim"
        else   -> "$h tim $m min"
    }
}
