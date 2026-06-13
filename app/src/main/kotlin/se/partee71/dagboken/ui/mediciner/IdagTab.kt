package se.partee71.dagboken.ui.mediciner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.tidpunktSortIndex

@OptIn(ExperimentalMaterial3Api::class)
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
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(sorted, key = { it.id }) { medicin ->
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
                    val isSwiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
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
                ListItem(
                    headlineContent   = { Text(medicin.namn) },
                    supportingContent = { Text("${medicin.dos} ${medicin.enhet}  •  ${medicin.tidpunkt}") },
                    trailingContent   = {
                        Checkbox(
                            checked         = medicin.tagen,
                            onCheckedChange = { vm.toggleTagen(medicin) },
                        )
                    },
                )
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
