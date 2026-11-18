package se.kodhast.dagboken.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import kotlinx.datetime.LocalDate
import se.kodhast.dagboken.BuildConfig
import se.kodhast.dagboken.R
import se.kodhast.dagboken.core.engine.DiaryEntry
import se.kodhast.dagboken.data.auth.AuthUser
import se.kodhast.dagboken.reminders.ReminderLaunch
import se.kodhast.dagboken.ui.components.AccountSheet
import se.kodhast.dagboken.ui.components.ComponentGallery
import se.kodhast.dagboken.ui.components.LogChoice
import se.kodhast.dagboken.ui.components.SettingsPage
import se.kodhast.dagboken.ui.components.UserAvatar
import se.kodhast.dagboken.ui.diary.DiaryRoute
import se.kodhast.dagboken.ui.illness.IllnessDetailRoute
import se.kodhast.dagboken.ui.log.ActivityEditRoute
import se.kodhast.dagboken.ui.log.CheckinEditRoute
import se.kodhast.dagboken.ui.log.DoseEditRoute
import se.kodhast.dagboken.ui.log.EpisodeEditRoute
import se.kodhast.dagboken.ui.log.EpisodeNewRoute
import se.kodhast.dagboken.ui.log.EventEditRoute
import se.kodhast.dagboken.ui.log.LogEvent
import se.kodhast.dagboken.ui.log.LogSheets
import se.kodhast.dagboken.ui.log.LogTarget
import se.kodhast.dagboken.ui.log.LogViewModel
import se.kodhast.dagboken.ui.medicines.MedicinesRoute
import se.kodhast.dagboken.ui.medicines.PrescriptionEditRoute
import se.kodhast.dagboken.ui.medicines.PrnMedicineEditRoute
import se.kodhast.dagboken.ui.settings.AboutRoute
import se.kodhast.dagboken.ui.settings.ExportImportRoute
import se.kodhast.dagboken.ui.settings.ListsRoute
import se.kodhast.dagboken.ui.settings.OptionEditRoute
import se.kodhast.dagboken.ui.settings.ProfileRoute
import se.kodhast.dagboken.ui.settings.RemindersRoute
import se.kodhast.dagboken.ui.settings.ThemeRoute
import se.kodhast.dagboken.ui.sync.SyncViewModel
import se.kodhast.dagboken.ui.today.TodayRoute
import se.kodhast.dagboken.ui.trends.TrendsRoute

/** Arket som ligger över flikarna: inställningsarket bakom avataren (NAV-9). Loggmenyn är plusknappens FAB-meny i bottenraden (NAV-10). */
enum class RootSheet { Account }

/**
 * Appens innehåll efter inloggning: fyra flikar med egna back stackar (NAV-8, NAV-11), synkläget
 * (NFR-22), inställningsarket bakom avataren i flikarnas sidhuvud (NAV-9, NAV-12, med anslutningspluppen) och
 * plusknappens loggmeny (NAV-10). [account] är den inloggade (namn, e-post och foto i avataren och arket, AUTH-3); [onSignOut] loggar ut. [launch]
 * är vad en tryckt påminnelse ska öppna ([open]); när det är gjort anropas [onLaunchHandled].
 */
@Composable
fun AppNavigation(
    account: AuthUser?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    launch: ReminderLaunch? = null,
    onLaunchHandled: () -> Unit = {},
    syncViewModel: SyncViewModel = hiltViewModel(),
    logViewModel: LogViewModel = hiltViewModel()
) {
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    val backStack = rememberAppBackStack()
    val activity = LocalActivity.current
    var sheet by rememberSaveable { mutableStateOf<RootSheet?>(null) }
    val entries = remember(backStack) {
        appEntries(
            backStack,
            onScreening = logViewModel::onEvent,
            onSettingsClosed = { sheet = RootSheet.Account }
        )
    }
    AppNavHost(
        backStack,
        entries,
        modifier,
        onExit = { activity?.finish() },
        sync = sync,
        onSyncEvent = syncViewModel::onEvent,
        onLog = { choice -> backStack.log(choice, logViewModel.logDay(onToday = backStack.currentTab == TodayKey), logViewModel::onEvent) },
        // NAV-9: systemets tillbaka från en underskärm i inställningsarket öppnar arket igen, som pilen.
        onPopped = { key -> if (key.isSettingsPage) sheet = RootSheet.Account },
        // Samma lambda tills kontot eller anslutningen ändras, så att flikarnas sidhuvud inte ritas om i onödan.
        account = remember(account, sync.online) { { UserAvatar(account, onClick = { sheet = RootSheet.Account }, online = sync.online) } }
    )
    RootSheets(
        sheet,
        onDismiss = { sheet = null },
        onSignOut = onSignOut,
        onOpenGallery = if (BuildConfig.DEBUG) ({ backStack.push(ComponentGalleryKey) }) else null,
        account = account,
        onOpen = { page -> backStack.push(page.key) }
    )
    LogSheets(logViewModel, onOpen = { target -> backStack.push(target.key) })
    val launchHandled by rememberUpdatedState(onLaunchHandled)
    LaunchedEffect(launch) {
        val target = launch ?: return@LaunchedEffect
        sheet = null
        backStack.open(target, logViewModel::onEvent)
        launchHandled()
    }
}

