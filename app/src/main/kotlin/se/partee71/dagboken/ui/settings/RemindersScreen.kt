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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.enabledOccasions
import se.partee71.dagboken.core.engine.turnsOnIn
import se.partee71.dagboken.core.engine.withOccasion
import se.partee71.dagboken.core.engine.withSlot
import se.partee71.dagboken.core.model.MomentSettings
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.SlotReminder
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.reminders.ReminderAccess
import se.partee71.dagboken.reminders.needsNotificationPermission
import se.partee71.dagboken.reminders.openExactAlarmSettings
import se.partee71.dagboken.reminders.openFullScreenIntentSettings
import se.partee71.dagboken.reminders.openNotificationSettings
import se.partee71.dagboken.reminders.reminderAccess
import se.partee71.dagboken.ui.common.formatDuration
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.components.NoticeBanner
import se.partee71.dagboken.ui.components.ReminderTimeRow
import se.partee71.dagboken.ui.components.ValueSlider
import se.partee71.ui.common.EditorEvents
import se.partee71.ui.common.EditorState
import se.partee71.ui.common.EditorUiState
import se.partee71.ui.common.Validator
import se.partee71.ui.components.AppCard
import se.partee71.ui.components.EntityEditScreen
import se.partee71.ui.components.LabeledGroup
import se.partee71.ui.components.SwitchRow

sealed interface RemindersEvent {
    data class MedsEnabledChanged(val enabled: Boolean) : RemindersEvent

    /** En medicintidpunkts reglage eller klockslag (NOT-18). */
    data class SlotChanged(val reminder: SlotReminder) : RemindersEvent

    /** Ett måendetillfälles reglage eller klockslag (NOT-4). */
    data class OccasionChanged(val reminder: OccasionReminder) : RemindersEvent

    /** Periodslutspåminnelsens klockslag (NOT-13). */
    data class PeriodTimeChanged(val time: LocalTime) : RemindersEvent

    /** Stunden: på/av, längden, Ta över skärmen eller Hjälpfråga (NOT-20, NOT-21). */
    data class MomentChanged(val moment: MomentSettings) : RemindersEvent

    data object Save : RemindersEvent

    data object Retry : RemindersEvent
}

/** Formulärets ändring av [current] för händelsen – samma regel för formuläret och för [turnsOn]. */
fun RemindersEvent.applyTo(current: ReminderSettings): ReminderSettings = when (this) {
    is RemindersEvent.MedsEnabledChanged -> current.copy(medsEnabled = enabled)
    is RemindersEvent.SlotChanged -> current.withSlot(reminder)
    is RemindersEvent.OccasionChanged -> current.withOccasion(reminder)
    is RemindersEvent.PeriodTimeChanged -> current.copy(periodReminderTime = time)
    is RemindersEvent.MomentChanged -> current.copy(moment = moment)
    RemindersEvent.Save, RemindersEvent.Retry -> current
}

/** NOT-16: om händelsen gör en påminnelse aktiv som inte var det i [current] (`turnsOnIn`) – då begärs notisbehörigheten. */
fun RemindersEvent.turnsOn(current: ReminderSettings): Boolean = current.turnsOnIn(applyTo(current))

/**
 * Påminnelser i inställningsarket (SET-4, NOT-4, NOT-13, NOT-18, NOT-20): läser och sparar bara gruppen
 * `reminders` i `settings/app`. Här sparas bara inställningarna; larmen följer dem via `ReminderSync` (NOT-7).
 */
@HiltViewModel
class RemindersEditViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    // Varje kombination av reglage och klockslag är giltig – "Spara" följer bara ändrat-läget.
    private val form = SettingsEditor(
        settings,
        viewModelScope,
        ReminderSettings(),
        Validator { emptyMap() },
        project = { it.reminders },
        apply = { stored, form -> stored.copy(reminders = form) }
    )

    val editor: EditorState<ReminderSettings> = form.editor

    fun onEvent(event: RemindersEvent) {
        when (event) {
            is RemindersEvent.MedsEnabledChanged, is RemindersEvent.SlotChanged, is RemindersEvent.OccasionChanged,
            is RemindersEvent.PeriodTimeChanged, is RemindersEvent.MomentChanged -> editor.update { event.applyTo(it) }

            RemindersEvent.Save -> form.save()

            RemindersEvent.Retry -> form.retry()
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
        viewModel.editor,
        onEvent = { event ->
            if (event.turnsOn(state.value) && context.needsNotificationPermission()) requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            viewModel.onEvent(event)
        },
        onClose = onClose,
        access = access,
        onOpenNotificationSettings = context::openNotificationSettings,
        onOpenExactAlarmSettings = context::openExactAlarmSettings,
        onOpenFullScreenSettings = context::openFullScreenIntentSettings
    )
}

/**
 * Påminnelser på `EntityEditScreen` (NFR-10): huvudreglaget, en `ReminderTimeRow` per medicintidpunkt
 * (07/10/12/15/19/22 – "Vid behov" har ingen tid) och per måendetillfälle, och periodslutets klockslag
 * utan reglage – varje grupp som `LabeledGroup`. En avslagen påminnelse tonas ned, och medicintiderna
 * när huvudreglaget är av. Saknas behörighet att visa notiser eller ställa exakta larm ([access]) står det överst
 * som en `NoticeBanner` som öppnar systeminställningarna (NOT-16). Efter måendetillfällena sektionen Stunden (NOT-20):
 * på/av med antalet påslagna tillfällen, längden (`ValueSlider`, 15 s–5 min i steg om 15 s), Ta över skärmen och
 * Hjälpfråga – nedtonade när Stunden är av – och "Tillåt helskärm" när Stunden ska ta över skärmen men systemet inte
 * tillåter helskärm ([ReminderAccess.fullScreen]).
 */
