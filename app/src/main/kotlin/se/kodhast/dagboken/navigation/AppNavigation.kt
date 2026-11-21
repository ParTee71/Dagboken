package se.kodhast.dagboken.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
import se.kodhast.dagboken.ui.components.ComponentGallery
import se.kodhast.dagboken.ui.components.LogChoice
import se.kodhast.dagboken.ui.components.SettingsPage
import se.kodhast.dagboken.ui.components.UserMenu
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
import se.kodhast.dagboken.ui.settings.SettingsScreen
import se.kodhast.dagboken.ui.settings.ThemeRoute
import se.kodhast.dagboken.ui.sync.SyncViewModel
import se.kodhast.dagboken.ui.today.TodayRoute
import se.kodhast.dagboken.ui.trends.TrendsRoute

/**
 * Appens innehåll efter inloggning: fyra flikar med egna back stackar (NAV-8, NAV-11), synkläget
 * (NFR-22), avatarmenyn i flikarnas sidhuvud (NAV-9, NAV-12, med anslutningspluppen) och
 * plusknappens loggmeny (NAV-10). [account] är den inloggade (namn, e-post och foto i avataren och menyn, AUTH-3); [onSignOut] loggar ut. [launch]
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
    val entries = remember(backStack) { appEntries(backStack, onScreening = logViewModel::onEvent) }
    AppNavHost(
        backStack,
        entries,
        modifier,
        sync = sync,
        onSyncEvent = syncViewModel::onEvent,
        onLog = { choice -> backStack.log(choice, logViewModel.logDay(onToday = backStack.currentTab == TodayKey), logViewModel::onEvent) },
        // Samma lambda tills kontot eller anslutningen ändras, så att flikarnas sidhuvud inte ritas om i onödan.
        account = remember(account, sync.online, onSignOut) {
            {
                UserMenu(
                    account,
                    onOpenSettings = { backStack.push(SettingsKey) },
                    onOpenAbout = { backStack.push(AboutKey) },
                    onSignOut = onSignOut,
                    online = sync.online,
                    onOpenGallery = if (BuildConfig.DEBUG) ({ backStack.push(ComponentGalleryKey) }) else null
                )
            }
        }
    )
    LogSheets(logViewModel, onOpen = { target -> backStack.push(target.key) })
    val launchHandled by rememberUpdatedState(onLaunchHandled)
    LaunchedEffect(launch) {
        val target = launch ?: return@LaunchedEffect
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

/** Underskärmen som en rad i Inställningar öppnar (NAV-9). */
val SettingsPage.key: AppKey
    get() = when (this) {
        SettingsPage.Profile -> ProfileKey
        SettingsPage.Reminders -> RemindersKey
        SettingsPage.Theme -> ThemeKey
        SettingsPage.Lists -> ListsKey
        SettingsPage.ExportImport -> ExportImportKey
    }

/**
 * Vilken skärm varje nyckel visar – varje nyckel har en (`AppNavigationTest`). [onScreening] öppnar måendearket ovanpå flikarna – från Idag och
 * från en måendepost i Dagbok – ett enda ark (`LogViewModel`), och dosvalet från Mediciner (MEDF-6). En skärm stänger sig med `popIfTop(key)`, aldrig `pop()`, så att ett
 * andra tryck inte stänger skärmen under. Inställningar, dess underskärmar och Om Dagboken läggs på den aktuella
 * flikens stack, så att tillbaka från en underskärm går till Inställningar (NAV-9).
 */
fun appEntries(
    backStack: AppBackStack,
    onScreening: (LogEvent) -> Unit = {}
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
        entry<SettingsKey> { key -> SettingsScreen(onBack = backStack.closer(key), onOpen = { page -> backStack.push(page.key) }) }
        entry<ProfileKey> { key -> ProfileRoute(onClose = backStack.closer(key)) }
        entry<RemindersKey> { key -> RemindersRoute(onClose = backStack.closer(key)) }
        entry<ThemeKey> { key -> ThemeRoute(onBack = backStack.closer(key)) }
        entry<ListsKey> { key ->
            ListsRoute(
                onBack = backStack.closer(key),
                onAdd = { kind -> backStack.push(OptionEditKey(kind)) },
                onOpen = { option -> backStack.push(OptionEditKey(option.kind, option.id)) }
            )
        }
        entry<OptionEditKey> { key -> OptionEditRoute(key.kind, key.id, onClose = backStack.closer(key)) }
        entry<ExportImportKey> { key -> ExportImportRoute(onBack = backStack.closer(key)) }
        entry<AboutKey> { key -> AboutRoute(onBack = backStack.closer(key)) }
    }
