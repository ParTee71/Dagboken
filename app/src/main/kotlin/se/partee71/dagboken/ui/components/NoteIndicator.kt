package se.partee71.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R

/**
 * Anteckningsikonen i ett postkorts trailing-del (NFR-16, MED-12): syns bara när [note] har text;
 * ett tryck visar anteckningen att läsa, med [title] som rubrik och en Stäng-knapp. Den redigeras
 * i postens formulär ([NoteField]), aldrig här.
 */
@Composable
internal fun NoteIndicator(note: String, title: String) {
    if (note.isBlank()) return
    var open by rememberSaveable { mutableStateOf(false) }
    AppIconButton(R.drawable.ic_note, stringResource(R.string.note_show), { open = true })
    if (open) {
        val close = { open = false }
        ConfirmDialog(title, note, stringResource(R.string.close), onConfirm = close, onDismiss = close, dismissLabel = null)
    }
}
