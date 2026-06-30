package se.partee71.dagboken.ui.sjukdomar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.DateTimeRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSjukdomScreen(
    editId: String?,
    onBack: () -> Unit,
    vm: AddEditSjukdomViewModel = hiltViewModel(),
) {
    LaunchedEffect(editId) { editId?.let { vm.loadForEdit(it) } }

    val form by vm.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (editId == null) R.string.sjukdom_add_title else R.string.sjukdom_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = { vm.resetForm(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
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

            OutlinedTextField(
                value         = form.anteckning,
                onValueChange = { vm.updateForm { copy(anteckning = it) } },
                label         = { Text(stringResource(R.string.label_note)) },
                modifier      = Modifier.fillMaxWidth(),
                minLines      = 3,
                maxLines      = 6,
            )

            FilledTonalButton(
                onClick  = { vm.save { onBack() } },
                enabled  = form.typ.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.save))
            }
        }
    }
}
