package se.partee71.dagboken.ui.aktiviteter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.DateTimeRow
import se.partee71.dagboken.ui.components.DurationRow
import se.partee71.dagboken.ui.components.Foldout
import se.partee71.dagboken.ui.components.GradientSliderRow
import se.partee71.dagboken.ui.components.SymptomLogCard
import se.partee71.dagboken.ui.theme.energyColor
import se.partee71.dagboken.ui.theme.energyLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoggaTab(vm: AktiviteterViewModel, onSaved: () -> Unit = {}) {
    val form by vm.form.collectAsState()
    val aktivitetOptions by vm.aktivitetOptions.collectAsState()
    val symptomOptions by vm.symptomOptions.collectAsState()

    val cs = MaterialTheme.colorScheme
    val eColor = energyColor(form.energy, cs)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.logga_header),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        DateTimeRow(
            datum         = form.datum,
            tid           = form.tid,
            onDatumChange = { vm.updateForm { copy(datum = it) } },
            onTidChange   = { vm.updateForm { copy(tid = it) } },
        )

        AktivitetCard(
            selectedAktivitet  = form.aktivitet,
            aktivitetAnnat     = form.aktivitetAnnat,
            aterhamtande       = form.aterhamtande,
            energitjuv         = form.energitjuv,
            aktivitetOptions   = aktivitetOptions,
            onSelectAktivitet  = { vm.updateForm { copy(aktivitet = it) } },
            onChangeAnnat      = { vm.updateForm { copy(aktivitetAnnat = it) } },
            onToggleAterham    = { vm.updateForm { copy(aterhamtande = !aterhamtande) } },
            onToggleEnergiTjuv = { vm.updateForm { copy(energitjuv = !energitjuv) } },
        )

        DurationRow(
            hours          = form.spentTimeHours,
            minutes        = form.spentTimeMinutes,
            onHoursChange  = { vm.updateForm { copy(spentTimeHours = it) } },
            onMinutesChange = { vm.updateForm { copy(spentTimeMinutes = it) } },
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Foldout(
                title    = stringResource(R.string.label_metrics),
                expanded = form.metricsExpanded,
                onToggle = { vm.updateForm { copy(metricsExpanded = !metricsExpanded) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    GradientSliderRow(
                        label         = stringResource(R.string.label_energy),
                        emoji         = "⚡",
                        value         = form.energy.toFloat(),
                        onValueChange = { vm.updateForm { copy(energy = it.toInt()) } },
                        valueRange    = -10f..10f,
                        steps         = 19,
                        startLabel    = "-10  😴",
                        endLabel      = "+10  ⚡",
                        displayValue  = energyLabel(form.energy),
                        accentColor   = eColor,
                    )
                    HorizontalDivider()
                    GradientSliderRow(
                        label         = stringResource(R.string.label_stress),
                        emoji         = "😰",
                        value         = form.stress.toFloat(),
                        onValueChange = { vm.updateForm { copy(stress = it.toInt()) } },
                        valueRange    = 0f..10f,
                        steps         = 9,
                        startLabel    = "0  😌",
                        endLabel      = "😰  10",
                        reverseColors = true,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        if (symptomOptions.isNotEmpty()) {
            SymptomLogCard(
                symptomOptions   = symptomOptions,
                scores           = form.symptomScores,
                onScoresChange   = { vm.updateForm { copy(symptomScores = it) } },
                onToggleFavorite = vm::toggleSymptomFavorite,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AktivitetCard(
    selectedAktivitet: String,
    aktivitetAnnat: String,
    aterhamtande: Boolean,
    energitjuv: Boolean,
    aktivitetOptions: List<String>,
    onSelectAktivitet: (String) -> Unit,
    onChangeAnnat: (String) -> Unit,
    onToggleAterham: () -> Unit,
    onToggleEnergiTjuv: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.logga_type_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val items = remember(aktivitetOptions) { aktivitetOptions + "Övrigt" }
                items.forEach { opt ->
                    FilterChip(
                        selected = selectedAktivitet == opt,
                        onClick  = { onSelectAktivitet(opt) },
                        label    = { Text(opt) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor     = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            if (selectedAktivitet == "Övrigt") {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value         = aktivitetAnnat,
                    onValueChange = onChangeAnnat,
                    label         = { Text(stringResource(R.string.logga_type_custom_hint)) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
            ) {
                InputChip(
                    selected = aterhamtande,
                    onClick  = onToggleAterham,
                    label    = { Text(stringResource(R.string.tag_aterhamtande)) },
                )
                InputChip(
                    selected = energitjuv,
                    onClick  = onToggleEnergiTjuv,
                    label    = { Text(stringResource(R.string.tag_energitjuv)) },
                )
            }
        }
    }
}
