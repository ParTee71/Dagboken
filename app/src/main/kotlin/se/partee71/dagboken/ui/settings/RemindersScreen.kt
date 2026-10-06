package se.partee71.dagboken.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.SlotReminder
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.reminders.ReminderAccess
import se.partee71.dagboken.reminders.needsNotificationPermission
import se.partee71.dagboken.reminders.openExactAlarmSettings
import se.partee71.dagboken.reminders.openNotificationSettings
import se.partee71.dagboken.reminders.reminderAccess
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.EditorLoader
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.Validator
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.EntityEditScreen
import se.partee71.dagboken.ui.components.LabeledGroup
import se.partee71.dagboken.ui.components.NoticeBanner
import se.partee71.dagboken.ui.components.ReminderTimeRow
import se.partee71.dagboken.ui.components.SwitchRow

sealed interface RemindersEvent {
    data class MedsEnabledChanged(val enabled: Boolean) : RemindersEvent

    /** En medicintidpunkts reglage eller klockslag (NOT-18). */
    data class SlotChanged(val reminder: SlotReminder) : RemindersEvent

    /** Ett måendetillfälles reglage eller klockslag (NOT-4). */
    data class OccasionChanged(val reminder: OccasionReminder) : RemindersEvent

    /** Periodslutspåminnelsens klockslag (NOT-13). */
    data class PeriodTimeChanged(val time: LocalTime) : RemindersEvent

    data object Save : RemindersEvent

    data object Retry : RemindersEvent
}

/** Formulärets ändring av [current] för händelsen – samma regel för formuläret och för [turnsOn]. */
fun RemindersEvent.applyTo(current: ReminderSettings): ReminderSettings = when (this) {
    is RemindersEvent.MedsEnabledChanged -> current.copy(medsEnabled = enabled)
    is RemindersEvent.SlotChanged -> current.copy(medSlots = current.medSlots.map { if (it.slot == reminder.slot) reminder else it })
    is RemindersEvent.OccasionChanged -> current.copy(screeningOccasions = current.screeningOccasions.map { if (it.occasion == reminder.occasion) reminder else it })
    is RemindersEvent.PeriodTimeChanged -> current.copy(periodReminderTime = time)
    RemindersEvent.Save, RemindersEvent.Retry -> current
}

/**
 * NOT-16: om händelsen gör en påminnelse **aktiv** som inte var det i [current] – då begärs notisbehörigheten. En
 * medicintidpunkt som slås på medan huvudreglaget är av, huvudreglaget utan någon påslagen tidpunkt, ett ändrat
 * klockslag eller något som slås av gör det inte.
 */
fun RemindersEvent.turnsOn(current: ReminderSettings): Boolean = (applyTo(current).active() - current.active()).isNotEmpty()

/** De påminnelser som skulle ge notiser: påslagna medicintider (när huvudreglaget är på) och måendetillfällen. */
private fun ReminderSettings.active(): Set<Any> =
    (if (medsEnabled) medSlots.filter { it.enabled }.map { it.slot } else emptyList<Any>()).toSet() +
        screeningOccasions.filter { it.enabled }.map { it.occasion }

/**
 * Påminnelser i inställningsarket (SET-4, NOT-4, NOT-13, NOT-18): läser och sparar bara gruppen
 * `reminders` i `settings/app`. Här sparas bara inställningarna; larmen följer dem via `ReminderSync` (NOT-7).
 */
@HiltViewModel
class RemindersEditViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    // Varje kombination av reglage och klockslag är giltig – "Spara" följer bara ändrat-läget.
    val editor: EditorState<ReminderSettings> = EditorState(ReminderSettings(), Validator { emptyMap() }, loading = true)
    // Det lagrade (loader.stored) är det sparningen jämför mot när bara skillnaden ska skrivas.
    private val loader = EditorLoader(editor, viewModelScope, read = { settings.get() }, project = { it.reminders })

    private val difference = SettingsDifference(settings) { loader.stored.value }

    fun onEvent(event: RemindersEvent) {
        when (event) {
            is RemindersEvent.MedsEnabledChanged, is RemindersEvent.SlotChanged, is RemindersEvent.OccasionChanged,
            is RemindersEvent.PeriodTimeChanged -> editor.update { event.applyTo(it) }
            RemindersEvent.Save -> viewModelScope.launch {
                editor.save { form -> difference.save { it.copy(reminders = form) } }
            }
            RemindersEvent.Retry -> loader.retry()
        }
    }
}

