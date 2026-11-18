package se.kodhast.dagboken.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import se.kodhast.dagboken.R
import se.kodhast.dagboken.ui.components.LogChoice
import se.kodhast.dagboken.ui.sync.SyncEvent
import se.kodhast.ui.common.SyncUiState
import se.kodhast.ui.components.AppMenuItem
import se.kodhast.ui.components.ToolbarAction
import se.kodhast.ui.components.ToolbarItem
import se.kodhast.ui.navigation.AppNavHost as LibraryNavHost

/**
 * Dagbokens navigationsram: android-ui:s `AppNavHost` (ui-navigation) med de fyra flikarna och plusknappen (NAV-8,
 * NAV-10) – bottenraden syns bara i en fliks rot (NAV-3) – och skärmbytena ur `AppTransitions`. Skärmarna kommer från
 * [entryProvider] (`appEntries` i appen). [onLog] får plusknappens val (loggmenyn, NAV-10); utan den visas ingen
 * plusknapp. [sync] ger synkläget – prickarna i flikarnas sidhuvud hoppar medan ändringar väntar – och meddelandet om en
 * nekad skrivning (NFR-22), i appens gemensamma meddelandeyta ovanför bottenraden. [account] är kontot längst till
 * höger i flikarnas sidhuvud (NAV-9, NAV-12). [onPopped] får skärmen som systemets tillbaka stängde; i Idags rot
 * stänger systemet appen.
 */
@Composable
fun AppNavHost(
    backStack: AppBackStack,
    entryProvider: (AppKey) -> NavEntry<AppKey>,
    modifier: Modifier = Modifier,
    sync: SyncUiState = SyncUiState(),
    onSyncEvent: (SyncEvent) -> Unit = {},
    onLog: ((LogChoice) -> Unit)? = null,
    onPopped: (AppKey) -> Unit = {},
    account: @Composable () -> Unit = {}
) {
    val logLabel = stringResource(R.string.log_menu_open)
    LibraryNavHost(
        backStack = backStack,
        tabs = toolbarItems(),
        entryProvider = entryProvider,
        modifier = modifier,
        sync = sync,
        onSyncErrorShown = { onSyncEvent(SyncEvent.WriteErrorShown) },
        action = onLog?.let { ToolbarAction(logLabel, R.drawable.ic_add, logMenu(it)) },
        onPopped = onPopped,
        account = account
    )
}

/** Plusknappens loggmeny (NAV-10): exakt fem val i ordning, med ikon, som android-ui:s FAB-meny. [onPick] får valet. */
@Composable
private fun logMenu(onPick: (LogChoice) -> Unit): List<AppMenuItem> = LogChoice.entries.map { choice -> AppMenuItem(stringResource(choice.label), { onPick(choice) }, icon = choice.icon) }

@Composable
private fun toolbarItems(): List<ToolbarItem> = listOf(
    ToolbarItem(stringResource(R.string.tab_today), R.drawable.ic_sun),
    ToolbarItem(stringResource(R.string.tab_diary), R.drawable.ic_book),
    ToolbarItem(stringResource(R.string.tab_trends), R.drawable.ic_trend),
    ToolbarItem(stringResource(R.string.tab_medicines), R.drawable.ic_pill)
)
