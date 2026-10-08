package se.partee71.dagboken.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.components.AppBottomSheet
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppDivider
import se.partee71.dagboken.ui.components.EntityDetailScreen
import se.partee71.dagboken.ui.components.MessageSnackbar
import se.partee71.dagboken.ui.components.NoticeBanner
import se.partee71.dagboken.ui.migration.ChoiceRow
import se.partee71.dagboken.ui.migration.ImportChoiceRows
import se.partee71.dagboken.ui.migration.ImportEvent
import se.partee71.dagboken.ui.migration.ImportStage
import se.partee71.dagboken.ui.migration.ImportStageContent
import se.partee71.dagboken.ui.migration.JSON_MIME
import se.partee71.dagboken.ui.migration.countText
import se.partee71.dagboken.ui.migration.rememberImportLaunchers
import se.partee71.dagboken.ui.theme.Tone

/** Export och import med sin ViewModel (SET-8, NAV-9). */
@Composable
fun ExportImportRoute(onBack: () -> Unit, viewModel: ExportImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExportImportScreen(state, viewModel::onEvent, viewModel::exportFileName, onBack)
}

/**
 * Export och import (BCK-6, BCK-13, BCK-14, SET-8; mockupen avsnitt 16): underskärm med liten topprad och ett kort med
 * "Spara som JSON" (dokumentväljaren `CreateDocument` med [exportFileName], sedan snackbaren "Sparade N poster") och
 * "Importera backup" (arket med Google Drive och fil), plus notisen att Health Connect-data aldrig ingår. När en import
 * pågår visas dess läge i stället – samma vy som i första starten ([ImportStageContent]). Medan något sparas eller
 * importeras går inget annat att starta.
 */
@Composable
fun ExportImportScreen(
    state: ExportImportUiState,
    onEvent: (ExportImportEvent) -> Unit,
    exportFileName: () -> String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val import = { event: ImportEvent -> onEvent(ExportImportEvent.Import(event)) }
    val pickFile = rememberImportLaunchers(state.import, import)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
        uri?.let { onEvent(ExportImportEvent.ExportChosen(it)) }
    }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val saved = state.exported?.let { pluralStringResource(R.plurals.export_saved, it, countText(it)) }
    MessageSnackbar(saved, snackbar, key = state.exported) { onEvent(ExportImportEvent.MessageShown) }
    EntityDetailScreen(
        state = DetailUiState.Content(state),
        header = null,
        onBack = onBack,
        modifier = modifier,
        title = stringResource(R.string.settings_export_import),
        failure = state.failure,
        onErrorShown = { onEvent(ExportImportEvent.MessageShown) },
        snackbar = snackbar,
    ) { current ->
        if (current.import == ImportStage.Choose) {
            AppCard {
                ChoiceRow(
                    R.string.export_title,
                    if (current.exporting) R.string.export_saving else R.string.export_subtitle,
                    R.drawable.ic_download,
                    { export.launch(exportFileName()) },
                    enabled = !current.busy,
                )
                AppDivider()
                ChoiceRow(R.string.import_title, R.string.import_note, R.drawable.ic_file, { choosing = true }, enabled = !current.busy)
            }
            NoticeBanner(stringResource(R.string.export_note), R.drawable.ic_info, onClick = null, tone = Tone.Neutral)
        } else {
            ImportStageContent(current.import, import, pickFile, stringResource(R.string.import_done), { import(ImportEvent.Reset) })
        }
    }
    if (choosing) {
        AppBottomSheet(stringResource(R.string.import_title), onDismiss = { choosing = false }) {
            ImportChoiceRows(
                onDrive = {
                    choosing = false
                    import(ImportEvent.FromDrive)
                },
                onFile = {
                    choosing = false
                    pickFile()
                },
            )
        }
    }
}
