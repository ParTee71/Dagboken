package se.partee71.dagboken.ui.aktiviteter.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel
import se.partee71.dagboken.ui.aktiviteter.ScreeningTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreeningScreen(
    editId: String,
    onBack: () -> Unit,
    vm: AktiviteterViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { vm.loadForEdit(editId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screening_edit)) },
                navigationIcon = {
                    IconButton(onClick = { vm.resetForm(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Button(
                    onClick  = { vm.save { onBack() } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.save_screening)) }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            ScreeningTab(vm = vm)
        }
    }
}
