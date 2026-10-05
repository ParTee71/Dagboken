package se.partee71.dagboken.ui.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import se.partee71.dagboken.BuildConfig
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.DetailLoader
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.EntityDetailScreen
import se.partee71.dagboken.ui.components.Foldout
import se.partee71.dagboken.ui.components.ItemRow
import se.partee71.dagboken.ui.theme.AppTypography

/** Det Om Dagboken visar: versionen och licenstexterna för de bundlade typsnitten (DSN-2). */
data class AboutInfo(val versionName: String, val versionCode: Int, val licenses: String)

/** Om Dagboken: versionen ur `BuildConfig` och licenserna ur `assets/licenses/fonts.txt`. */
@HiltViewModel
class AboutViewModel @Inject constructor(@ApplicationContext context: Context) : ViewModel() {
    private val loader = DetailLoader(
        flow { emit(AboutInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, context.assets.open(LICENSES).bufferedReader().use { it.readText() })) }
            .flowOn(Dispatchers.IO),
        viewModelScope,
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

/** Om Dagboken som detaljskärm med titeln i toppraden: version, licenser (ihopfällda) och integritet. */
@Composable
fun AboutScreen(state: DetailUiState<AboutInfo>, onBack: () -> Unit, onRetry: () -> Unit = {}) {
    var licensesOpen by rememberSaveable { mutableStateOf(false) }
    EntityDetailScreen(
        state = state,
        header = null,
        onBack = onBack,
        title = stringResource(R.string.settings_about),
        onRetry = onRetry,
    ) { about ->
        AppCard {
            ItemRow(stringResource(R.string.about_version), subtitle = stringResource(R.string.about_build_format, about.versionName, about.versionCode))
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
