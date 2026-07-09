package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Aktivitet

@Composable
fun RecentAktiviteterSection(
    entries: List<Aktivitet>,
    onEdit: (id: String, type: String) -> Unit,
    onDelete: (Aktivitet) -> Unit,
    modifier: Modifier = Modifier,
    noteMap: Map<String, String> = emptyMap(),
) {
    if (entries.isEmpty()) return

    var deleteTarget by remember { mutableStateOf<Aktivitet?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text  = stringResource(R.string.recent_entries_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        entries.forEach { aktivitet ->
            AktivitetCard(
                aktivitet = aktivitet,
                onEdit    = { onEdit(aktivitet.id, aktivitet.type) },
                onDelete  = { deleteTarget = aktivitet },
                noteText  = noteMap[aktivitet.id].orEmpty(),
            )
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title     = stringResource(R.string.delete_aktivitet_title),
            text      = stringResource(R.string.format_delete_aktivitet_confirm, target.aktivitet),
            onConfirm = { onDelete(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}