/**
 * NOT-16: behörigheterna läses när skärmen visas och igen när användaren kommer tillbaka från systeminställningarna;
 * notisbehörigheten begärs när en påminnelse slås på ([turnsOn]) – inte vid appens första start.
 */
@Composable
fun RemindersRoute(onClose: () -> Unit, viewModel: RemindersEditViewModel = hiltViewModel()) {
    val state by viewModel.editor.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var access by remember { mutableStateOf(context.reminderAccess()) }
    LifecycleResumeEffect(context) {
        access = context.reminderAccess()
        onPauseOrDispose {}
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { access = context.reminderAccess() }
    RemindersScreen(
        state,
        viewModel.editor.effects,
        onEvent = { event ->
            if (event.turnsOn(state.value) && context.needsNotificationPermission()) requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            viewModel.onEvent(event)
        },
        onClose = onClose,
        access = access,
        onOpenNotificationSettings = context::openNotificationSettings,
        onOpenExactAlarmSettings = context::openExactAlarmSettings,
    )
}

/**
 * Påminnelser på `EntityEditScreen` (NFR-10): huvudreglaget, en `ReminderTimeRow` per medicintidpunkt
 * (07/10/12/15/19/22 – "Vid behov" har ingen tid) och per måendetillfälle, och periodslutets klockslag
 * utan reglage – varje grupp som `LabeledGroup`. En avslagen påminnelse tonas ned, och medicintiderna
 * när huvudreglaget är av. Saknas behörighet att visa notiser eller ställa exakta larm ([access]) står det överst
 * som en `NoticeBanner` som öppnar systeminställningarna (NOT-16).
 */
@Composable
fun RemindersScreen(
    state: EditorUiState<ReminderSettings>,
    effects: Flow<EditorEffect>,
    onEvent: (RemindersEvent) -> Unit,
    onClose: () -> Unit,
    access: ReminderAccess = ReminderAccess(),
    onOpenNotificationSettings: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
) {
    val reminders = state.value
    EntityEditScreen(
        title = stringResource(R.string.settings_reminders),
        state = state,
        effects = effects,
        onSave = { onEvent(RemindersEvent.Save) },
        onClose = onClose,
        onRetry = { onEvent(RemindersEvent.Retry) },
    ) {
        if (!access.notifications) {
            NoticeBanner(
                stringResource(R.string.reminders_notifications_off),
                R.drawable.ic_bell,
                onOpenNotificationSettings,
                onClickLabel = stringResource(R.string.reminders_open_system_settings),
                detail = stringResource(R.string.reminders_notifications_off_detail),
            )
        }
        if (!access.exactAlarms) {
            NoticeBanner(
                stringResource(R.string.reminders_exact_off),
                R.drawable.ic_clock,
                onOpenExactAlarmSettings,
                onClickLabel = stringResource(R.string.reminders_open_system_settings),
                detail = stringResource(R.string.reminders_exact_off_detail),
            )
        }
        AppCard {
            SwitchRow(
                stringResource(R.string.reminders_meds),
                reminders.medsEnabled,
                { onEvent(RemindersEvent.MedsEnabledChanged(it)) },
                subtitle = stringResource(R.string.reminders_meds_subtitle),
            )
        }
        LabeledGroup(stringResource(R.string.reminders_med_times)) {
            AppCard {
                reminders.medSlots.forEach { row ->
                    ReminderTimeRow(
                        stringResource(row.slot.label()),
                        row.time,
                        { onEvent(RemindersEvent.SlotChanged(row.copy(time = it))) },
                        enabled = row.enabled,
                        onEnabledChange = { onEvent(RemindersEvent.SlotChanged(row.copy(enabled = it))) },
                        // Medicintiderna går att ställa även när medicinpåminnelserna är av, men tonas ned.
                        inactive = !reminders.medsEnabled,
                    )
                }
            }
        }
        LabeledGroup(stringResource(R.string.log_mood)) {
            AppCard {
                reminders.screeningOccasions.forEach { row ->
                    ReminderTimeRow(
                        stringResource(row.occasion.label()),
                        row.time,
                        { onEvent(RemindersEvent.OccasionChanged(row.copy(time = it))) },
                        enabled = row.enabled,
                        onEnabledChange = { onEvent(RemindersEvent.OccasionChanged(row.copy(enabled = it))) },
                    )
                }
            }
        }
        LabeledGroup(stringResource(R.string.reminders_period)) {
            AppCard {
                ReminderTimeRow(stringResource(R.string.reminders_period_time), reminders.periodReminderTime, { onEvent(RemindersEvent.PeriodTimeChanged(it)) })
            }
        }
    }
}
