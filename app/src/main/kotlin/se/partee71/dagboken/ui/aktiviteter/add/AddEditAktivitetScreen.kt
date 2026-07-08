package se.partee71.dagboken.ui.aktiviteter.add

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel
import se.partee71.dagboken.ui.aktiviteter.LoggaTab
import se.partee71.dagboken.ui.components.DagbokenScaffold

@Composable
fun AddEditAktivitetScreen(
    editId: String?,
    onBack: () -> Unit,
    vm: AktiviteterViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { editId?.let { vm.loadForEdit(it) } }

    DagbokenScaffold(
        title  = stringResource(if (editId == null) R.string.aktivitet_new else R.string.aktivitet_edit),
        onBack = { vm.resetForm(); onBack() },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            LoggaTab(vm = vm, onSaved = onBack, showRecent = false)
        }
    }
}
