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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.TIDP_ORDER
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.NoteField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicinScreen(
    editId: String?,
    onBack: () -> Unit,
    vm: AddEditMedicinViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { editId?.let { vm.loadForEdit(it) } }

    val form by vm.form.collectAsState()
    val scope = rememberCoroutineScope()

    DagbokenScaffold(
        title  = stringResource(if (editId == null) R.string.medicin_new else R.string.edit),
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
                    value = form.enhet,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.label_unit)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                    listOf("mg", "ml", "st", "g", "mcg", "IE", "dropp").forEach { unit ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(unit) },
                            onClick = { vm.updateForm { copy(enhet = unit) }; unitExpanded = false },
                        )
                    }
                }
            }

            Text(stringResource(R.string.label_time_slot))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TIDP_ORDER.forEach { t ->
                    FilterChip(
                        selected = form.tidpunkt == t,
                        onClick  = { vm.updateForm { copy(tidpunkt = t) } },
                        label    = { Text(t) },
                    )
                }
            }

            NoteField(
                text         = form.anteckning,
                onTextChange = { vm.updateForm { copy(anteckning = it) } },
            )

            Button(
                onClick = { scope.launch { vm.save(); onBack() } },
                enabled = form.namn.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
