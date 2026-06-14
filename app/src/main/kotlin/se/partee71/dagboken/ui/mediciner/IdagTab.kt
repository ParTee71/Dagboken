package se.partee71.dagboken.ui.mediciner

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.TIDP_ORDER
import se.partee71.dagboken.domain.model.tidpunktSortIndex

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IdagTab(
    vm: MedicinerViewModel,
    onEdit: (String) -> Unit,
) {
    val today by vm.todayMediciner.collectAsState()
    val sorted = today.sortedBy { tidpunktSortIndex(it.tidpunkt) }
    var deleteTarget by remember { mutableStateOf<Medicin?>(null) }

    if (sorted.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Inga mediciner idag",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Lägg till ett schema eller logga en engångsdos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        return
    }

    // Adherence progress
    val tagenCount = sorted.count { it.tagen }
    val total = sorted.size
    val progress = tagenCount.toFloat() / total.toFloat()

    // Group by tidpunkt, preserving TIDP_ORDER sort
    val grouped = sorted.groupBy { it.tidpunkt }
    // Only iterate tidpunkter that have entries, in canonical order
    val orderedGroups = TIDP_ORDER
        .filter { grouped.containsKey(it) }
        .map { it to grouped.getValue(it) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Adherence bar — always visible when list is non-empty
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "$tagenCount av $total tagna",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            orderedGroups.forEach { (tidpunkt, mediciner) ->
                // Sticky tidpunkt section header
                stickyHeader(key = "header_$tidpunkt") {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = tidpunkt.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                }

                items(mediciner, key = { it.id }) { medicin ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                deleteTarget = medicin
                            }
                            false
                        },
                    )

                    LaunchedEffect(deleteTarget) {
                        if (deleteTarget == null) dismissState.reset()
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val isSwiping =
                                dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isSwiping) MaterialTheme.colorScheme.errorContainer
                                        else MaterialTheme.colorScheme.surface,
                                    )
                                    .padding(end = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                if (isSwiping) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Ta bort",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        },
                    ) {
                        val isTagen = medicin.tagen
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = medicin.namn,
                                    textDecoration = if (isTagen) TextDecoration.LineThrough else null,
                                    modifier = if (isTagen) Modifier.alpha(0.5f) else Modifier,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "${medicin.dos} ${medicin.enhet}",
                                    modifier = if (isTagen) Modifier.alpha(0.5f) else Modifier,
                                )
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = medicin.tagen,
                                    onCheckedChange = { vm.toggleTagen(medicin) },
                                )
                            },
                            colors = if (isTagen) {
                                ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                )
                            } else {
                                ListItemDefaults.colors()
                            },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (target.receptId != null) "Hoppa över dos?" else "Ta bort?") },
            text  = {
                Text(
                    if (target.receptId != null)
                        "\"${target.namn}\" markeras som hoppad för idag."
                    else
                        "\"${target.namn}\" raderas permanent.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteMedicin(target); deleteTarget = null }) {
                    Text(
                        if (target.receptId != null) "Hoppa över" else "Ta bort",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Avbryt") }
            },
        )
    }
}
