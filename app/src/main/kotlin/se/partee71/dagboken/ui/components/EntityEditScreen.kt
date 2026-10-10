@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.dagboken.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.toMessage
import se.partee71.dagboken.ui.theme.Spacing

/** Det som händer efter "Släng ändringar": lämna formuläret, arkivera eller återställa. */
private enum class AfterDiscard { Close, Archive, Restore }

/** Radering i redigeringens meny – alltid bekräftad med [ConfirmDialog] (destructive). */
data class DeleteAction(val title: String, val message: String, val onConfirm: () -> Unit)

/**
 * Den enda redigeringsskärmen (NFR-10, skill shared-ui-components), tillsammans med
 * `EditorState`:
 * - tangentbordets inset krymper den scrollande ytan (`imePadding` före `verticalScroll`, Scaffoldens inset
 *   förbrukad så att navigeringsfältet inte räknas två gånger), så att det fokuserade fältet scrollas fram ovanför
 *   tangentbordet (NFR-11);
 * - "Spara" ([saveLabel]) aktiv först när formuläret är giltigt och ändrat;
 * - bakåt med osparade ändringar → "Släng ändringar?" – likaså arkivera/återställ, som stänger
 *   formuläret utan att spara fälten;
 * - sparfel → snackbar via `DataError.toMessage()`; lyckad sparning → [onClose];
 * - arkivera/återställ/radera i menyn när [onArchive]/[onRestore]/[delete] finns;
 * - läsfel → feltillstånd med "Försök igen" ([onRetry]);
 * - [formError] överst i formuläret: ett fel som inte hör till ett synligt fält (t.ex. ett värde som rules nekar).
 *
 * @param onSaved efter lyckad sparning (och arkivera/radera); standard [onClose]. Ett nytt objekt
 *   kan öppna sin detaljskärm i stället för att gå tillbaka.
 * @param onClose lämnar skärmen: `backStack.popIfTop(key)`, aldrig `pop()` – den kan anropas mer
 *   än en gång ("klar" efter "släng ändringar", dubbeltryck) och får då inte stänga skärmen under.
 */
@Composable
fun EntityEditScreen(
    title: String,
    state: EditorUiState<*>,
    effects: Flow<EditorEffect>,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    saveLabel: String = stringResource(R.string.save),
    onArchive: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    delete: DeleteAction? = null,
    onRetry: () -> Unit = {},
    onSaved: () -> Unit = onClose,
    formError: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    // Vad "Släng ändringar" leder till; null = ingen fråga visas.
    var discardThen by rememberSaveable { mutableStateOf<AfterDiscard?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    val saved by rememberUpdatedState(onSaved)
    val resources = LocalResources.current
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                EditorEffect.Done -> saved()
                // Egen coroutine: en visad snackbar får inte hålla tillbaka nästa händelse.
                is EditorEffect.Failed -> launch { snackbar.showSnackbar(resources.getString(effect.failure.message)) }
            }
        }
    }
    val archive by rememberUpdatedState(onArchive)
    val restore by rememberUpdatedState(onRestore)
    val proceed: (AfterDiscard) -> Unit = { after ->
        when (after) {
            AfterDiscard.Close -> close()
            AfterDiscard.Archive -> archive?.invoke()
            AfterDiscard.Restore -> restore?.invoke()
        }
    }
    val askFirst: (AfterDiscard) -> Unit = { after -> if (state.isDirty) discardThen = after else proceed(after) }
    val back: () -> Unit = { askFirst(AfterDiscard.Close) }
    BackHandler(enabled = state.isDirty) { discardThen = AfterDiscard.Close }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title, size = TopBarSize.Small, onBack = back) {
                AppButton(saveLabel, onSave, Modifier.padding(horizontal = Spacing.xs), enabled = state.canSave, loading = state.saving, compact = true)
                val menu = listOfNotNull(
                    onArchive?.let { AppMenuItem(stringResource(R.string.archive), { askFirst(AfterDiscard.Archive) }, R.drawable.ic_archive) },
                    onRestore?.let { AppMenuItem(stringResource(R.string.restore), { askFirst(AfterDiscard.Restore) }, R.drawable.ic_unarchive) },
                    delete?.let { AppMenuItem(stringResource(R.string.delete), { confirmDelete = true }, R.drawable.ic_delete, destructive = true) },
                )
                if (menu.isNotEmpty()) AppMenu(menu)
            }
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        // Scaffoldens inset (navigeringsfältet) är redan utfyllnad här – imePadding nedan lägger bara till resten.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val loadError = state.loadError
            if (state.loading) {
                AppLoading()
            } else if (loadError != null) {
                LoadErrorState(stringResource(R.string.load_error_title), loadError, onRetry)
            } else {
                Column(
                    Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                        .padding(horizontal = SCREEN_MARGIN, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
                ) {
                    formError?.let { FieldError(it) }
                    content()
                }
            }
        }
    }

    discardThen?.let { after ->
        DiscardChangesDialog(
            onConfirm = {
                discardThen = null
                proceed(after)
            },
            onDismiss = { discardThen = null },
        )
    }
    if (confirmDelete && delete != null) {
        DeleteConfirmDialog(delete) { confirmDelete = false }
    }
}
