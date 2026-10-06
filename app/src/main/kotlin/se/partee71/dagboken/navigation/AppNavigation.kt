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
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.BuildConfig
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.DiaryEntry
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.ui.components.AccountSheet
import se.partee71.dagboken.ui.components.ComponentGallery
import se.partee71.dagboken.ui.components.LogChoice
import se.partee71.dagboken.ui.components.LogMenuSheet
import se.partee71.dagboken.ui.components.SettingsPage
import se.partee71.dagboken.ui.diary.DiaryEntryPlaceholder
import se.partee71.dagboken.ui.diary.DiaryRoute
import se.partee71.dagboken.ui.log.ActivityEditRoute
import se.partee71.dagboken.ui.log.EventEditRoute
import se.partee71.dagboken.ui.log.LogEvent
import se.partee71.dagboken.ui.log.LogSheets
import se.partee71.dagboken.ui.log.LogUpcomingScreen
import se.partee71.dagboken.ui.log.LogViewModel
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
import se.partee71.dagboken.ui.trends.TrendsRoute

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
    logViewModel: LogViewModel = hiltViewModel(),
) {
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    val saved by logViewModel.notice.collectAsStateWithLifecycle()
    val backStack = rememberAppBackStack()
    val activity = LocalActivity.current
    var sheet by rememberSaveable { mutableStateOf<RootSheet?>(null) }
    val currentAccount = rememberUpdatedState(account)
    val entries = remember(backStack) {
        appEntries(
            backStack,
            account = { currentAccount.value },
            onAccount = { sheet = RootSheet.Account },
            onScreening = logViewModel::onEvent,
        )
    }
    AppNavHost(
        backStack,
        entries,
        modifier,
        onExit = { activity?.finish() },
        sync = sync,
        onSyncEvent = syncViewModel::onEvent,
        onLog = { sheet = RootSheet.Log },
        message = if (saved) stringResource(R.string.today_screening_saved) else null,
        onMessageShown = { logViewModel.onEvent(LogEvent.NoticeShown) },
    )
    RootSheets(
        sheet,
        onDismiss = { sheet = null },
        onSignOut = onSignOut,
        onOpenGallery = if (BuildConfig.DEBUG) ({ backStack.push(ComponentGalleryKey) }) else null,
        account = account,
        onOpen = { page -> backStack.push(page.key) },
        onLog = { choice -> backStack.log(choice, logViewModel.logDay(onToday = backStack.currentTab == TodayKey), logViewModel::onEvent) },
    )
    LogSheets(logViewModel)
}

/**
 * Ett val i plusknappens meny (NAV-10) mot dagen [date] (den Idag visar, `null` = idag): Mående öppnar
 * tillfällesväljaren ([onEvent]), Aktivitet och Händelse sina formulär på den aktuella fliken, Dos och Sjukdom en
 * platshållare tills #271.
 */
fun AppBackStack.log(choice: LogChoice, date: LocalDate?, onEvent: (LogEvent) -> Unit) {
    when (choice) {
        LogChoice.Mood -> onEvent(LogEvent.PickOccasion(date))
        LogChoice.Activity -> push(ActivityEditKey(date = date))
        LogChoice.Event -> push(EventEditKey(date = date))
        LogChoice.Dose, LogChoice.Illness -> push(LogUpcomingKey(choice))
    }
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

/**
 * Det ark som är öppet; varje val stänger arket först. [onOpen] öppnar en underskärm på den aktuella fliken och
 * [onLog] tar emot ett val i plusknappens meny (NAV-10).
 */
@Composable
fun RootSheets(
    sheet: RootSheet?,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onOpenGallery: (() -> Unit)?,
    account: AuthUser? = null,
    onOpen: (SettingsPage) -> Unit = {},
    onLog: (LogChoice) -> Unit = {},
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
        RootSheet.Log -> LogMenuSheet(
            onDismiss = onDismiss,
            onPick = { choice ->
                onDismiss()
                onLog(choice)
            },
        )
        null -> Unit
    }
}

/**
 * Vilken skärm varje nyckel visar – varje nyckel har en (`AppNavigationTest`). [onAccount] är avataren uppe
 * till höger (NAV-9) med [account]s namn och foto; [onScreening] öppnar måendearket ovanpå flikarna – från Idag och
 * från en måendepost i Dagbok – ett enda ark (`LogViewModel`). En skärm stänger sig med `popIfTop(key)`, aldrig `pop()`, så att ett
 * andra tryck inte stänger skärmen under. Inställningsarkets underskärmar läggs på den aktuella
 * flikens stack.
 */
fun appEntries(
    backStack: AppBackStack,
    account: () -> AuthUser?,
    onAccount: () -> Unit,
    onScreening: (LogEvent) -> Unit = {},
): (AppKey) -> NavEntry<AppKey> =
    // Varje nyckel har sin skärm (AppKey är förseglad); en saknad är ett programfel.
    entryProvider(fallback = { key -> error("Ingen skärm för $key") }) {
        entry<TodayKey> {
            TodayRoute(
                account(),
                onAccount,
                onEditPrn = { id -> backStack.push(PrnMedicineEditKey(id)) },
                // TRD-5: "Visa i Trender" under Idags 7-dagarstrend byter flik.
                onOpenTrends = { backStack.select(TrendsKey) },
                onScreening = onScreening,
            )
        }
        // HIST-3: aktivitet och händelse öppnar sina formulär, mående måendearket; dos, episod och incheckning en
        // platshållare tills dosformuläret (#271) och sjukdomsdetaljen (#240) finns.
        entry<DiaryKey> {
            DiaryRoute(account(), onAccount, onOpen = { entry -> entry.key?.let(backStack::push) ?: (entry as? DiaryEntry.Mood)?.let { onScreening(LogEvent.EditScreening(it.screening)) } })
        }
        entry<DiaryEntryKey> { key -> DiaryEntryPlaceholder(key.kind, onBack = { backStack.popIfTop(key) }) }
        entry<ActivityEditKey> { key -> ActivityEditRoute(key.id, key.date, onClose = { backStack.popIfTop(key) }) }
        entry<EventEditKey> { key -> EventEditRoute(key.id, key.date, onClose = { backStack.popIfTop(key) }) }
        entry<LogUpcomingKey> { key -> LogUpcomingScreen(key.choice, onBack = { backStack.popIfTop(key) }) }
        entry<TrendsKey> { TrendsRoute(account(), onAccount) }
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
