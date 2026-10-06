package se.partee71.dagboken.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import se.partee71.dagboken.BuildConfig
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.ui.TabPlaceholderScreen
import se.partee71.dagboken.ui.components.AccountSheet
import se.partee71.dagboken.ui.components.ComponentGallery
import se.partee71.dagboken.ui.components.LogMenuSheet
import se.partee71.dagboken.ui.components.SettingsPage
import se.partee71.dagboken.ui.medicines.MedicinesRoute
import se.partee71.dagboken.ui.medicines.PrescriptionEditRoute
import se.partee71.dagboken.ui.medicines.PrnMedicineEditRoute
import se.partee71.dagboken.ui.settings.AboutRoute
import se.partee71.dagboken.ui.settings.ExportImportScreen
import se.partee71.dagboken.ui.settings.ListsRoute
import se.partee71.dagboken.ui.settings.OptionEditRoute
import se.partee71.dagboken.ui.settings.ProfileRoute
import se.partee71.dagboken.ui.settings.RemindersRoute
import se.partee71.dagboken.ui.settings.ThemeRoute
import se.partee71.dagboken.ui.sync.SyncViewModel
import se.partee71.dagboken.ui.today.TodayRoute

/** Arken som ligger över flikarna: inställningsarket bakom avataren (NAV-9) och loggmenyn bakom plusknappen (NAV-10). */
enum class RootSheet { Account, Log }

/**
 * Appens innehåll efter inloggning: fyra flikar med egna back stackar (NAV-8, NAV-11), synkläget
 * (NFR-1), inställningsarket bakom avataren och loggmenyn bakom plusknappen. [account] är den
 * inloggade (namn, e-post och foto i avataren och arket – bara i minnet, AUTH-3); [onSignOut] loggar ut.
 */
@Composable
fun AppNavigation(
    account: AuthUser?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    syncViewModel: SyncViewModel = hiltViewModel(),
) {
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    val backStack = rememberAppBackStack()
    val activity = LocalActivity.current
    var sheet by rememberSaveable { mutableStateOf<RootSheet?>(null) }
    val currentAccount = rememberUpdatedState(account)
    val entries = remember(backStack) { appEntries(backStack, account = { currentAccount.value }, onAccount = { sheet = RootSheet.Account }) }
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
        account = account,
        onOpen = { page -> backStack.push(page.key) },
    )
}

/** Underskärmen som en rad i inställningsarket öppnar (NAV-9). */
val SettingsPage.key: AppKey
    get() = when (this) {
        SettingsPage.Profile -> ProfileKey
        SettingsPage.Reminders -> RemindersKey
        SettingsPage.Theme -> ThemeKey
        SettingsPage.Lists -> ListsKey
        SettingsPage.ExportImport -> ExportImportKey
        SettingsPage.About -> AboutKey
    }

/** Det ark som är öppet; varje val stänger arket först. [onOpen] öppnar en underskärm på den aktuella fliken. */
@Composable
fun RootSheets(
    sheet: RootSheet?,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onOpenGallery: (() -> Unit)?,
    account: AuthUser? = null,
    onOpen: (SettingsPage) -> Unit = {},
) {
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
            account = account,
            onOpen = { page ->
                onDismiss()
                onOpen(page)
            },
        )
        // Loggformulären kommer i etapp 5; tills dess stänger varje val bara arket.
        RootSheet.Log -> LogMenuSheet(onDismiss = onDismiss, onPick = { onDismiss() })
        null -> Unit
    }
}

/**
 * Vilken skärm varje nyckel visar – varje nyckel har en (`AppNavigationTest`). Flikarna visar en
 * platshållare tills de byggs i etapp 5 (Idag och Mediciner är byggda); [onAccount] är avataren uppe till höger (NAV-9) med
 * [account]s namn och foto. En skärm stänger sig med `popIfTop(key)`, aldrig `pop()`, så att ett
 * andra tryck inte stänger skärmen under. Inställningsarkets underskärmar läggs på den aktuella
 * flikens stack.
 */
fun appEntries(backStack: AppBackStack, account: () -> AuthUser?, onAccount: () -> Unit): (AppKey) -> NavEntry<AppKey> =
    // Varje nyckel har sin skärm (AppKey är förseglad); en saknad är ett programfel.
    entryProvider(fallback = { key -> error("Ingen skärm för $key") }) {
        entry<TodayKey> {
            TodayRoute(
                account(),
                onAccount,
                onEditPrn = { id -> backStack.push(PrnMedicineEditKey(id)) },
                // TRD-5: "Visa i Trender" under Idags 7-dagarstrend byter flik.
                onOpenTrends = { backStack.select(TrendsKey) },
            )
        }
        entry<DiaryKey> { TabPlaceholderScreen(DiaryKey, account(), onAccount) }
        entry<TrendsKey> { TabPlaceholderScreen(TrendsKey, account(), onAccount) }
        entry<MedicinesKey> {
            MedicinesRoute(
                account(),
                onAccount,
                onOpenPrescription = { id -> backStack.push(PrescriptionEditKey(id)) },
                onOpenPrn = { id -> backStack.push(PrnMedicineEditKey(id)) },
                onExtendPrescription = { id -> backStack.push(PrescriptionEditKey(id, extend = true)) },
            )
        }
        entry<PrescriptionEditKey> { key -> PrescriptionEditRoute(key.id, key.extend, onClose = { backStack.popIfTop(key) }) }
        entry<PrnMedicineEditKey> { key -> PrnMedicineEditRoute(key.id, onClose = { backStack.popIfTop(key) }) }
        entry<ComponentGalleryKey> { key -> ComponentGallery(onBack = { backStack.popIfTop(key) }) }
        entry<ProfileKey> { key -> ProfileRoute(onClose = { backStack.popIfTop(key) }) }
        entry<RemindersKey> { key -> RemindersRoute(onClose = { backStack.popIfTop(key) }) }
        entry<ThemeKey> { key -> ThemeRoute(onBack = { backStack.popIfTop(key) }) }
        entry<ListsKey> { key ->
            ListsRoute(
                onBack = { backStack.popIfTop(key) },
                onAdd = { kind -> backStack.push(OptionEditKey(kind)) },
                onOpen = { option -> backStack.push(OptionEditKey(option.kind, option.id)) },
            )
        }
        entry<OptionEditKey> { key -> OptionEditRoute(key.kind, key.id, onClose = { backStack.popIfTop(key) }) }
        entry<ExportImportKey> { key -> ExportImportScreen(onBack = { backStack.popIfTop(key) }) }
        entry<AboutKey> { key -> AboutRoute(onBack = { backStack.popIfTop(key) }) }
    }
