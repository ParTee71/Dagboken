package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R

/**
 * Small info icon shown only when [noteText] is non-blank. Tapping it opens a
 * read-only dialog with the full note text — editing still happens via the
 * existing note form for that entity, not from here.
 */
@Composable
fun NoteIndicatorIcon(
    noteText: String,
    modifier: Modifier = Modifier,
    dialogTitle: String? = null,
) {
    if (noteText.isBlank()) return

    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }, modifier = modifier) {
        Icon(
            imageVector        = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.show_note),
            modifier           = Modifier.size(20.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = dialogTitle?.let { { Text(it) } } ?: { Text(stringResource(R.string.label_note)) },
            text  = { Text(noteText) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}