/**
 * En tryckt påminnelse (NOT-9, NOT-11, NOT-12): medicin- och måendepåminnelsen öppnar Idag – "Logga nu" dessutom
 * måendearket för tillfället, idag och nu ([onEvent]), Stundens "Välj känslor" samma ark på känslosteget (NOT-20,
 * SCR-7) – och periodslutet fliken Mediciner.
 */
fun AppBackStack.open(launch: ReminderLaunch, onEvent: (LogEvent) -> Unit) {
    when (launch) {
        ReminderLaunch.Today -> select(TodayKey)

        is ReminderLaunch.LogMood -> {
            select(TodayKey)
            onEvent(LogEvent.LogScreening(launch.occasion, date = null, reminder = null))
        }

        ReminderLaunch.Medicines -> select(MedicinesKey)
    }
}

/** Formuläret ett val i plusknappens dos- eller sjukdomsval öppnar (NAV-10). */
val LogTarget.key: AppKey
    get() = when (this) {
        is LogTarget.AsNeeded -> DoseEditKey(prnId = prnId, date = date)
        is LogTarget.OneOffDose -> DoseEditKey(date = date)
        is LogTarget.Checkin -> CheckinEditKey(episodeId, date = date)
        is LogTarget.NewEpisode -> EpisodeNewKey(date)
    }

/**
 * Ett val i plusknappens meny (NAV-10) mot dagen [date] (den Idag visar, `null` = idag): Mående öppnar
 * tillfällesväljaren, Dos och Sjukdom sina val ([onEvent], ark ovanpå flikarna), Aktivitet och Händelse sina
 * formulär på den aktuella fliken.
 */
