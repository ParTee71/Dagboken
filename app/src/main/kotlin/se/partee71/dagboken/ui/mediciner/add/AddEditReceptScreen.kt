package se.partee71.dagboken.ui.mediciner.add

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Dosperiod
import se.partee71.dagboken.domain.model.TIDP_ORDER
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.DatePickerModal
import se.partee71.dagboken.ui.components.NoteField
import se.partee71.dagboken.ui.components.SaveButton
import se.partee71.dagboken.ui.components.SectionHeader
import se.partee71.dagboken.ui.components.UnsavedChangesBackHandler
import se.partee71.dagboken.ui.formatDisplayDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val UPPREPNING_OPTIONS = listOf("dagligen", "vardagar", "helger", "anpassad", "intervall")
private val UPPREPNING_LABELS = mapOf(
    "dagligen"  to "Dagligen",
    "vardagar"  to "Vardagar",
    "helger"    to "Helger",
    "anpassad"  to "Specifika dagar",
    "intervall" to "Var X:e dag",
)
private val DAG_LABELS = listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön")
private val ENHET_OPTIONS = listOf("mg", "ml", "st", "g", "mcg", "IE", "dropp")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditReceptScreen(
    editId: String?,
    onBack: () -> Unit,
    vm: AddEditReceptViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { editId?.let { vm.loadForEdit(it) } }

    // Posten kan ha raderats någon annanstans medan skärmen öppnades. Utan detta stod
    // formuläret tomt och nästa "Spara" skapade ett nytt recept i stället för att
    // redigera det som skulle öppnas.
    val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
    LaunchedEffect(loadFailed) { if (loadFailed) onBack() }

    val form by vm.form.collectAsStateWithLifecycle()
    val isDirty by vm.isDirty.collectAsStateWithLifecycle()
    val validationError by vm.validationError.collectAsStateWithLifecycle()

    val canSave = form.namn.isNotBlank() && form.dos.isNotBlank() && validationError == null

    val guardedBack = UnsavedChangesBackHandler(
        isDirty   = isDirty,
        canSave   = canSave,
        onSave    = { vm.save(onDone = onBack) },
        onDiscard = onBack,
    )

    DagbokenScaffold(
        title  = stringResource(if (editId == null) R.string.recept_new else R.string.recept_edit),
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
            OutlinedTextField(form.namn, { vm.updateForm { copy(namn = it) } },
                label = { Text(stringResource(R.string.label_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.dos, { vm.updateForm { copy(dos = it) } },
                label = { Text(stringResource(R.string.label_dose)) }, modifier = Modifier.fillMaxWidth())

            EnhetDropdown(
                enhet    = form.enhet,
                onSelect = { u -> vm.updateForm { copy(enhet = u) } },
            )

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

            PeriodSection(form = form, vm = vm)

            DosperiodSection(form = form, vm = vm)

            validationError?.let { error ->
                Text(
                    text  = stringResource(error.messageRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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

            SaveButton(
                enabled = isDirty && canSave,
                onClick = { vm.save(onDone = onBack) },
            )
        }
    }
}

@StringRes
private fun ReceptFormError.messageRes(): Int = when (this) {
    ReceptFormError.SLUT_FORE_START            -> R.string.error_period_end_before_start
    ReceptFormError.DOSPERIOD_SLUT_FORE_START  -> R.string.error_dosperiod_end_before_start
    ReceptFormError.DOSPERIOD_OVERLAPP         -> R.string.error_dosperiod_overlap
    ReceptFormError.DOSPERIOD_UTAN_DOS         -> R.string.error_dosperiod_missing_dose
}

/** Receptets period (REC-7) — startdatum plus tills vidare, längd eller t.o.m.-datum. */
@Composable
private fun PeriodSection(form: ReceptForm, vm: AddEditReceptViewModel) {
    DagbokenCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.label_period))

            DateField(
                label   = stringResource(R.string.label_start_date),
                datum   = form.startDatum,
                onPick  = { vm.updateForm { copy(startDatum = it) } },
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(
                    PeriodMode.TILLS_VIDARE to R.string.period_mode_until_further,
                    PeriodMode.LANGD        to R.string.period_mode_length,
                    PeriodMode.SLUTDATUM    to R.string.period_mode_end_date,
                )
                modes.forEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        selected = form.periodMode == mode,
                        onClick  = { vm.setPeriodMode(mode) },
                        shape    = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label    = {
                            Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                    )
                }
            }

            when (form.periodMode) {
                PeriodMode.TILLS_VIDARE -> Text(
                    stringResource(R.string.period_until_further_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PeriodMode.LANGD -> {
                    OutlinedTextField(
                        value = form.langdDagar.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { d -> vm.updateForm { copy(langdDagar = d.coerceAtLeast(1)) } }
                        },
                        label = { Text(stringResource(R.string.label_period_length_days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PeriodSummary(form)
                }

                PeriodMode.SLUTDATUM -> {
                    DateField(
                        label  = stringResource(R.string.label_end_date),
                        datum  = form.slutDatumVal,
                        onPick = { vm.updateForm { copy(slutDatumVal = it) } },
                    )
                    PeriodSummary(form)
                }
            }
        }
    }
}

@Composable
private fun PeriodSummary(form: ReceptForm) {
    val slut = form.resolvedSlutDatum() ?: return
    val dagar = runCatching {
        ChronoUnit.DAYS.between(
            LocalDate.parse(form.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
            LocalDate.parse(slut, DateTimeFormatter.ISO_LOCAL_DATE),
        ) + 1
    }.getOrNull() ?: return
    if (dagar < 1) return
    Text(
        stringResource(
            R.string.format_period_summary,
            runCatching { formatDisplayDate(slut) }.getOrDefault(slut),
            dagar.toInt(),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Tillfälliga dosändringar inom perioden (REC-9). */
@Composable
private fun DosperiodSection(form: ReceptForm, vm: AddEditReceptViewModel) {
    DagbokenCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.label_dose_changes))
            Text(
                stringResource(R.string.dose_changes_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            form.dosperioder.forEach { dosperiod ->
                DosperiodRow(
                    dosperiod = dosperiod,
                    onUpdate  = { update -> vm.updateDosperiod(dosperiod.id) { update() } },
                    onRemove  = { vm.removeDosperiod(dosperiod.id) },
                )
            }

            TextButton(onClick = { vm.addDosperiod() }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.dose_change_add))
            }
        }
    }
}

@Composable
private fun DosperiodRow(
    dosperiod: Dosperiod,
    onUpdate: (Dosperiod.() -> Dosperiod) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value         = dosperiod.dos,
                onValueChange = { v -> onUpdate { copy(dos = v) } },
                label         = { Text(stringResource(R.string.label_dose)) },
                singleLine    = true,
                modifier      = Modifier.weight(1f),
            )
            EnhetDropdown(
                enhet    = dosperiod.enhet,
                onSelect = { u -> onUpdate { copy(enhet = u) } },
                modifier = Modifier.width(120.dp),
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.dose_change_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.fillMaxWidth(),
        ) {
            DateField(
                label    = stringResource(R.string.label_start_date),
                datum    = dosperiod.startDatum,
                onPick   = { d -> onUpdate { copy(startDatum = d) } },
                modifier = Modifier.weight(1f),
            )
            DateField(
                label       = stringResource(R.string.label_end_date),
                datum       = dosperiod.slutDatum,
                onPick      = { d -> onUpdate { copy(slutDatum = d) } },
                emptyLabel  = stringResource(R.string.dose_change_until_period_end),
                modifier    = Modifier.weight(1f),
            )
        }
    }
}

/** Knapp som öppnar den delade [DatePickerModal]. */
@Composable
private fun DateField(
    label: String,
    datum: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyLabel: String? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val visning = datum?.takeIf { it.isNotBlank() }
        ?.let { runCatching { formatDisplayDate(it) }.getOrDefault(it) }
        ?: emptyLabel.orEmpty()

    OutlinedButton(
        onClick  = { showPicker = true },
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label: $visning" },
    ) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f).clearAndSetSemantics { }) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                visning,
                style    = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (showPicker) {
        DatePickerModal(
            initialDatum = datum?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString(),
            onConfirm    = { onPick(it); showPicker = false },
            onDismiss    = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhetDropdown(
    enhet: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
        modifier         = modifier,
    ) {
        OutlinedTextField(
            value = enhet, onValueChange = {}, readOnly = true,
            label = { Text(stringResource(R.string.label_unit)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            ENHET_OPTIONS.forEach { u ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(u) },
                    onClick = { onSelect(u); expanded = false },
                )
            }
        }
    }
}
