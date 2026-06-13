package se.partee71.dagboken.ui.mediciner.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditFavoritScreen(
    editId: String?,
    onBack: () -> Unit,
    vm: AddEditFavoritViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { editId?.let { vm.loadForEdit(it) } }

    val form by vm.form
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId == null) "Ny favorit" else "Redigera favorit") },
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
            OutlinedTextField(
                form.namn, { vm.form.value = form.copy(namn = it) },
                label = { Text("Namn") }, modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    form.dos, { vm.form.value = form.copy(dos = it) },
                    label = { Text("Dos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )

                var unitExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = unitExpanded, onExpandedChange = { unitExpanded = it },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = form.enhet, onValueChange = {}, readOnly = true,
                        label = { Text("Enhet") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(unitExpanded, { unitExpanded = false }) {
                        listOf("mg", "ml", "st", "g", "mcg", "IE", "dropp").forEach { u ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(u) },
                                onClick = { vm.form.value = form.copy(enhet = u); unitExpanded = false },
                            )
                        }
                    }
                }
            }

            // Cooldown slider
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Spärrtid", modifier = Modifier.weight(1f))
                    Text(
                        if (form.minTidMellan == 0) "Ingen" else "${form.minTidMellan} tim",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = form.minTidMellan.toFloat(),
                    onValueChange = { vm.form.value = form.copy(minTidMellan = it.roundToInt()) },
                    valueRange = 0f..24f,
                    steps = 23,
                )
            }

            // Daily limit slider
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Max per dag", modifier = Modifier.weight(1f))
                    Text(
                        if (form.maxDoserPerDag == 0) "Obegränsat" else "${form.maxDoserPerDag} ggr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = form.maxDoserPerDag.toFloat(),
                    onValueChange = { vm.form.value = form.copy(maxDoserPerDag = it.roundToInt()) },
                    valueRange = 0f..10f,
                    steps = 9,
                )
            }

            Button(
                onClick = { scope.launch { vm.save(); onBack() } },
                enabled = form.namn.isNotBlank() && form.dos.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Spara") }
        }
    }
}
