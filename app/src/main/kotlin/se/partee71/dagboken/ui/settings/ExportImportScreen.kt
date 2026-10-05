package se.partee71.dagboken.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.EntityDetailScreen

/**
 * Export och import (BCK-13, SET-8): raden i inställningsarket finns redan; funktionen byggs i #230
 * (efter grinden OMB-4). Tills dess säger underskärmen att den kommer – samma topprad som de andra
 * underskärmarna och samma tomma tillstånd som flikarna som ännu inte byggts.
 */
@Composable
fun ExportImportScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    EntityDetailScreen(DetailUiState.Content(Unit), header = null, onBack = onBack, modifier = modifier, title = stringResource(R.string.settings_export_import)) {
        EmptyState(R.drawable.ic_download, stringResource(R.string.tab_upcoming_title), stringResource(R.string.export_import_upcoming))
    }
}
