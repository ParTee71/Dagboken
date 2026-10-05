package se.partee71.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R

/**
 * Anteckningen på en post (DAT-7, AKT-11, SCR-5, SJ-8): en [Foldout] som i stängt läge visar början
 * av texten (eller "Lägg till en anteckning") och utfälld ett flerradigt [AppTextField]. Fältet `note`
 * skrivs av anroparen via [onNoteChange]; samma tak som rules (`TextLimits.LONG`).
 */
@Composable
fun NoteField(
    note: String,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val label = stringResource(R.string.note)
    Foldout(
        title = if (note.isBlank()) stringResource(R.string.note_placeholder) else label,
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
        summary = note,
    ) {
        AppTextField(note, onNoteChange, label, singleLine = false)
    }
}
