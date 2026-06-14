package se.partee71.dagboken.ui.aktiviteter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.components.Foldout
import se.partee71.dagboken.ui.components.SliderRow

@Composable
private fun energyDescription(energy: Int): String = when {
    energy >= 8  -> "Toppen! Superenergi"
    energy >= 5  -> "Bra energi"
    energy >= 2  -> "Okej, ganska bra"
    energy >= 0  -> "Neutral"
    energy >= -3 -> "Lite trött"
    energy >= -6 -> "Ganska trött"
    else         -> "Mycket trött"
}

@Composable
private fun energyColor(energy: Int): Color {
    val cs = MaterialTheme.colorScheme
    return when {
        energy >= 5  -> cs.tertiary
        energy >= 1  -> cs.secondary
        energy >= -1 -> cs.onSurfaceVariant
        else         -> cs.error
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoggaTab(vm: AktiviteterViewModel) {
    val form by vm.form.collectAsState()
    val aktivitetOptions by vm.aktivitetOptions.collectAsState()
    val symptomOptions by vm.symptomOptions.collectAsState()

    val eColor = energyColor(form.energy)
    val eLabel = if (form.energy > 0) "+${form.energy}" else "${form.energy}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Friendly heading
        Text(
            text = "Hur gick det?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Activity type card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
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
                        value = form.aktivitetAnnat,
                        onValueChange = { vm.updateForm { copy(aktivitetAnnat = it) } },
                        label = { Text("Beskriv aktivitet") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                SliderRow(
                    label           = "Energi",
                    value           = form.energy.toFloat(),
                    onValueChange   = { vm.updateForm { copy(energy = it.toInt()) } },
                    valueRange      = -10f..10f,
                    steps           = 19,
                    valueLabel      = "$eLabel  ${energyDescription(form.energy)}",
                    valueLabelColor = eColor,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "😴 Trött",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Energisk ⚡",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SliderRow(
                    label         = "Stress",
                    value         = form.stress.toFloat(),
                    onValueChange = { vm.updateForm { copy(stress = it.toInt()) } },
                    valueRange    = 0f..10f,
                    steps         = 9,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "😌 Lugn",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Stressad 😰",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    symptomOptions.forEach { symptom ->
                        SliderRow(
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
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("Spara aktivitet")
        }
    }
}
