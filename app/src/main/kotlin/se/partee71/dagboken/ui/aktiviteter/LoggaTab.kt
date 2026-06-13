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
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.components.Foldout
import se.partee71.dagboken.ui.components.SliderRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoggaTab(vm: AktiviteterViewModel) {
    val form by vm.form.collectAsState()
    val aktivitetOptions by vm.aktivitetOptions.collectAsState()
    val symptomOptions by vm.symptomOptions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Activity type
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Aktivitetstyp",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (aktivitetOptions + listOf("Övrigt")).forEach { opt ->
                        FilterChip(
                            selected = form.aktivitet == opt,
                            onClick  = { vm.updateForm { copy(aktivitet = opt) } },
                            label    = { Text(opt) },
                        )
                    }
                }
                if (form.aktivitet == "Övrigt") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = form.aktivitetAnnat,
                        onValueChange = { vm.updateForm { copy(aktivitetAnnat = it) } },
                        label = { Text("Beskriv aktivitet") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                if (aktivitetOptions.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        // Metrics
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Foldout(
                title    = "Mätvärden",
                expanded = form.metricsExpanded,
                onToggle = { vm.updateForm { copy(metricsExpanded = !metricsExpanded) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                SliderRow(
                    label         = "Energi",
                    value         = form.energy.toFloat(),
                    onValueChange = { vm.updateForm { copy(energy = it.toInt()) } },
                    valueRange    = -10f..10f,
                    steps         = 19,
                    valueLabel    = if (form.energy > 0) "+${form.energy}" else "${form.energy}",
                )
                SliderRow(
                    label         = "Stress",
                    value         = form.stress.toFloat(),
                    onValueChange = { vm.updateForm { copy(stress = it.toInt()) } },
                    valueRange    = 0f..10f,
                    steps         = 9,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // Symptoms
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

        Button(
            onClick  = { vm.save { } },
            enabled  = form.aktivitet.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Spara")
        }

        Spacer(Modifier.height(8.dp))
    }
}
