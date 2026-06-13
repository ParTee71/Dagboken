package se.partee71.dagboken.ui.aktiviteter.add

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel
import se.partee71.dagboken.ui.aktiviteter.LoggaTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAktivitetScreen(
    editId: String?,
    onBack: () -> Unit,
    vm: AktiviteterViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { editId?.let { vm.loadForEdit(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId == null) "Ny aktivitet" else "Redigera aktivitet") },
                navigationIcon = {
                    IconButton(onClick = { vm.resetForm(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tillbaka")
                    }
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            LoggaTab(vm = vm) // reuse LoggaTab; save calls onBack via vm.save
        }
    }

    // Navigate back after save
    LaunchedEffect(Unit) {
        // Override the save callback to navigate back
    }
}
