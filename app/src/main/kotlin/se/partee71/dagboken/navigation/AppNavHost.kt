package se.partee71.dagboken.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.AppFloatingToolbar
import se.partee71.dagboken.ui.components.AppSnackbarHost
import se.partee71.dagboken.ui.components.ErrorSnackbar
import se.partee71.dagboken.ui.components.FloatingToolbarClearance
import se.partee71.dagboken.ui.components.LocalBottomClearance
import se.partee71.dagboken.ui.components.LocalSyncIndicator
import se.partee71.dagboken.ui.components.SyncIndicator
import se.partee71.dagboken.ui.components.ToolbarAction
import se.partee71.dagboken.ui.components.ToolbarItem
import se.partee71.dagboken.ui.sync.SyncEvent
import se.partee71.dagboken.ui.sync.SyncUiState
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Navigationsramen: `NavDisplay` med [Transitions] och bottenraden – de fyra flikarna och
 * plusknappen (NAV-8, NAV-10) – som bara syns i en fliks rot (NAV-3). Skärmarna kommer från
 * [entryProvider] (`appEntries` i appen). [onLog] är plusknappen; utan den visas ingen.
 * [sync] ger synkindikatorn i varje skärms toppbar och meddelandet om en nekad skrivning
 * (NFR-1), i appens gemensamma meddelandeyta ovanför bottenraden.
 */
@Composable
fun AppNavHost(
    backStack: AppBackStack,
    entryProvider: (AppKey) -> NavEntry<AppKey>,
    modifier: Modifier = Modifier,
    onExit: () -> Unit = {},
    sync: SyncUiState = SyncUiState(),
    onSyncEvent: (SyncEvent) -> Unit = {},
    onLog: (() -> Unit)? = null,
) {
    val toolbar = toolbarItems()
    val logLabel = stringResource(R.string.log_menu_open)
    val action = onLog?.let { ToolbarAction(logLabel, R.drawable.ic_add, it) }
    val clearance = if (backStack.atTopLevel) FloatingToolbarClearance else 0.dp
    val messages = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val indicator = remember(sync.pending, resources) {
        SyncIndicator(sync.pending) {
            // Ett meddelande som redan visas (t.ex. en nekad skrivning) får stå kvar; upprepade tryck köar inget.
            if (messages.currentSnackbarData == null) {
                scope.launch { messages.showSnackbar(resources.getString(R.string.sync_pending_explained)) }
            }
        }
    }
    ErrorSnackbar(sync.writeError, messages, R.string.sync_write_rejected, SnackbarDuration.Long) { onSyncEvent(SyncEvent.WriteErrorShown) }
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomClearance provides clearance, LocalSyncIndicator provides indicator) {
            NavDisplay(
                backStack = backStack.entries,
                onBack = { if (!backStack.pop()) onExit() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(rememberTabRootStateHolder()),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = Transitions.forward,
                popTransitionSpec = Transitions.back,
                predictivePopTransitionSpec = Transitions.predictiveBack,
                entryProvider = { key -> entryProvider(key).asTabRootIf(key) },
            )
        }
        AnimatedVisibility(
            visible = backStack.atTopLevel,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.l),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            AppFloatingToolbar(
                items = toolbar,
                selectedIndex = TOP_LEVEL.indexOf(backStack.currentTab),
                onSelect = { backStack.select(TOP_LEVEL[it]) },
                action = action,
            )
        }
        AppSnackbarHost(messages, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = clearance))
    }
}

/**
 * Sparat tillstånd (t.ex. rullning) för flikarnas rötter behålls när fliken lämnar back stacken
 * vid flikbyte (NAV-11); undersidornas tillstånd släpps som vanligt när de stängs.
 */
@Composable
private fun rememberTabRootStateHolder(): SaveableStateHolder {
    val holder = rememberSaveableStateHolder()
    return remember(holder) { TabRootStateHolder(holder) }
}

private class TabRootStateHolder(private val holder: SaveableStateHolder) : SaveableStateHolder {
    @Composable
    override fun SaveableStateProvider(key: Any, content: @Composable () -> Unit) = holder.SaveableStateProvider(key, content)

    override fun removeState(key: Any) {
        if (!(key is String && key.startsWith(TAB_ROOT))) holder.removeState(key)
    }
}

/** Prefix för flikrötternas innehållsnycklar (en sträng, eftersom nyckeln sparas i en Bundle). */
private const val TAB_ROOT = "flikrot:"

private fun NavEntry<AppKey>.asTabRootIf(key: AppKey): NavEntry<AppKey> =
    if (key is TopLevelKey) NavEntry(key, contentKey = TAB_ROOT + key, metadata = metadata) { Content() } else this

@Composable
private fun toolbarItems(): List<ToolbarItem> = listOf(
    ToolbarItem(stringResource(R.string.tab_today), R.drawable.ic_sun),
    ToolbarItem(stringResource(R.string.tab_diary), R.drawable.ic_book),
    ToolbarItem(stringResource(R.string.tab_trends), R.drawable.ic_trend),
    ToolbarItem(stringResource(R.string.tab_medicines), R.drawable.ic_pill),
)
