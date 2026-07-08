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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.TIDP_ORDER
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.NoteField

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

    DagbokenScaffold(
        title  = stringResource(if (editId == null) R.string.recept_new else R.string.recept_edit),
        onBack = onBack,
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
                label = { Text(stringResource(R.string.label_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.dos, { vm.updateForm { copy(dos = it) } },
                label = { Text(stringResource(R.string.label_dose)) }, modifier = Modifier.fillMaxWidth())

            var unitExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = it }) {
                OutlinedTextField(
                    value = form.enhet, onValueChange = {}, readOnly = true,
                    label = { Text(stringResource(R.string.label_unit)) },
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

            Text(stringResource(R.string.label_time_slots), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TIDP_ORDER.filter { it != "Vid behov" }.forEach { t ->
                    FilterChip(
                        selected = form.tidpunkter.contains(t),
                        onClick  = { vm.toggleTidpunkt(t) },
                        label    = { Text(t) },
                    )
                }
            }

            var uppExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = uppExpanded, onExpandedChange = { uppExpanded = it }) {
                OutlinedTextField(
                    value = UPPREPNING_LABELS[form.upprepning] ?: form.upprepning,
                    onValueChange = {}, readOnly = true,
                    label = { Text(stringResource(R.string.label_recurrence)) },
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

            if (form.upprepning == "anpassad") {
                Text(stringResource(R.string.label_days), style = MaterialTheme.typography.labelMedium)
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

            if (form.upprepning == "intervall") {
                OutlinedTextField(
                    value = form.intervalDagar.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { vm.updateForm { copy(intervalDagar = it) } }
                    },
                    label = { Text(stringResource(R.string.label_interval_days)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            NoteField(
                text         = form.anteckning,
                onTextChange = { vm.updateForm { copy(anteckning = it) } },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_active), modifier = Modifier.weight(1f))
                Switch(checked = form.aktiv, onCheckedChange = { vm.updateForm { copy(aktiv = it) } })
            }

            Button(
                onClick = { scope.launch { vm.save(); onBack() } },
                enabled = form.namn.isNotBlank() && form.dos.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
