package se.partee71.dagboken.ui.sjukdomar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.DateTimeRow
import se.partee71.dagboken.ui.components.GradientSliderRow
import se.partee71.dagboken.ui.components.NoteField
import se.partee71.dagboken.ui.components.SaveButton
import se.partee71.dagboken.ui.components.SymptomLogCard
import se.partee71.dagboken.ui.components.UnsavedChangesBackHandler

@Composable
fun AddSjukdomsIncheckningScreen(
    onBack: () -> Unit,
    vm: SjukdomsEpisodViewModel = hiltViewModel(),
) {
    val form          by vm.incheckningForm.collectAsStateWithLifecycle()
    val symptomOptions by vm.symptomOptions.collectAsStateWithLifecycle()
    val snackbar      by vm.snackbar.collectAsStateWithLifecycle()
    val isDirty       by vm.isIncheckningFormDirty.collectAsStateWithLifecycle()

    LaunchedEffect(snackbar) {
        snackbar?.let {
            if (it == "Incheckning sparad ✓") onBack()
        }
    }

    val guardedBack = UnsavedChangesBackHandler(
        isDirty   = isDirty,
        onSave    = { vm.saveIncheckning() },
        onDiscard = onBack,
    )

    DagbokenScaffold(
        title  = stringResource(R.string.sjukdom_incheckning_title),
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
            DateTimeRow(
                datum         = form.datum,
                tid           = form.tid,
                onDatumChange = { vm.updateForm { copy(datum = it) } },
                onTidChange   = { vm.updateForm { copy(tid = it) } },
            )

            DagbokenCard {
                GradientSliderRow(
                    label         = stringResource(R.string.sjukdom_label_svarighetsgrad),
                    value         = form.svarighetsgrad.toFloat(),
                    onValueChange = { vm.updateForm { copy(svarighetsgrad = it.toInt()) } },
                    valueRange    = 0f..10f,
                    steps         = 9,
                    startLabel    = "0  Ingen",
                    endLabel      = "10  Extrem",
                    reverseColors = true,
                )
            }

            SymptomLogCard(
                symptomOptions  = symptomOptions,
                scores          = form.symptomScores,
                onScoresChange  = { vm.updateForm { copy(symptomScores = it) } },
                onToggleFavorite = { vm.toggleSymptomFavorite(it) },
            )

            NoteField(
                text         = form.anteckning,
                onTextChange = { vm.updateForm { copy(anteckning = it) } },
            )

            SaveButton(
                enabled = isDirty,
                onClick = { vm.saveIncheckning() },
            )
        }
    }
}
