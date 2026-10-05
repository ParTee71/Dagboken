package se.partee71.dagboken.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.UpcomingScreen

/**
 * Export och import (BCK-13, SET-8): raden i inställningsarket finns redan; funktionen byggs i #230
 * (efter grinden OMB-4). Tills dess säger underskärmen att den kommer – samma lilla topprad som de andra
 * underskärmarna och samma centrerade tomma tillstånd som flikarna som ännu inte byggts ([UpcomingScreen]).
 */
@Composable
fun ExportImportScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    UpcomingScreen(
        stringResource(R.string.settings_export_import),
        R.drawable.ic_download,
        stringResource(R.string.export_import_upcoming),
        modifier,
        onBack = onBack,
    )
}
