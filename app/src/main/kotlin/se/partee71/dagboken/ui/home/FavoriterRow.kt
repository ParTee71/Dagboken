package se.partee71.dagboken.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.EntryAction
import se.partee71.dagboken.ui.components.EntryActionMenu
import se.partee71.dagboken.ui.components.NoteIndicatorIcon

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FavoriterRow(
    favoriter: List<Favorit>,
    others: List<Favorit> = emptyList(),
    onTap: (Favorit) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: ((Favorit) -> Unit)? = null,
    onToggleFavorite: ((Favorit) -> Unit)? = null,
    onLogEfterhand: ((Favorit) -> Unit)? = null,
    notes: Map<String, String> = emptyMap(),
) {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        favoriter.forEach { fav ->
            var menuExpanded by remember { mutableStateOf(false) }
            // Chip-kort (NFR-15): tryck loggar en dos, så hela kontextmenyn ligger på
            // långtryck — och den byggs av samma EntryAction-lista som postkortens, så
            // ordning, ikoner och färger inte kan glida isär.
            val actions = buildList {
                add(
                    EntryAction(
                        label   = stringResource(R.string.edit),
                        icon    = Icons.Default.Edit,
                        onClick = { onEdit(fav.id) },
                    ),
                )
                if (onToggleFavorite != null) {
                    add(
                        EntryAction(
                            label   = if (fav.isFavorite) {
                                stringResource(R.string.favorit_unmark_favorite)
                            } else {
                                stringResource(R.string.favorit_mark_favorite)
                            },
                            icon    = Icons.Default.Star,
                            onClick = { onToggleFavorite(fav) },
                        ),
                    )
                }
                if (onLogEfterhand != null) {
                    add(
                        EntryAction(
                            label   = stringResource(R.string.medicin_log_efterhand),
                            icon    = Icons.Filled.Schedule,
                            onClick = { onLogEfterhand(fav) },
                        ),
                    )
                }
                if (onDelete != null) {
                    add(
                        EntryAction(
                            label       = stringResource(R.string.delete),
                            icon        = Icons.Default.Delete,
                            destructive = true,
                            onClick     = { onDelete(fav) },
                        ),
                    )
                }
            }

            Box {
                DagbokenCard(
                    onClick          = { onTap(fav) },
                    onLongClick      = { menuExpanded = true },
                    onClickLabel     = stringResource(R.string.favorit_log_dose),
                    onLongClickLabel = stringResource(R.string.alternatives),
                    containerColor   = cs.secondaryContainer,
                    contentPadding   = PaddingValues(0.dp),
                    fillMaxWidth     = false,
                ) {
                    Row(
                        modifier          = Modifier.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                fav.namn,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSecondaryContainer,
                            )
                            Text(
                                "${fav.dos} ${fav.enhet}",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                        }
                        NoteIndicatorIcon(noteText = notes[fav.id].orEmpty(), dialogTitle = fav.namn)
                    }
                }
                EntryActionMenu(
                    expanded  = menuExpanded,
                    actions   = actions,
                    onDismiss = { menuExpanded = false },
                )
            }
        }

        if (others.isNotEmpty()) {
            var showMore by remember { mutableStateOf(false) }
            Box {
                AssistChip(
                    onClick = { showMore = true },
                    label   = { Text(stringResource(R.string.favorit_more_label, others.size)) },
                )
                DropdownMenu(
                    expanded = showMore,
                    onDismissRequest = { showMore = false },
                ) {
                    others.forEach { fav ->
                        DropdownMenuItem(
                            text = { Text("${fav.namn} — ${fav.dos} ${fav.enhet}") },
                            onClick = { showMore = false; onTap(fav) },
                        )
                    }
                }
            }
        }
    }
}
