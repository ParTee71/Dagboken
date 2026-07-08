package se.partee71.dagboken.ui.mediciner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.EmptyState

private val UPPREPNING_LABELS = mapOf(
    "dagligen"  to "Dagligen",
    "vardagar"  to "Vardagar",
    "helger"    to "Helger",
    "anpassad"  to "Specifika dagar",
    "intervall" to "Var X:e dag",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchemaTab(
    vm: MedicinerViewModel,
    onEdit: (String) -> Unit,
) {
    val recept by vm.allRecept.collectAsState()
    val receptNotes by vm.receptNotes.collectAsState()
    var deleteTarget by remember { mutableStateOf<Recept?>(null) }
    val cs = MaterialTheme.colorScheme

    if (recept.isEmpty()) {
        EmptyState(
            icon  = Icons.AutoMirrored.Outlined.EventNote,
            title = stringResource(R.string.empty_schema_title),
            body  = stringResource(R.string.empty_schema_body),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(recept, key = { it.id }) { r ->
            var expanded by remember { mutableStateOf(false) }
            var menuExpanded by remember { mutableStateOf(false) }
            val chevron by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                label = "schema_chevron",
            )
            val activeColor = if (r.aktiv) cs.tertiary else cs.surfaceVariant

            DagbokenCard(contentPadding = PaddingValues(0.dp), accentColor = activeColor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            r.namn,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (r.aktiv) cs.onSurface else cs.onSurface.copy(alpha = 0.5f),
                        )
                        Text(
                            "${r.dos} ${r.enhet}  •  ${UPPREPNING_LABELS[r.upprepning] ?: r.upprepning}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant.copy(alpha = if (r.aktiv) 1f else 0.5f),
                        )
                    }
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).rotate(chevron),
                        tint = cs.onSurfaceVariant,
                    )
                    Switch(
                        checked = r.aktiv,
                        onCheckedChange = { vm.toggleReceptAktiv(r) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = cs.tertiary,
                            checkedTrackColor = cs.tertiaryContainer,
                        ),
                    )
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.alternatives))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menuExpanded = false; onEdit(r.id) },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.delete), color = cs.error)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, null, tint = cs.error)
                                },
                                onClick = { menuExpanded = false; deleteTarget = r },
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                        Text(
                            stringResource(R.string.label_time_slots),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            r.tidpunkter.forEach { t ->
                                AssistChip(onClick = {}, label = { Text(t) })
                            }
                        }
                        val note = receptNotes[r.id].orEmpty()
                        if (note.isNotBlank()) {
                            Text(
                                note,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title     = stringResource(R.string.delete_schema_title),
            text      = stringResource(R.string.format_delete_schema_confirm, target.namn),
            onConfirm = { vm.deleteRecept(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}
