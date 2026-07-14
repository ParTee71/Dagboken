package se.partee71.dagboken.ui.handelser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.DateTimeRow
import se.partee71.dagboken.ui.components.DurationRow
import se.partee71.dagboken.ui.components.GradientSliderRow
import se.partee71.dagboken.ui.components.NoteField
import se.partee71.dagboken.ui.components.SaveButton
import se.partee71.dagboken.ui.components.UnsavedChangesBackHandler

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditHandelseScreen(
    editId: String?,
    onBack: () -> Unit,
    prefillDatum: String? = null,
    vm: HandelserViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId, prefillDatum) {
        when {
            editId != null -> vm.loadForEdit(editId)
            prefillDatum != null -> vm.startNewFor(prefillDatum)
        }
    }

    val form  by vm.form.collectAsStateWithLifecycle()
    val typPicker by vm.typPickerOptions.collectAsStateWithLifecycle()
    val isDirty by vm.isDirty.collectAsStateWithLifecycle()
    val favorites    = typPicker.favorites
    val nonFavorites = typPicker.nonFavorites

    val guardedBack = UnsavedChangesBackHandler(
        isDirty   = isDirty,
        canSave   = form.typ.isNotBlank(),
        onSave    = { vm.save { onBack() } },
        onDiscard = { vm.resetForm(); onBack() },
    )

    DagbokenScaffold(
        title  = stringResource(if (editId == null) R.string.handelse_new else R.string.handelse_edit),
        onBack = guardedBack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Typ
            DagbokenCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text       = stringResource(R.string.handelse_label_typ),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value         = form.typ,
                        onValueChange = { vm.updateForm { copy(typ = it) } },
                        label         = { Text(stringResource(R.string.handelse_label_typ)) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                    )
                    if (favorites.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(4.dp),
                        ) {
                            favorites.forEach { typ ->
                                FilterChip(
                                    selected = form.typ == typ,
                                    onClick  = { vm.updateForm { copy(typ = if (form.typ == typ) "" else typ) } },
                                    label    = { Text(typ) },
                                )
                            }
                        }
                    }

                    if (nonFavorites.isNotEmpty()) {
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        val dropdownValue = if (form.typ in nonFavorites) form.typ else ""

                        ExposedDropdownMenuBox(
                            expanded         = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it },
                        ) {
                            OutlinedTextField(
                                value         = dropdownValue,
                                onValueChange = {},
                                readOnly      = true,
                                placeholder   = { Text(stringResource(R.string.handelse_more_types)) },
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                modifier      = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            )
                            ExposedDropdownMenu(
                                expanded         = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                            ) {
                                nonFavorites.forEach { typ ->
                                    DropdownMenuItem(
                                        text           = { Text(typ) },
                                        onClick        = { vm.updateForm { copy(typ = typ) }; dropdownExpanded = false },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Datum och tid — shared component
            DateTimeRow(
                datum         = form.datum,
                tid           = form.tid,
                onDatumChange = { vm.updateForm { copy(datum = it) } },
                onTidChange   = { vm.updateForm { copy(tid = it) } },
            )

            // Svårighetsgrad — same GradientSliderRow as Aktivitet
            DagbokenCard {
                GradientSliderRow(
                    label         = stringResource(R.string.handelse_label_svarighetsgrad),
                    value         = form.svarighetsgrad.toFloat(),
                    onValueChange = { vm.updateForm { copy(svarighetsgrad = it.toInt()) } },
                    valueRange    = 0f..10f,
                    steps         = 9,
                    startLabel    = "0  Ingen",
                    endLabel      = "10  Extrem",
                    reverseColors = true,
                )
            }

            // Varaktighet — shared component
            DurationRow(
                hours           = form.varaktighetTimmar,
                minutes         = form.varaktighetMinuter,
                onHoursChange   = { vm.updateForm { copy(varaktighetTimmar = it) } },
                onMinutesChange = { vm.updateForm { copy(varaktighetMinuter = it) } },
            )

            // Triggers
            OutlinedTextField(
                value         = form.triggers,
                onValueChange = { vm.updateForm { copy(triggers = it) } },
                label         = { Text(stringResource(R.string.handelse_label_triggers)) },
                placeholder   = { Text(stringResource(R.string.handelse_triggers_hint)) },
                modifier      = Modifier.fillMaxWidth(),
                minLines      = 2,
                maxLines      = 4,
            )

            // Åtgärder
            OutlinedTextField(
                value         = form.atgarder,
                onValueChange = { vm.updateForm { copy(atgarder = it) } },
                label         = { Text(stringResource(R.string.handelse_label_atgarder)) },
                placeholder   = { Text(stringResource(R.string.handelse_atgarder_hint)) },
                modifier      = Modifier.fillMaxWidth(),
                minLines      = 2,
                maxLines      = 4,
            )

            // Anteckning
            NoteField(
                text         = form.anteckning,
                onTextChange = { vm.updateForm { copy(anteckning = it) } },
            )

            SaveButton(
                enabled = isDirty && form.typ.isNotBlank(),
                onClick = { vm.save { onBack() } },
            )
        }
    }
}