fun AppBackStack.log(choice: LogChoice, date: LocalDate?, onEvent: (LogEvent) -> Unit) {
    when (choice) {
        LogChoice.Mood -> onEvent(LogEvent.PickOccasion(date))
        LogChoice.Activity -> push(ActivityEditKey(date = date))
        LogChoice.Dose -> onEvent(LogEvent.PickDose(date))
        LogChoice.Event -> push(EventEditKey(date = date))
        LogChoice.Illness -> onEvent(LogEvent.PickIllness(date))
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

/** Om [this] är en underskärm som en rad i inställningsarket öppnar – tillbaka därifrån öppnar arket igen (NAV-9). */
val AppKey.isSettingsPage: Boolean
    get() = SettingsPage.entries.any { it.key == this }

/**
 * Det ark som är öppet; varje val stänger arket först. [onOpen] öppnar en underskärm på den aktuella fliken.
 */
@Composable
fun RootSheets(
    sheet: RootSheet?,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onOpenGallery: (() -> Unit)?,
    account: AuthUser? = null,
    onOpen: (SettingsPage) -> Unit = {}
) {
    /** Arket stängs först, sedan görs valet. */
    fun dismissThen(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }

    fun <A> dismissThen(action: (A) -> Unit): (A) -> Unit = { argument ->
        onDismiss()
        action(argument)
    }
    when (sheet) {
        RootSheet.Account -> AccountSheet(
            onDismiss = onDismiss,
            onSignOut = dismissThen(onSignOut),
            onOpenGallery = onOpenGallery?.let(::dismissThen),
            account = account,
            onOpen = dismissThen(onOpen)
        )

        null -> Unit
    }
}

/**
 * Vilken skärm varje nyckel visar – varje nyckel har en (`AppNavigationTest`). [onScreening] öppnar måendearket ovanpå flikarna – från Idag och
 * från en måendepost i Dagbok – ett enda ark (`LogViewModel`), och dosvalet från Mediciner (MEDF-6). En skärm stänger sig med `popIfTop(key)`, aldrig `pop()`, så att ett
 * andra tryck inte stänger skärmen under. Inställningsarkets underskärmar läggs på den aktuella
 * flikens stack, och när de stängs anropas [onSettingsClosed] (arket öppnas igen, NAV-9).
 */
fun appEntries(
    backStack: AppBackStack,
    onScreening: (LogEvent) -> Unit = {},
    onSettingsClosed: () -> Unit = {}
): (AppKey) -> NavEntry<AppKey> = // Varje nyckel har sin skärm (AppKey är förseglad); en saknad är ett programfel.
    entryProvider(fallback = { key -> error("Ingen skärm för $key") }) {
        entry<TodayKey> {
            TodayRoute(
                onEditPrn = { id -> backStack.push(PrnMedicineEditKey(id)) },
                // FAV-12: Redigera i långtrycksmenyn på ett stjärnmärkt recept.
                onEditPrescription = { id -> backStack.push(PrescriptionEditKey(id)) },
                // TRD-5: "Visa i Trender" under Idags 7-dagarstrend byter flik.
                onOpenTrends = { backStack.select(TrendsKey) },
                onScreening = onScreening,
                onLogLater = { prnId, date -> backStack.push(DoseEditKey(prnId = prnId, date = date)) },
                // HEM-12: den pågående sjukdomen öppnar sjukdomsdetaljen på Idags stack.
                onOpenIllness = { episodeId, date -> backStack.push(EpisodeKey(episodeId, date)) }
            )
        }
        // HIST-3: aktivitet, händelse, dos och incheckning öppnar sina formulär, mående måendearket; episodens start och
        // slut sjukdomsdetaljen (HIST-9).
        entry<DiaryKey> {
            DiaryRoute(onOpen = { entry -> entry.key?.let(backStack::push) ?: (entry as? DiaryEntry.Mood)?.let { onScreening(LogEvent.EditScreening(it.screening)) } })
        }
        entry<EpisodeKey> { key ->
            IllnessDetailRoute(
                key.id,
                onBack = backStack.closer(key),
                onEdit = { backStack.push(EpisodeEditKey(key.id)) },
                onCheckin = { checkinId -> backStack.push(key.checkinKey(checkinId)) }
            )
        }
        entry<EpisodeEditKey> { key -> EpisodeEditRoute(key.id, onClose = backStack.closer(key)) }
        entry<ActivityEditKey> { key -> ActivityEditRoute(key.id, key.date, onClose = backStack.closer(key)) }
        entry<EventEditKey> { key -> EventEditRoute(key.id, key.date, onClose = backStack.closer(key)) }
        entry<DoseEditKey> { key -> DoseEditRoute(key.id, key.prnId, key.date, onClose = backStack.closer(key)) }
        entry<EpisodeNewKey> { key -> EpisodeNewRoute(key.date, onClose = backStack.closer(key)) }
        entry<CheckinEditKey> { key -> CheckinEditRoute(key.episodeId, key.id, key.date, onClose = backStack.closer(key)) }
        entry<TrendsKey> { TrendsRoute() }
        entry<MedicinesKey> {
            MedicinesRoute(
                onOpenPrescription = { id -> backStack.push(PrescriptionEditKey(id)) },
                onOpenPrn = { id -> backStack.push(PrnMedicineEditKey(id)) },
                onExtendPrescription = { id -> backStack.push(PrescriptionEditKey(id, extend = true)) },
                // MEDF-6: samma dosval som plusknappen, mot idag.
                onLogDose = { onScreening(LogEvent.PickDose(null)) }
            )
        }
        entry<PrescriptionEditKey> { key -> PrescriptionEditRoute(key.id, key.extend, onClose = backStack.closer(key)) }
        entry<PrnMedicineEditKey> { key -> PrnMedicineEditRoute(key.id, onClose = backStack.closer(key)) }
        entry<ComponentGalleryKey> { key -> ComponentGallery(onBack = backStack.closer(key)) }
        entry<ProfileKey> { key -> ProfileRoute(onClose = backStack.closer(key, onSettingsClosed)) }
        entry<RemindersKey> { key -> RemindersRoute(onClose = backStack.closer(key, onSettingsClosed)) }
        entry<ThemeKey> { key -> ThemeRoute(onBack = backStack.closer(key, onSettingsClosed)) }
        entry<ListsKey> { key ->
            ListsRoute(
                onBack = backStack.closer(key, onSettingsClosed),
                onAdd = { kind -> backStack.push(OptionEditKey(kind)) },
                onOpen = { option -> backStack.push(OptionEditKey(option.kind, option.id)) }
            )
        }
        entry<OptionEditKey> { key -> OptionEditRoute(key.kind, key.id, onClose = backStack.closer(key)) }
        entry<ExportImportKey> { key -> ExportImportRoute(onBack = backStack.closer(key, onSettingsClosed)) }
        entry<AboutKey> { key -> AboutRoute(onBack = backStack.closer(key, onSettingsClosed)) }
    }
