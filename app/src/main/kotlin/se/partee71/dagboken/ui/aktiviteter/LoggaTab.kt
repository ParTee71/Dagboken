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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.components.Foldout
import se.partee71.dagboken.ui.components.GradientSliderRow
import se.partee71.dagboken.ui.theme.energyColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoggaTab(vm: AktiviteterViewModel) {
    val form by vm.form.collectAsState()
    val aktivitetOptions by vm.aktivitetOptions.collectAsState()
    val symptomOptions by vm.symptomOptions.collectAsState()

    val cs = MaterialTheme.colorScheme
    val eColor = energyColor(form.energy, cs)
    val eLabel = if (form.energy > 0) "+${form.energy}" else "${form.energy}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Hur gick det?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Activity type card
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Aktivitetstyp",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (aktivitetOptions + listOf("Övrigt")).forEach { opt ->
                        FilterChip(
                            selected = form.aktivitet == opt,
                            onClick  = { vm.updateForm { copy(aktivitet = opt) } },
                            label    = { Text(opt) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor     = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }

                if (form.aktivitet == "Övrigt") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value         = form.aktivitetAnnat,
                        onValueChange = { vm.updateForm { copy(aktivitetAnnat = it) } },
                        label         = { Text("Beskriv aktivitet") },
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
                        selected = form.aterhamtande,
                        onClick  = { vm.updateForm { copy(aterhamtande = !aterhamtande) } },
                        label    = { Text("Återhämtande") },
                    )
                    InputChip(
                        selected = form.energitjuv,
                        onClick  = { vm.updateForm { copy(energitjuv = !energitjuv) } },
                        label    = { Text("Energitjuv") },
                    )
                }
            }
        }

        // Metrics card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Foldout(
                title    = "Mätvärden",
                expanded = form.metricsExpanded,
                onToggle = { vm.updateForm { copy(metricsExpanded = !metricsExpanded) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    GradientSliderRow(
                        label         = "Energi",
                        emoji         = "⚡",
                        value         = form.energy.toFloat(),
                        onValueChange = { vm.updateForm { copy(energy = it.toInt()) } },
                        valueRange    = -10f..10f,
                        steps         = 19,
                        startLabel    = "-10  😴",
                        endLabel      = "+10  ⚡",
                        displayValue  = eLabel,
                        accentColor   = eColor,
                    )
                    HorizontalDivider()
                    GradientSliderRow(
                        label         = "Stress",
                        emoji         = "😰",
                        value         = form.stress.toFloat(),
                        onValueChange = { vm.updateForm { copy(stress = it.toInt()) } },
                        valueRange    = 0f..10f,
                        steps         = 9,
                        startLabel    = "0  😌",
                        endLabel      = "😰  10",
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // Symptoms card
        if (symptomOptions.isNotEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Foldout(
                    title    = "Symptom",
                    expanded = form.symptomsExpanded,
                    onToggle = { vm.updateForm { copy(symptomsExpanded = !symptomsExpanded) } },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        symptomOptions.forEachIndexed { index, symptom ->
                            if (index > 0) HorizontalDivider()
                            GradientSliderRow(
                                label         = symptom,
                                value         = (form.symptomScores[symptom] ?: 0).toFloat(),
                                onValueChange = { v ->
                                    vm.updateForm {
                                        copy(symptomScores = symptomScores + (symptom to v.toInt()))
                                    }
                                },
                                valueRange = 0f..10f,
                                steps      = 9,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Save button
        FilledTonalButton(
            onClick  = { vm.save { } },
            enabled  = form.aktivitet.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Spara aktivitet")
        }
    }
}
