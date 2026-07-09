package se.partee71.dagboken.ui.mediciner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.DagbokenScaffold

@Composable
fun SchemaScreen(
    onBack: () -> Unit,
    onAddRecept: () -> Unit,
    onEditRecept: (String) -> Unit,
    vm: MedicinerViewModel = hiltViewModel(),
) {
    DagbokenScaffold(
        title  = stringResource(R.string.hantera_schema_title),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecept) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fab_new))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            SchemaTab(vm = vm, onEdit = onEditRecept)
        }
    }
}
