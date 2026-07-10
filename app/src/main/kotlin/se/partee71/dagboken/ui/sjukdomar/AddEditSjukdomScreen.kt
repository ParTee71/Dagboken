package se.partee71.dagboken.ui.sjukdomar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
fun AddEditSjukdomScreen(
    editId: String?,
    onBack: () -> Unit,
    vm: AddEditSjukdomViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { editId?.let { vm.loadForEdit(it) } }

    val form           by vm.form.collectAsStateWithLifecycle()
    val symptomOptions by vm.symptomOptions.collectAsStateWithLifecycle()
    val isDirty        by vm.isDirty.collectAsStateWithLifecycle()

    val guardedBack = UnsavedChangesBackHandler(
        isDirty   = isDirty,
        canSave   = form.typ.isNotBlank(),
        onSave    = { vm.save { onBack() } },
        onDiscard = { vm.resetForm(); onBack() },
    )

    DagbokenScaffold(
        title  = stringResource(if (editId == null) R.string.sjukdom_add_title else R.string.sjukdom_edit_title),
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
            OutlinedTextField(
                value         = form.typ,
                onValueChange = { vm.updateForm { copy(typ = it) } },
                label         = { Text(stringResource(R.string.sjukdom_label_typ)) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
            )

            DateTimeRow(
                datum         = form.startDatum,
                tid           = "00:00",
                onDatumChange = { vm.updateForm { copy(startDatum = it) } },
                onTidChange   = {},
            )

            if (editId == null) {
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
                    symptomOptions   = symptomOptions,
                    scores           = form.symptomScores,
                    onScoresChange   = { vm.updateForm { copy(symptomScores = it) } },
                    onToggleFavorite = { vm.toggleSymptomFavorite(it) },
                )
            }

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
