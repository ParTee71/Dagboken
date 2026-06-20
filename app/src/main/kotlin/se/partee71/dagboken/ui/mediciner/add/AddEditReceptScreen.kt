package se.partee71.dagboken.ui.mediciner.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import se.partee71.dagboken.domain.model.TIDP_ORDER

private val UPPREPNING_OPTIONS = listOf("dagligen", "vardagar", "helger", "anpassad", "intervall")
private val UPPREPNING_LABELS = mapOf(
    "dagligen"  to "Dagligen",
    "vardagar"  to "Vardagar",
    "helger"    to "Helger",
    "anpassad"  to "Specifika dagar",
    "intervall" to "Var X:e dag",
)
private val DAG_LABELS = listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditReceptScreen(
    editId: String?,
    onBack: () -> Unit,
    vm: AddEditReceptViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { editId?.let { vm.loadForEdit(it) } }

    val form by vm.form.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId == null) "Nytt schema" else "Redigera schema") },
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
            OutlinedTextField(form.namn, { vm.updateForm { copy(namn = it) } },
                label = { Text("Namn") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.dos, { vm.updateForm { copy(dos = it) } },
                label = { Text("Dos") }, modifier = Modifier.fillMaxWidth())

            // Enhet dropdown
            var unitExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = it }) {
                OutlinedTextField(
                    value = form.enhet, onValueChange = {}, readOnly = true,
                    label = { Text("Enhet") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(unitExpanded, { unitExpanded = false }) {
                    listOf("mg", "ml", "st", "g", "mcg", "IE", "dropp").forEach { u ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(u) },
                            onClick = { vm.updateForm { copy(enhet = u) }; unitExpanded = false },
                        )
                    }
                }
            }

            // Tidpunkter multi-select
            Text("Tidpunkter", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TIDP_ORDER.filter { it != "Vid behov" }.forEach { t ->
                    FilterChip(
                        selected = form.tidpunkter.contains(t),
                        onClick  = { vm.toggleTidpunkt(t) },
                        label    = { Text(t) },
                    )
                }
            }

            // Upprepning dropdown
            var uppExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = uppExpanded, onExpandedChange = { uppExpanded = it }) {
                OutlinedTextField(
                    value = UPPREPNING_LABELS[form.upprepning] ?: form.upprepning,
                    onValueChange = {}, readOnly = true,
                    label = { Text("Upprepning") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(uppExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(uppExpanded, { uppExpanded = false }) {
                    UPPREPNING_OPTIONS.forEach { opt ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(UPPREPNING_LABELS[opt] ?: opt) },
                            onClick = { vm.updateForm { copy(upprepning = opt) }; uppExpanded = false },
                        )
                    }
                }
            }

            // Specifika dagar checkboxes
            if (form.upprepning == "anpassad") {
                Text("Dagar", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DAG_LABELS.forEachIndexed { index, label ->
                        FilterChip(
                            selected = form.dagar.contains(index),
                            onClick  = { vm.toggleDag(index) },
                            label    = { Text(label) },
                        )
                    }
                }
            }

            // Intervall days
            if (form.upprepning == "intervall") {
                OutlinedTextField(
                    value = form.intervalDagar.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { vm.updateForm { copy(intervalDagar = it) } }
                    },
                    label = { Text("Var X:e dag") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(form.anteckning, { vm.updateForm { copy(anteckning = it) } },
                label = { Text("Anteckning") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aktiv", modifier = Modifier.weight(1f))
                Switch(checked = form.aktiv, onCheckedChange = { vm.updateForm { copy(aktiv = it) } })
            }

            Button(
                onClick = { scope.launch { vm.save(); onBack() } },
                enabled = form.namn.isNotBlank() && form.dos.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Spara") }
        }
    }
}
