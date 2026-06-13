package se.partee71.dagboken.ui.mediciner.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import se.partee71.dagboken.domain.model.TIDP_ORDER

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicinScreen(
    editId: String?,
    onBack: () -> Unit,
    vm: AddEditMedicinViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { editId?.let { vm.loadForEdit(it) } }

    val form by vm.form
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId == null) "Ny medicinlogg" else "Redigera") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tillbaka")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(form.namn, { vm.form.value = form.copy(namn = it) },
                label = { Text("Namn") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.dos, { vm.form.value = form.copy(dos = it) },
                label = { Text("Dos") }, modifier = Modifier.fillMaxWidth())

            // Unit selector
            var unitExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = it }) {
                OutlinedTextField(
                    value = form.enhet,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Enhet") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                    listOf("mg", "ml", "st", "g", "mcg", "IE", "dropp").forEach { unit ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(unit) },
                            onClick = { vm.form.value = form.copy(enhet = unit); unitExpanded = false },
                        )
                    }
                }
            }

            // Tidpunkt chips
            Text("Tidpunkt")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TIDP_ORDER.forEach { t ->
                    FilterChip(
                        selected = form.tidpunkt == t,
                        onClick  = { vm.form.value = form.copy(tidpunkt = t) },
                        label    = { Text(t) },
                    )
                }
            }

            OutlinedTextField(form.anteckning, { vm.form.value = form.copy(anteckning = it) },
                label = { Text("Anteckning") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2)

            Button(
                onClick = { scope.launch { vm.save(); onBack() } },
                enabled = form.namn.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Spara") }
        }
    }
}
