package se.partee71.dagboken.ui.aktiviteter.add

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel
import se.partee71.dagboken.ui.aktiviteter.LoggaTab
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.UnsavedChangesBackHandler

@Composable
fun AddEditAktivitetScreen(
    editId: String?,
    onBack: () -> Unit,
    vm: AktiviteterViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) {
        if (editId != null) vm.loadForEdit(editId) else vm.prefillNewAktivitet()
    }

    val isDirty by vm.isDirty.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val isValid = if (form.aktivitet == "Övrigt") form.aktivitetAnnat.isNotBlank() else form.aktivitet.isNotBlank()
    val guardedBack = UnsavedChangesBackHandler(
        isDirty   = isDirty,
        canSave   = isValid,
        onSave    = { vm.save { onBack() } },
        onDiscard = { vm.resetForm(); onBack() },
    )

    DagbokenScaffold(
        title  = stringResource(if (editId == null) R.string.aktivitet_new else R.string.aktivitet_edit),
        onBack = guardedBack,
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            LoggaTab(vm = vm, onSaved = onBack, showRecent = false)
        }
    }
}
