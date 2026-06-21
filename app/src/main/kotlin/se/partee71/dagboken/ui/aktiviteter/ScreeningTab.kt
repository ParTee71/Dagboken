package se.partee71.dagboken.ui.aktiviteter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.data.datastore.SCREENING_EVENT_LABELS
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.GradientSliderRow
import se.partee71.dagboken.ui.components.SymptomLogCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreeningTab(vm: AktiviteterViewModel) {
    val form             by vm.form.collectAsState()
    val symptomOptions   by vm.symptomOptions.collectAsState()
    val todaysScreenings by vm.todaysScreenings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text       = stringResource(R.string.screening_header),
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        DagbokenCard(title = stringResource(R.string.screening_event_label)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SCREENING_EVENT_LABELS.forEach { label ->
                    val doneToday = label in todaysScreenings
                    FilterChip(
                        selected = form.aktivitet == label,
                        onClick  = { vm.updateForm { copy(aktivitet = label) } },
                        enabled  = !doneToday,
                        label    = { Text(label) },
                    )
                }
            }
        }

        DagbokenCard(title = stringResource(R.string.screening_metrics_title)) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                GradientSliderRow(
                    label         = stringResource(R.string.label_energy),
                    emoji         = "⚡",
                    value         = form.energy.coerceIn(0, 10).toFloat(),
                    onValueChange = { vm.updateForm { copy(energy = it.toInt()) } },
                    valueRange    = 0f..10f,
                    steps         = 9,
                    startLabel    = "0  😴",
                    endLabel      = "😊  10",
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
