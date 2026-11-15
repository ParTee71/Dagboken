package se.kodhast.dagboken.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.LocalDate
import se.kodhast.dagboken.BuildConfig
import se.kodhast.dagboken.R
import se.kodhast.dagboken.data.medicines.MedicineRepository
import se.kodhast.dagboken.di.IoDispatcher
import se.kodhast.dagboken.ui.common.AppErrors
import se.kodhast.dagboken.ui.common.DateFormat
import se.kodhast.dagboken.ui.components.Foldout
import se.kodhast.dagboken.ui.theme.AppTypography
import se.kodhast.ui.common.DetailLoader
import se.kodhast.ui.common.DetailUiState
import se.kodhast.ui.components.AppCard
import se.kodhast.ui.components.EntityDetailScreen
import se.kodhast.ui.components.ItemRow
import se.kodhast.ui.components.MakerMark
import se.kodhast.ui.theme.Spacing

/** Det Om Dagboken visar: versionen, licenstexterna för de bundlade typsnitten (DSN-2) och läkemedelslistans datum (REC-14). */
data class AboutInfo(val versionName: String, val versionCode: Int, val licenses: String, val medicinesUpdated: LocalDate? = null)

/** Om Dagboken: versionen ur `BuildConfig` och licenserna ur `assets/licenses/fonts.txt`. */
@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext context: Context,
    medicines: MedicineRepository,
    @param:IoDispatcher io: CoroutineDispatcher
) : ViewModel() {
    private val loader = DetailLoader(
        flow {
            val licenses = context.assets.open(LICENSES).bufferedReader().use { it.readText() }
            emit(AboutInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, licenses, medicines.updated()))
        }
            .flowOn(io),
        viewModelScope,
        AppErrors
    )
    val state: StateFlow<DetailUiState<AboutInfo>> = loader.state

    fun retry() = loader.retry()

    private companion object {
        const val LICENSES = "licenses/fonts.txt"
    }
}

@Composable
fun AboutRoute(onBack: () -> Unit, viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AboutScreen(state, onBack, viewModel::retry)
}

/**
 * Om Dagboken som detaljskärm med titeln i toppraden: version med Kodhästs signatur under (NAV-9), licenser
 * (ihopfällda) och integritet.
 */
@Composable
fun AboutScreen(state: DetailUiState<AboutInfo>, onBack: () -> Unit, onRetry: () -> Unit = {}) {
    var licensesOpen by rememberSaveable { mutableStateOf(false) }
    EntityDetailScreen(
        state = state,
        header = null,
        onBack = onBack,
        title = stringResource(R.string.settings_about),
        onRetry = onRetry
    ) { about ->
        AppCard {
            ItemRow(stringResource(R.string.about_version), subtitle = stringResource(R.string.about_build_format, about.versionName, about.versionCode))
            MakerMark(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))
        }
        about.medicinesUpdated?.let { updated ->
            AppCard {
                ItemRow(stringResource(R.string.settings_medicines), subtitle = stringResource(R.string.settings_medicines_note, DateFormat.display(updated)))
            }
        }
        AppCard {
            Foldout(stringResource(R.string.about_licenses), licensesOpen, { licensesOpen = !licensesOpen }, summary = stringResource(R.string.about_fonts)) {
                Text(about.licenses, style = AppTypography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AppCard {
            ItemRow(stringResource(R.string.about_privacy), subtitle = stringResource(R.string.about_privacy_text))
        }
    }
}
