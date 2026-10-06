package se.partee71.dagboken.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.typeChoices
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.EventCodec
import se.partee71.dagboken.data.repository.EventRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.ui.common.EntryEditEvent
import se.partee71.dagboken.ui.common.EntryForm
import se.partee71.dagboken.ui.common.hasErrorOutside
import se.partee71.dagboken.ui.common.rememberEntryForm
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EntryEditor
import se.partee71.dagboken.ui.common.choices
import se.partee71.dagboken.ui.common.entryDeleteAction
import se.partee71.dagboken.ui.common.nonBlank
import se.partee71.dagboken.ui.common.rulesValidator
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppTextField
import se.partee71.dagboken.ui.components.DateTimeRow
import se.partee71.dagboken.ui.components.DurationRow
import se.partee71.dagboken.ui.components.EntityEditScreen
import se.partee71.dagboken.ui.components.NoteField
import se.partee71.dagboken.ui.components.TypeChoiceField
import se.partee71.dagboken.ui.components.ValueSlider

/** Fälten i formuläret – codecens namn, så att rules-felen ([rulesValidator]) hamnar på rätt fält. */
object EventField {
    const val TYPE = "optionId"
    const val TRIGGERS = "triggers"
    const val ACTIONS = "actions"
    const val NOTE = "note"
}

/** HAN-1: en typ måste vara vald; allt annat som rules (TextLimits, intervallen). */
val eventValidator = rulesValidator(CollectionNames.EVENTS, EventCodec) { e ->
    if (e.optionId.isBlank()) mapOf(EventField.TYPE to R.string.entry_type_missing) else emptyMap()
}

/** Det som sparas: trimmad text, och en tom text som ingen. */
internal fun Event.cleaned(): Event = copy(
    triggers = triggers?.trim().nonBlank(),
    actions = actions?.trim().nonBlank(),
    note = note?.trim().nonBlank(),
)

/** Fälten som formuläret visar ett fel vid; ett fel på något annat visas överst (`EntityEditScreen(formError)`). */
private val SHOWN_ERRORS = setOf(EventField.TYPE, EventField.TRIGGERS, EventField.ACTIONS, EventField.NOTE)

/**
 * Ny eller ändrad händelse (HAN-1, SET-9; [id] = `null` för ny). En ny loggas mot [date] (den dag Idag visar,
 * `null` = idag, HEM-14, NAV-10) kl. nu med svårighetsgraden 5 (`EventRepository.new`). Formuläret redigerar
 * modellen själv, så id, skapandetid och okända fält följer med, och en ändrad skriver bara ändrade fält – [form]
 * är det delade postformuläret.
 */
@HiltViewModel(assistedFactory = EventEditViewModel.Factory::class)
class EventEditViewModel @AssistedInject constructor(
    events: EventRepository,
    options: OptionsRepository,
    clock: Clock,
    zone: Provider<TimeZone>,
    @Assisted id: String?,
    @Assisted date: LocalDate?,
) : ViewModel() {
    val form = EntryEditor(
        viewModelScope,
        events,
        id,
        eventValidator,
        placeholder = Event(""),
        create = {
            val now = clock.now().toLocalDateTime(zone.get())
            events.new(date ?: now.date, LocalTime(now.hour, now.minute))
        },
        clean = { _, value -> value.cleaned() },
    )

    val editor: EditorState<Event> = form.editor

    /** Händelsetyperna ur Listor (SET-9) – följer listan medan formuläret är öppet. */
    val eventOptions: StateFlow<List<Option>> = options.choices(OptionKind.EVENT, viewModelScope)

    @AssistedFactory
    interface Factory {
        fun create(id: String?, date: LocalDate?): EventEditViewModel
    }
}

@Composable
fun EventEditRoute(id: String?, date: LocalDate?, onClose: () -> Unit) {
    val viewModel = hiltViewModel<EventEditViewModel, EventEditViewModel.Factory> { it.create(id, date) }
    val eventOptions by viewModel.eventOptions.collectAsStateWithLifecycle()
    EventEditScreen(rememberEntryForm(viewModel.form), onClose, eventOptions)
}

/**
 * Händelseformuläret på `EntityEditScreen` (HAN-1, SET-9, NFR-10–12): typen – stjärnmärkta som chips och övriga
 * under "Fler typer" – dag och tid, svårighetsgraden 0–10 (högre är värre), varaktigheten, möjliga triggers,
 * åtgärder/medicinering och anteckningen. En befintlig kan raderas i menyn, efter bekräftelse som namnger den lagrade
 * posten (HIST-5).
 */
@Composable
fun EventEditScreen(form: EntryForm<Event>, onClose: () -> Unit, eventOptions: List<Option> = emptyList()) {
    val state = form.state
    val e = form.value
    val types = remember(eventOptions, e.optionId) { typeChoices(eventOptions, OptionKind.EVENT, e.optionId) }
    val messages = state.errors.mapValues { stringResource(it.value) }
    val error: (String) -> String? = messages::get
    EntityEditScreen(
        title = stringResource(if (form.isNew) R.string.event_new else R.string.event_edit),
        state = state,
        effects = form.effects,
        onSave = { form.onEvent(EntryEditEvent.Save) },
        onClose = onClose,
        delete = if (form.isNew) {
            null
        } else {
            val shown = form.stored ?: e
            val typeName = eventOptions.firstOrNull { it.id == shown.optionId }?.name ?: stringResource(R.string.log_event)
            entryDeleteAction(R.string.diary_subject_event, typeName, shown.date, shown.time) { form.onEvent(EntryEditEvent.Delete) }
        },
        onRetry = { form.onEvent(EntryEditEvent.Retry) },
        formError = if (state.hasErrorOutside(SHOWN_ERRORS)) stringResource(R.string.form_not_savable) else null,
    ) {
        TypeChoiceField(types, e.optionId, { id -> form.change(EventField.TYPE) { it.copy(optionId = id) } }, error = error(EventField.TYPE))
        e.date?.let { date ->
            DateTimeRow(date, e.time ?: LocalTime(0, 0), { day -> form.change { it.copy(date = day) } }, { time -> form.change { it.copy(time = time) } })
        }
        AppCard {
            ValueSlider(
                stringResource(R.string.event_severity),
                e.severity,
                { value -> form.change { it.copy(severity = value) } },
                valueRange = DocumentRules.SCORE,
                higherIsBetter = false,
            )
        }
        DurationRow(e.durationMinutes, { minutes -> form.change { it.copy(durationMinutes = minutes) } }, label = stringResource(R.string.event_duration))
        AppTextField(
            e.triggers.orEmpty(),
            { text -> form.change(EventField.TRIGGERS) { it.copy(triggers = text.ifEmpty { null }) } },
            stringResource(R.string.event_triggers),
            error = error(EventField.TRIGGERS),
            helper = stringResource(R.string.event_triggers_hint),
            singleLine = false,
        )
        AppTextField(
            e.actions.orEmpty(),
            { text -> form.change(EventField.ACTIONS) { it.copy(actions = text.ifEmpty { null }) } },
            stringResource(R.string.event_actions),
            error = error(EventField.ACTIONS),
            helper = stringResource(R.string.event_actions_hint),
            singleLine = false,
        )
        AppCard {
            NoteField(e.note.orEmpty(), { note -> form.change(EventField.NOTE) { it.copy(note = note.ifEmpty { null }) } }, error = error(EventField.NOTE))
        }
    }
}
