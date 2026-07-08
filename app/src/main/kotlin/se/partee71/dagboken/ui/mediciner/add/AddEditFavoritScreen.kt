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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.GradientSliderRow
import se.partee71.dagboken.ui.components.NoteField
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditFavoritScreen(
    editId: String?,
    onBack: () -> Unit,
    vm: AddEditFavoritViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { editId?.let { vm.loadForEdit(it) } }

    val form by vm.form.collectAsState()
    val scope = rememberCoroutineScope()

    DagbokenScaffold(
        title  = stringResource(if (editId == null) R.string.favorit_new else R.string.favorit_edit),
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
            OutlinedTextField(
                form.namn, { vm.updateForm { copy(namn = it) } },
                label = { Text(stringResource(R.string.label_name)) }, modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    form.dos, { vm.updateForm { copy(dos = it) } },
                    label = { Text(stringResource(R.string.label_dose)) },
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
            }

            GradientSliderRow(
                label = stringResource(R.string.label_cooldown),
                value = form.minTidMellan.toFloat(),
                onValueChange = { vm.updateForm { copy(minTidMellan = it.roundToInt()) } },
                valueRange = 0f..24f,
                steps = 23,
                displayValue = if (form.minTidMellan == 0) stringResource(R.string.label_no_limit)
                    else stringResource(R.string.format_hours, form.minTidMellan),
                accentColor = MaterialTheme.colorScheme.primary,
                useGradient = false,
            )

            GradientSliderRow(
                label = stringResource(R.string.label_max_per_day),
                value = form.maxDoserPerDag.toFloat(),
                onValueChange = { vm.updateForm { copy(maxDoserPerDag = it.roundToInt()) } },
                valueRange = 0f..10f,
                steps = 9,
                displayValue = if (form.maxDoserPerDag == 0) stringResource(R.string.label_unlimited)
                    else stringResource(R.string.format_times, form.maxDoserPerDag),
                accentColor = MaterialTheme.colorScheme.primary,
                useGradient = false,
            )

            NoteField(
                text         = form.anteckning,
                onTextChange = { vm.updateForm { copy(anteckning = it) } },
            )

            Button(
                onClick = { scope.launch { vm.save(); onBack() } },
                enabled = form.namn.isNotBlank() && form.dos.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