@Composable
fun RemindersScreen(
    state: EditorUiState<ReminderSettings>,
    events: EditorEvents,
    onEvent: (RemindersEvent) -> Unit,
    onClose: () -> Unit,
    access: ReminderAccess = ReminderAccess(),
    onOpenNotificationSettings: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
    onOpenFullScreenSettings: () -> Unit = {}
) {
    val reminders = state.value
    EntityEditScreen(
        title = stringResource(R.string.settings_reminders),
        state = state,
        events = events,
        onSave = { onEvent(RemindersEvent.Save) },
        onClose = onClose,
        onRetry = { onEvent(RemindersEvent.Retry) }
    ) {
        if (!access.notifications) {
            NoticeBanner(
                stringResource(R.string.reminders_notifications_off),
                R.drawable.ic_bell,
                onOpenNotificationSettings,
                onClickLabel = stringResource(R.string.reminders_open_system_settings),
                detail = stringResource(R.string.reminders_notifications_off_detail)
            )
        }
        if (!access.exactAlarms) {
            NoticeBanner(
                stringResource(R.string.reminders_exact_off),
                R.drawable.ic_clock,
                onOpenExactAlarmSettings,
                onClickLabel = stringResource(R.string.reminders_open_system_settings),
                detail = stringResource(R.string.reminders_exact_off_detail)
            )
        }
        AppCard {
            SwitchRow(
                stringResource(R.string.reminders_meds),
                reminders.medsEnabled,
                { onEvent(RemindersEvent.MedsEnabledChanged(it)) },
                subtitle = stringResource(R.string.reminders_meds_subtitle)
            )
        }
        // NOT-18: klockslaget är tidpunktens – det gäller också doserna, också när påminnelsen är av.
        LabeledGroup(stringResource(R.string.reminders_med_times), helper = stringResource(R.string.reminders_med_times_helper)) {
            AppCard {
                reminders.medSlots.forEach { row ->
                    ReminderTimeRow(
                        stringResource(row.slot.label()),
                        row.time,
                        { onEvent(RemindersEvent.SlotChanged(row.copy(time = it))) },
                        enabled = row.enabled,
                        onEnabledChange = { onEvent(RemindersEvent.SlotChanged(row.copy(enabled = it))) },
                        // Medicintiderna går att ställa även när medicinpåminnelserna är av, men tonas ned.
                        inactive = !reminders.medsEnabled
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
                        onEnabledChange = { onEvent(RemindersEvent.OccasionChanged(row.copy(enabled = it))) }
                    )
                }
            }
        }
        MomentSection(reminders, access, onEvent, onOpenFullScreenSettings)
        LabeledGroup(stringResource(R.string.reminders_period)) {
            AppCard {
                ReminderTimeRow(stringResource(R.string.reminders_period_time), reminders.periodReminderTime, { onEvent(RemindersEvent.PeriodTimeChanged(it)) })
            }
        }
    }
}

/** Sektionen Stunden (NOT-20, NOT-21) – gäller alla påslagna måendetillfällen; inget val per tillfälle (NOT-4). */
@Composable
private fun MomentSection(reminders: ReminderSettings, access: ReminderAccess, onEvent: (RemindersEvent) -> Unit, onOpenFullScreenSettings: () -> Unit) {
    val moment = reminders.moment
    val change = { updated: MomentSettings -> onEvent(RemindersEvent.MomentChanged(updated)) }
    val occasions = reminders.enabledOccasions.size
    LabeledGroup(stringResource(R.string.reminders_moment), helper = stringResource(R.string.reminders_moment_helper)) {
        if (moment.enabled && moment.takeover && !access.fullScreen) {
            NoticeBanner(
                stringResource(R.string.reminders_full_screen_off),
                R.drawable.ic_bell,
                onOpenFullScreenSettings,
                onClickLabel = stringResource(R.string.reminders_open_system_settings),
                detail = stringResource(R.string.reminders_full_screen_off_detail)
            )
        }
        AppCard {
            SwitchRow(
                stringResource(R.string.reminders_moment),
                moment.enabled,
                { change(moment.copy(enabled = it)) },
                subtitle = pluralStringResource(R.plurals.reminders_moment_subtitle, occasions, occasions)
            )
            ValueSlider(
                stringResource(R.string.reminders_moment_length),
                moment.seconds,
                { change(moment.copy(seconds = it)) },
                valueRange = MomentSettings.MIN_SECONDS..MomentSettings.MAX_SECONDS,
                enabled = moment.enabled,
                step = MomentSettings.STEP_SECONDS,
                valueText = formatDuration(moment.seconds)
            )
            SwitchRow(
                stringResource(R.string.reminders_moment_takeover),
                moment.takeover,
                { change(moment.copy(takeover = it)) },
                subtitle = stringResource(R.string.reminders_moment_takeover_subtitle),
                inactive = !moment.enabled
            )
            SwitchRow(
                stringResource(R.string.reminders_moment_question),
                moment.question,
                { change(moment.copy(question = it)) },
                subtitle = stringResource(R.string.reminders_moment_question_subtitle),
                inactive = !moment.enabled
            )
        }
    }
}
