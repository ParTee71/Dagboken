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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import se.partee71.dagboken.domain.model.pagaende
import se.partee71.dagboken.domain.usecase.SymptomUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SjukdomsEpisodDetailScreen(
    onBack: () -> Unit,
    onAddIncheckning: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: SjukdomsEpisodViewModel = hiltViewModel(),
) {
    val episod       by vm.episod.collectAsStateWithLifecycle()
    val incheckningar by vm.incheckningar.collectAsStateWithLifecycle()
    val snackbar     by vm.snackbar.collectAsStateWithLifecycle()

    LaunchedEffect(snackbar) {
        snackbar?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    var deleteInchTarget by remember { mutableStateOf<SjukdomsIncheckning?>(null) }
    var showMarkFriskDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(episod?.typ ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
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
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Text(ep.typ, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                StatusChip(pagaende = ep.pagaende)
                            }
                            Text(
                                text  = stringResource(R.string.sjukdom_label_start) + ": ${ep.startDatum}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (ep.slutDatum.isNotBlank()) {
                                Text(
                                    text  = stringResource(R.string.sjukdom_label_slut) + ": ${ep.slutDatum}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (ep.anteckning.isNotBlank()) {
                                Text(ep.anteckning, style = MaterialTheme.typography.bodySmall)
                            }
                            if (ep.pagaende) {
                                Spacer(Modifier.height(4.dp))
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
            }

            if (incheckningar.isEmpty()) {
                item {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Inga incheckningar ännu. Tryck + för att lägga till.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(incheckningar, key = { it.id }) { incheckning ->
                    IncheckningCardSwipeable(
                        incheckning = incheckning,
                        onDelete    = { deleteInchTarget = incheckning },
                    )
                }
            }
        }
    }

    deleteInchTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteInchTarget = null },
            title            = { Text(stringResource(R.string.delete_incheckning_title)) },
            text             = { Text(stringResource(R.string.delete_incheckning_confirm)) },
            confirmButton    = {
                TextButton(onClick = { vm.deleteIncheckning(target); deleteInchTarget = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton    = {
                TextButton(onClick = { deleteInchTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showMarkFriskDialog) {
        AlertDialog(
            onDismissRequest = { showMarkFriskDialog = false },
            title            = { Text(stringResource(R.string.sjukdom_markera_frisk)) },
            text             = { Text("Markera episoden som avslutad idag?") },
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
        Triple(cs.errorContainer, cs.onErrorContainer, "Pågår")
    } else {
        Triple(cs.primaryContainer, cs.onPrimaryContainer, "Avslutad")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncheckningCardSwipeable(
    incheckning: SjukdomsIncheckning,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        }
    )
    SwipeToDismissBox(
        state             = dismissState,
        backgroundContent = {
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
        IncheckningCard(incheckning = incheckning)
    }
}

@Composable
private fun IncheckningCard(incheckning: SjukdomsIncheckning) {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    "${incheckning.datum}  ${incheckning.tid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                SeverityChip(incheckning.svarighetsgrad)
            }
            if (incheckning.symptom.isNotBlank()) {
                val symptoms = SymptomUtils.decode(incheckning.symptom)
                Text(
                    text  = symptoms.entries.joinToString(", ") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            if (incheckning.anteckning.isNotBlank()) {
                Text(
                    text     = incheckning.anteckning,
                    style    = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
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
    Surface(color = containerColor, shape = MaterialTheme.shapes.small) {
        Text(
            text     = "$value/10",
            style    = MaterialTheme.typography.labelSmall,
            color    = labelColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
