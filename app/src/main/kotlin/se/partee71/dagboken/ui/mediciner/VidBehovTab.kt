package se.partee71.dagboken.ui.mediciner

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import se.partee71.dagboken.ui.components.NoteIndicatorIcon
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun VidBehovTab(
    vm: MedicinerViewModel,
    onEdit: (String) -> Unit,
) {
    val allFavoriter    by vm.allFavoriter.collectAsState()
    val favoriter       by vm.favoriteFavoriter.collectAsState()
    val others          by vm.otherFavoriter.collectAsState()
    val favoritNotes    by vm.favoritNotes.collectAsState()
    val cooldownWarning by vm.cooldownWarning.collectAsState()
    var deleteTarget    by remember { mutableStateOf<Favorit?>(null) }
    val cs = MaterialTheme.colorScheme

    if (allFavoriter.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = cs.secondary.copy(alpha = 0.3f),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.empty_favoriter_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.empty_favoriter_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.vid_behov_hint_tap),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.vid_behov_hint_hold),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(16.dp))
        FavoriterRow(
            favoriter        = favoriter,
            others           = others,
            onTap            = { vm.quickDos(it) },
            onEdit           = onEdit,
            onDelete         = { deleteTarget = it },
            onToggleFavorite = { vm.toggleFavoritFavorite(it) },
            notes            = favoritNotes,
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_favorit_title)) },
            text  = { Text(stringResource(R.string.format_delete_favorit_confirm, target.namn)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteFavorit(target); deleteTarget = null }) {
                    Text(stringResource(R.string.delete), color = cs.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    cooldownWarning?.let { warning ->
        val h = warning.remainingHours.toInt()
        val m = ((warning.remainingHours - h) * 60).toInt()
        AlertDialog(
            onDismissRequest = { vm.dismissCooldownWarning() },
            title = { Text(stringResource(R.string.cooldown_warning_title)) },
            text  = {
                Text(
                    stringResource(
                        R.string.format_cooldown_warning_body,
                        String.format(Locale.ROOT, "%d", h),
                        String.format(Locale.ROOT, "%02d", m),
                        warning.favorit.namn,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.forceDos(warning.favorit) }) {
                    Text(stringResource(R.string.cooldown_take_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissCooldownWarning() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun FavoriterRow(
    favoriter: List<Favorit>,
    others: List<Favorit> = emptyList(),
    onTap: (Favorit) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: ((Favorit) -> Unit)? = null,
    onToggleFavorite: ((Favorit) -> Unit)? = null,
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
            Box {
                ElevatedCard(
                    modifier = Modifier.combinedClickable(
                        onClick     = { onTap(fav) },
                        onLongClick = {
                            if (onDelete != null) menuExpanded = true else onEdit(fav.id)
                        },
                    ),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = cs.secondaryContainer,
                    ),
                    shape = MaterialTheme.shapes.large,
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
                if (onDelete != null) {
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; onEdit(fav.id) },
                        )
                        if (onToggleFavorite != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (fav.isFavorite) stringResource(R.string.favorit_unmark_favorite)
                                        else stringResource(R.string.favorit_mark_favorite),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (fav.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    )
                                },
                                onClick = { menuExpanded = false; onToggleFavorite(fav) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = cs.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = cs.error) },
                            onClick = { menuExpanded = false; onDelete(fav) },
                        )
                    }
                }
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
