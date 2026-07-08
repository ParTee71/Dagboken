package se.partee71.dagboken.ui.aktiviteter.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel
import se.partee71.dagboken.ui.aktiviteter.ScreeningTab
import se.partee71.dagboken.ui.components.DagbokenScaffold

@Composable
fun AddEditScreeningScreen(
    editId: String,
    onBack: () -> Unit,
    vm: AktiviteterViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { vm.loadForEdit(editId) }

    DagbokenScaffold(
        title  = stringResource(R.string.screening_edit),
        onBack = { vm.resetForm(); onBack() },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            ScreeningTab(vm = vm, onSaved = onBack, showRecent = false)
        }
    }
}
