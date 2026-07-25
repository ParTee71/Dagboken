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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import se.partee71.dagboken.ui.components.DateTimeRow
import se.partee71.dagboken.ui.components.NoteField
import se.partee71.dagboken.ui.components.SaveButton
import se.partee71.dagboken.ui.components.UnsavedChangesBackHandler
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicinScreen(
    editId: String?,
    favoritId: String? = null,
    onBack: () -> Unit,
    vm: AddEditMedicinViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId, favoritId) {
        editId?.let { vm.loadForEdit(it) }
        favoritId?.let { vm.loadForFavorit(it) }
    }

    val form by vm.form.collectAsState()
    val isDirty by vm.isDirty.collectAsState()
    val cooldownWarning by vm.cooldownWarning.collectAsState()
    val blockedMessage by vm.blockedMessage.collectAsState()
    val saveCompleted by vm.saveCompleted.collectAsState()
    val scope = rememberCoroutineScope()

    // Efterhandsloggning (MED-16) kan blockeras av cooldown/dagsgräns, så Save
    // navigerar inte förrän save() faktiskt lyckats — till skillnad från ny/redigera-
    // lägena nedan, som aldrig blockeras och kan navigera direkt.
    if (favoritId != null) {
        LaunchedEffect(saveCompleted) { if (saveCompleted > 0) onBack() }
    }

    val onSave: () -> Unit = {
        if (favoritId != null) vm.save() else scope.launch { vm.save(); onBack() }
    }

    val guardedBack = UnsavedChangesBackHandler(
        isDirty   = isDirty,
        canSave   = form.namn.isNotBlank(),
        onSave    = onSave,
        onDiscard = onBack,
    )

    val titleRes = when {
        editId != null    -> R.string.medicin_edit_tagning
        favoritId != null -> R.string.medicin_log_efterhand
        else               -> R.string.medicin_new
    }

    DagbokenScaffold(
        title  = stringResource(titleRes),
        onBack = guardedBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editId != null || favoritId != null) {
                DateTimeRow(
                    datum         = form.datum,
                    tid           = form.tid,
                    onDatumChange = { vm.updateForm { copy(datum = it) } },
                    onTidChange   = { vm.updateForm { copy(tid = it) } },
                )
            }

            if (editId != null && vm.isFromRecept()) {
                Text(form.namn, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.medicin_recept_readonly_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(form.namn, { vm.updateForm { copy(namn = it) } },
                    label = { Text(stringResource(R.string.label_name)) }, modifier = Modifier.fillMaxWidth())
            }

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

            if ((editId != null && vm.isFromRecept()) || favoritId != null) {
                Text(stringResource(R.string.label_time_slot))
                Text(form.tidpunkt, style = MaterialTheme.typography.bodyLarge)
            } else {
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
            }

            if (editId != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_taken), modifier = Modifier.weight(1f))
                    Switch(checked = form.tagen, onCheckedChange = { vm.updateForm { copy(tagen = it) } })
                }
            }

            NoteField(
                text         = form.anteckning,
                onTextChange = { vm.updateForm { copy(anteckning = it) } },
            )

            SaveButton(
                enabled = isDirty && form.namn.isNotBlank(),
                onClick = onSave,
            )
        }
    }

    cooldownWarning?.let { warning ->
        val h = warning.remainingHours.toInt()
        val m = ((warning.remainingHours - h) * 60).toInt()
        AlertDialog(
            onDismissRequest = { vm.dismissCooldownWarning() },
            title = { Text(stringResource(R.string.cooldown_warning_title)) },
            text  = {
                Text(
                    stringResource(
                        R.string.format_cooldown_warning_body,
                        String.format(Locale.ROOT, "%d", h),
                        String.format(Locale.ROOT, "%02d", m),
                        warning.favorit.namn,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.save(force = true) }) {
                    Text(stringResource(R.string.cooldown_take_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissCooldownWarning() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    blockedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.dismissBlockedMessage() },
            title            = { Text(stringResource(R.string.cooldown_warning_title)) },
            text             = { Text(message) },
            confirmButton    = {
                TextButton(onClick = { vm.dismissBlockedMessage() }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}
