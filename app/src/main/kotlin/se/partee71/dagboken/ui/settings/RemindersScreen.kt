package se.partee71.dagboken.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
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
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.EditorLoader
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.Validator
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.EntityEditScreen
import se.partee71.dagboken.ui.components.GroupLabel
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

/**
 * Påminnelser i inställningsarket (SET-4, NOT-4, NOT-13, NOT-18): läser och sparar bara gruppen
 * `reminders` i `settings/app`. Här sparas bara inställningarna; larmen schemaläggs i #242.
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
            is RemindersEvent.MedsEnabledChanged -> editor.update { it.copy(medsEnabled = event.enabled) }
            is RemindersEvent.SlotChanged -> editor.update { r ->
                r.copy(medSlots = r.medSlots.map { if (it.slot == event.reminder.slot) event.reminder else it })
            }
            is RemindersEvent.OccasionChanged -> editor.update { r ->
                r.copy(screeningOccasions = r.screeningOccasions.map { if (it.occasion == event.reminder.occasion) event.reminder else it })
            }
            is RemindersEvent.PeriodTimeChanged -> editor.update { it.copy(periodReminderTime = event.time) }
            RemindersEvent.Save -> viewModelScope.launch {
                editor.save { form -> difference.save { it.copy(reminders = form) } }
            }
            RemindersEvent.Retry -> loader.retry()
        }
    }
}

@Composable
fun RemindersRoute(onClose: () -> Unit, viewModel: RemindersEditViewModel = hiltViewModel()) {
    val state by viewModel.editor.state.collectAsStateWithLifecycle()
    RemindersScreen(state, viewModel.editor.effects, viewModel::onEvent, onClose)
}

/**
 * Påminnelser på `EntityEditScreen` (NFR-10): huvudreglaget, en `ReminderTimeRow` per medicintidpunkt
 * (07/10/12/15/19/22 – "Vid behov" har ingen tid) och per måendetillfälle, och periodslutets klockslag
 * utan reglage.
 */
@Composable
fun RemindersScreen(state: EditorUiState<ReminderSettings>, effects: Flow<EditorEffect>, onEvent: (RemindersEvent) -> Unit, onClose: () -> Unit) {
    val reminders = state.value
    EntityEditScreen(
        title = stringResource(R.string.settings_reminders),
        state = state,
        effects = effects,
        onSave = { onEvent(RemindersEvent.Save) },
        onClose = onClose,
        onRetry = { onEvent(RemindersEvent.Retry) },
    ) {
        AppCard {
            SwitchRow(
                stringResource(R.string.reminders_meds),
                reminders.medsEnabled,
                { onEvent(RemindersEvent.MedsEnabledChanged(it)) },
                subtitle = stringResource(R.string.reminders_meds_subtitle),
            )
        }
        GroupLabel(stringResource(R.string.reminders_med_times))
        AppCard {
            reminders.medSlots.forEach { row ->
                ReminderTimeRow(
                    stringResource(row.slot.label()),
                    row.time,
                    { onEvent(RemindersEvent.SlotChanged(row.copy(time = it))) },
                    enabled = row.enabled,
                    onEnabledChange = { onEvent(RemindersEvent.SlotChanged(row.copy(enabled = it))) },
                )
            }
        }
        GroupLabel(stringResource(R.string.log_mood))
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
        GroupLabel(stringResource(R.string.reminders_period))
        AppCard {
            ReminderTimeRow(stringResource(R.string.reminders_period_time), reminders.periodReminderTime, { onEvent(RemindersEvent.PeriodTimeChanged(it)) })
        }
    }
}
