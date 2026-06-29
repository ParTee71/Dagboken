package se.partee71.dagboken.ui.mediciner

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.TIDP_ORDER
import se.partee71.dagboken.domain.model.tidpunktSortIndex
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IdagTab(
    vm: MedicinerViewModel,
    onEdit: (String) -> Unit,
) {
    val today                by vm.todayMediciner.collectAsState()
    val noteDialogMedicin    by vm.noteDialogMedicin.collectAsState()
    val noteDialogText       by vm.noteDialogText.collectAsState()
    val showSingleDoseDialog by vm.showSingleDoseDialog.collectAsState()
    val allFavoriter         by vm.allFavoriter.collectAsState()
    val cooldownWarning      by vm.cooldownWarning.collectAsState()
    val sorted = today.sortedBy { tidpunktSortIndex(it.tidpunkt) }
    var deleteTarget by remember { mutableStateOf<Medicin?>(null) }
    var showTaken    by remember { mutableStateOf(false) }

    if (sorted.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.empty_idag_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.empty_idag_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { vm.openSingleDoseDialog() }) {
                    Text(stringResource(R.string.log_single_dose))
                }
            }
        }
    } else {
        val tagenCount = sorted.count { it.tagen }
        val total      = sorted.size
        val progress   = tagenCount.toFloat() / total.toFloat()
        val visible    = sorted.filter { !it.tagen || showTaken }
        val grouped    = visible.groupBy { it.tidpunkt }
        val orderedGroups = TIDP_ORDER
            .filter { grouped.containsKey(it) }
            .map { it to grouped.getValue(it) }

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.format_adherence, tagenCount, total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (allFavoriter.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tab_vid_behov),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    FavoriterRow(
                        favoriter = allFavoriter,
                        onTap     = { vm.quickDos(it) },
                        onEdit    = onEdit,
                        onDelete  = null,
                    )
                }
            }

            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.idag_alla_tagna),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { showTaken = true }) {
                        Text(stringResource(R.string.idag_visa_tagna_count, tagenCount))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    orderedGroups.forEach { (tidpunkt, mediciner) ->
                        stickyHeader(key = "header_$tidpunkt") {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = tidpunkt.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                        }

                        items(mediciner, key = { it.id }) { medicin ->
                            val iconColor by animateColorAsState(
                                targetValue = if (medicin.tagen) MaterialTheme.colorScheme.primary
                                              else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "icon_color",
                            )
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        deleteTarget = medicin
                                    }
                                    false
                                },
                            )

                            LaunchedEffect(deleteTarget) {
                                if (deleteTarget == null) dismissState.reset()
                            }

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val isSwiping =
                                        dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (isSwiping) MaterialTheme.colorScheme.errorContainer
                                                else MaterialTheme.colorScheme.surface,
                                            )
                                            .padding(end = 20.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        if (isSwiping) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                        }
                                    }
                                },
                            ) {
                                val isTagen = medicin.tagen
                                ListItem(
                                    modifier = Modifier.clickable { vm.openNoteDialog(medicin) },
                                    headlineContent = {
                                        Text(
                                            text = medicin.namn,
                                            textDecoration = if (isTagen) TextDecoration.LineThrough else null,
                                            modifier = if (isTagen) Modifier.alpha(0.5f) else Modifier,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = "${medicin.dos} ${medicin.enhet}",
                                            modifier = if (isTagen) Modifier.alpha(0.5f) else Modifier,
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { vm.toggleTagen(medicin) }) {
                                            Icon(
                                                imageVector = if (isTagen) Icons.Filled.CheckCircle
                                                              else Icons.Outlined.RadioButtonUnchecked,
                                                contentDescription = if (isTagen)
                                                    stringResource(R.string.format_mark_as_untaken, medicin.namn)
                                                else
                                                    stringResource(R.string.format_mark_as_taken, medicin.namn),
                                                tint = iconColor,
                                            )
                                        }
                                    },
                                    colors = if (isTagen) {
                                        ListItemDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        )
                                    } else {
                                        ListItemDefaults.colors()
                                    },
                                )
                            }
                        }
                    }

                    if (tagenCount > 0) {
                        item("toggle_tagna") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(onClick = { showTaken = !showTaken }) {
                                    Text(
                                        if (showTaken) stringResource(R.string.idag_dolj_tagna)
                                        else stringResource(R.string.idag_visa_tagna_count, tagenCount),
                                    )
                                }
                            }
                        }
                    }

                    item("single_dose_btn") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = { vm.openSingleDoseDialog() }) {
                                Text(stringResource(R.string.log_single_dose))
                            }
                        }
                    }
                }
            }
        }
    }

    noteDialogMedicin?.let { medicin ->
        AlertDialog(
            onDismissRequest = { vm.dismissNoteDialog() },
            title   = { Text(medicin.namn) },
            text    = {
                OutlinedTextField(
                    value         = noteDialogText,
                    onValueChange = vm::updateNoteDialogText,
                    modifier      = Modifier.fillMaxWidth(),
                    minLines      = 3,
                    placeholder   = { Text(stringResource(R.string.note_placeholder)) },
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.saveAndCloseNoteDialog() }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissNoteDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(
                    if (target.receptId != null) stringResource(R.string.idag_skip_dose_title)
                    else stringResource(R.string.idag_delete_one_title),
                )
            },
            text  = {
                Text(
                    if (target.receptId != null)
                        stringResource(R.string.format_idag_skip_body, target.namn)
                    else
                        stringResource(R.string.format_delete_aktivitet_confirm, target.namn),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteMedicin(target); deleteTarget = null }) {
                    Text(
                        if (target.receptId != null) stringResource(R.string.idag_skip_button)
                        else stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
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
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.forceDos(warning.favorit) }) {
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

    if (showSingleDoseDialog) {
        SingleDoseDialog(
            onDismiss = { vm.closeSingleDoseDialog() },
            onConfirm = { namn, dos, enhet, tid -> vm.logSingleDose(namn, dos, enhet, tid) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDoseDialog(
    onDismiss: () -> Unit,
    onConfirm: (namn: String, dos: String, enhet: String, tid: String) -> Unit,
) {
    val currentTime = remember {
        java.time.LocalTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }
    var namn          by remember { mutableStateOf("") }
    var dos           by remember { mutableStateOf("") }
    var enhet         by remember { mutableStateOf("mg") }
    var tid           by remember { mutableStateOf(currentTime) }
    var enhetExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_single_dose)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = namn,
                    onValueChange = { namn = it },
                    label         = { Text(stringResource(R.string.label_name)) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                )
                OutlinedTextField(
                    value          = dos,
                    onValueChange  = { dos = it },
                    label          = { Text(stringResource(R.string.label_dose)) },
                    modifier       = Modifier.fillMaxWidth(),
                    singleLine     = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                ExposedDropdownMenuBox(
                    expanded        = enhetExpanded,
                    onExpandedChange = { enhetExpanded = it },
                ) {
                    OutlinedTextField(
                        value          = enhet,
                        onValueChange  = {},
                        readOnly       = true,
                        label          = { Text(stringResource(R.string.label_unit)) },
                        trailingIcon   = { ExposedDropdownMenuDefaults.TrailingIcon(enhetExpanded) },
                        modifier       = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded        = enhetExpanded,
                        onDismissRequest = { enhetExpanded = false },
                    ) {
                        listOf("mg", "ml", "g", "mcg", "st", "IE", "dropp").forEach { u ->
                            androidx.compose.material3.DropdownMenuItem(
                                text    = { Text(u) },
                                onClick = { enhet = u; enhetExpanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value          = tid,
                    onValueChange  = { tid = it },
                    label          = { Text(stringResource(R.string.handelse_label_tid)) },
                    modifier       = Modifier.fillMaxWidth(),
                    singleLine     = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onConfirm(namn, dos, enhet, tid) },
                enabled  = namn.isNotBlank() && dos.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
