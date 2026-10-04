package se.partee71.dagboken.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import se.partee71.dagboken.BuildConfig
import se.partee71.dagboken.ui.TabPlaceholderScreen
import se.partee71.dagboken.ui.components.AccountSheet
import se.partee71.dagboken.ui.components.ComponentGallery
import se.partee71.dagboken.ui.components.LogMenuSheet
import se.partee71.dagboken.ui.sync.SyncViewModel

/** Arken som ligger över flikarna: inställningsarket bakom avataren (NAV-9) och loggmenyn bakom plusknappen (NAV-10). */
enum class RootSheet { Account, Log }

/**
 * Appens innehåll efter inloggning: fyra flikar med egna back stackar (NAV-8, NAV-11), synkläget
 * (NFR-1), inställningsarket bakom avataren och loggmenyn bakom plusknappen. [onSignOut] loggar ut.
 */
@Composable
fun AppNavigation(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    syncViewModel: SyncViewModel = hiltViewModel(),
) {
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    val backStack = rememberAppBackStack()
    val activity = LocalActivity.current
    var sheet by rememberSaveable { mutableStateOf<RootSheet?>(null) }
    val entries = remember(backStack) { appEntries(backStack, onAccount = { sheet = RootSheet.Account }) }
    AppNavHost(
        backStack,
        entries,
        modifier,
        onExit = { activity?.finish() },
        sync = sync,
        onSyncEvent = syncViewModel::onEvent,
        onLog = { sheet = RootSheet.Log },
    )
    RootSheets(
        sheet,
        onDismiss = { sheet = null },
        onSignOut = onSignOut,
        onOpenGallery = if (BuildConfig.DEBUG) ({ backStack.push(ComponentGalleryKey) }) else null,
    )
}

/** Det ark som är öppet; varje val stänger arket först. */
@Composable
fun RootSheets(sheet: RootSheet?, onDismiss: () -> Unit, onSignOut: () -> Unit, onOpenGallery: (() -> Unit)?) {
    when (sheet) {
        RootSheet.Account -> AccountSheet(
            onDismiss = onDismiss,
            onSignOut = {
                onDismiss()
                onSignOut()
            },
            onOpenGallery = onOpenGallery?.let { open ->
                {
                    onDismiss()
                    open()
                }
            },
        )
        // Loggformulären kommer i etapp 5; tills dess stänger varje val bara arket.
        RootSheet.Log -> LogMenuSheet(onDismiss = onDismiss, onPick = { onDismiss() })
        null -> Unit
    }
}

/**
 * Vilken skärm varje nyckel visar – varje nyckel har en (`AppNavigationTest`). Flikarna visar en
 * platshållare tills de byggs i etapp 5; [onAccount] är avataren uppe till höger (NAV-9). En
 * skärm stänger sig med `popIfTop(key)`, aldrig `pop()`, så att ett andra tryck inte stänger
 * skärmen under.
 */
fun appEntries(backStack: AppBackStack, onAccount: () -> Unit): (AppKey) -> NavEntry<AppKey> =
    // Varje nyckel har sin skärm (AppKey är förseglad); en saknad är ett programfel.
    entryProvider(fallback = { key -> error("Ingen skärm för $key") }) {
        entry<TodayKey> { TabPlaceholderScreen(TodayKey, onAccount) }
        entry<DiaryKey> { TabPlaceholderScreen(DiaryKey, onAccount) }
        entry<TrendsKey> { TabPlaceholderScreen(TrendsKey, onAccount) }
        entry<MedicinesKey> { TabPlaceholderScreen(MedicinesKey, onAccount) }
        entry<ComponentGalleryKey> { key -> ComponentGallery(onBack = { backStack.popIfTop(key) }) }
    }
