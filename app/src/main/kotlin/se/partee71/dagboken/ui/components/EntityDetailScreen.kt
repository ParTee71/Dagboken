@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/** Huvudet överst i en detaljskärm: bild (t.ex. [PersonAvatar]), namn och en rad under. */
data class DetailHeader(val title: String, val subtitle: String? = null)

/**
 * Den enda detaljskärmen (skill shared-ui-components): toppbar med tillbaka, "Redigera" och
 * meny; laddning → [AppLoading]; fel → läsfel med "Försök igen"; innehåll → ett centrerat
 * huvud ([leading], [DetailHeader]) och sektioner i kort ([content]). Ett fel från en åtgärd
 * (t.ex. arkivera i menyn, [error], eller [failure] med egen text) visas som meddelande.
 *
 * En underskärm utan eget huvud (t.ex. Tema och Om i inställningsarket) har [title]: titeln står då i
 * toppraden bredvid tillbakapilen – samma topprad som `EntityEditScreen`, utan Spara – och inget
 * centrerat huvud visas.
 *
 * **Flikläge** ([onBack] = `null`, t.ex. Idag): stor topprad som fälls ihop vid rullning, med [title],
 * [subtitle] och [actions] (avataren, NAV-9), ingen tillbakapil, och plats för verktygsraden under
 * innehållet och meddelandena. [snackbar] är skärmens meddelandeyta – skärmen kan visa egna meddelanden
 * (Ångra, bekräftelser) i samma yta som felen.
 *
 * @param header vad huvudet visar för ett laddat värde; `null` = inget huvud.
 * @param menu valen i "Fler val" (arkivera/återställ m.m.) för ett laddat värde.
 */
@Composable
fun <T> EntityDetailScreen(
    state: DetailUiState<T>,
    header: ((T) -> DetailHeader)?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String? = null,
    onEdit: (() -> Unit)? = null,
    menu: (T) -> List<AppMenuItem> = { emptyList() },
    onRetry: () -> Unit = {},
    error: DataError? = null,
    onErrorShown: () -> Unit = {},
    failure: Failure? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    leading: @Composable (T) -> Unit = {},
    content: @Composable ColumnScope.(T) -> Unit,
) {
    // [failure] (fel + text, varje fel en egen händelse) går före [error].
    ErrorSnackbar(failure?.error ?: error, snackbar, message = failure?.message, key = failure ?: error, onShown = onErrorShown)
    val tab = onBack == null
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clearance = if (tab) LocalBottomClearance.current else 0.dp
    Scaffold(
        modifier = modifier.fillMaxSize().then(if (tab) Modifier.nestedScroll(scroll.nestedScrollConnection) else Modifier),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { AppSnackbarHost(snackbar, Modifier.padding(bottom = clearance)) },
        topBar = {
            AppTopBar(
                title.orEmpty(),
                size = if (tab) TopBarSize.Large else TopBarSize.Small,
                subtitle = subtitle,
                onBack = onBack,
                scrollBehavior = if (tab) scroll else null,
            ) {
                actions()
                if (state is DetailUiState.Content) {
                    onEdit?.let { AppIconButton(R.drawable.ic_edit, stringResource(R.string.edit), it) }
                    val items = menu(state.value)
                    if (items.isNotEmpty()) AppMenu(items)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                DetailUiState.Loading -> AppLoading(Modifier.padding(bottom = clearance))
                is DetailUiState.Error -> LoadErrorState(stringResource(R.string.load_error_title), state.error, onRetry, Modifier.padding(bottom = clearance))
                is DetailUiState.Content -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_MARGIN, vertical = Spacing.s).padding(bottom = clearance),
                    verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
                ) {
                    header?.let { DetailTop(it(state.value)) { leading(state.value) } }
                    content(state.value)
                }
            }
        }
    }
}

/** Det centrerade huvudet: bild, namn och en rad under. */
@Composable
private fun DetailTop(top: DetailHeader, leading: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        leading()
        Text(top.title, style = AppTypography.screenTitle, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        top.subtitle?.let {
            Text(it, style = AppTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
