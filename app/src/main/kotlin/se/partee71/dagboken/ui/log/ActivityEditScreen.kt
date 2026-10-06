package se.partee71.dagboken.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import se.partee71.dagboken.core.engine.OTHER_ACTIVITY_ID
import se.partee71.dagboken.core.engine.OTHER_SYMPTOM_ID
import se.partee71.dagboken.core.engine.symptomChoices
import se.partee71.dagboken.core.engine.typeChoices
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.schema.ActivityCodec
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.data.repository.ActivityRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.ui.common.EntryEditEvent
import se.partee71.dagboken.ui.common.EntryForm
import se.partee71.dagboken.ui.common.hasErrorOutside
import se.partee71.dagboken.ui.common.rememberEntryForm
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EntryEditor
import se.partee71.dagboken.ui.common.entryDeleteAction
import se.partee71.dagboken.ui.common.choices
import se.partee71.dagboken.ui.common.nonBlank
import se.partee71.dagboken.ui.common.rulesValidator
import se.partee71.dagboken.ui.common.scaleValueText
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppFilterChip
import se.partee71.dagboken.ui.components.AppTextField
import se.partee71.dagboken.ui.components.ChipRow
import se.partee71.dagboken.ui.components.DateTimeRow
import se.partee71.dagboken.ui.components.DurationRow
import se.partee71.dagboken.ui.components.EntityEditScreen
import se.partee71.dagboken.ui.components.Foldout
import se.partee71.dagboken.ui.components.NoteField
import se.partee71.dagboken.ui.components.SymptomLogCard
import se.partee71.dagboken.ui.components.TypeChoiceField
import se.partee71.dagboken.ui.components.ValueSlider

/** Fälten i formuläret – codecens namn, så att rules-felen ([rulesValidator]) hamnar på rätt fält. */
object ActivityField {
    const val TYPE = "optionId"
    const val DESCRIPTION = "customText"
    const val NOTE = "note"
}

/** AKT-9: en typ måste vara vald, och "Övrigt" kräver en beskrivning (AKT-2); allt annat som rules (TextLimits, intervallen). */
val activityValidator = rulesValidator(CollectionNames.ACTIVITIES, ActivityCodec) { a ->
    buildMap {
        if (a.optionId.isBlank()) put(ActivityField.TYPE, R.string.entry_type_missing)
        if (a.optionId == OTHER_ACTIVITY_ID && a.customText.isNullOrBlank()) put(ActivityField.DESCRIPTION, R.string.activity_describe_missing)
    }
}

/**
 * Det som sparas: trimmad text och en tom anteckning som ingen. Beskrivningen (`customText`) töms bara när typen i den
 * här redigeringen byts bort från "Övrigt" (eller en ny post inte är "Övrigt") – en äldre beskrivning på en annan typ
 * (från 3.x) står kvar vid en ändring som inte rör typen. [loaded] är den lästa posten (`null` = ny).
 */
internal fun Activity.cleaned(loaded: Activity?): Activity {
    val leftOther = optionId != OTHER_ACTIVITY_ID && (loaded == null || loaded.optionId == OTHER_ACTIVITY_ID)
    return copy(customText = if (leftOther) null else customText?.trim().nonBlank(), note = note?.trim().nonBlank())
}

/** Fälten som formuläret visar ett fel vid; ett fel på något annat visas överst (`EntityEditScreen(formError)`). */
private val SHOWN_ERRORS = setOf(ActivityField.TYPE, ActivityField.DESCRIPTION, ActivityField.NOTE)

/**
 * Ny eller ändrad aktivitet (AKT-1–AKT-9, AKT-11, AKT-12; [id] = `null` för ny). En ny loggas mot [date] (den dag
 * Idag visar, `null` = idag, NAV-10) kl. nu och förifylls med den senast loggade aktivitetens typ och tidsåtgång
 * (`ActivityRepository.new`). Formuläret redigerar modellen själv, så id, skapandetid och okända fält följer med,
 * och en ändrad skriver bara ändrade fält (`DatedRepository.save`) – [form] är det delade postformuläret.
 */
@HiltViewModel(assistedFactory = ActivityEditViewModel.Factory::class)
class ActivityEditViewModel @AssistedInject constructor(
    activities: ActivityRepository,
    options: OptionsRepository,
    clock: Clock,
    zone: Provider<TimeZone>,
    @Assisted id: String?,
    @Assisted date: LocalDate?,
) : ViewModel() {
    val form = EntryEditor(
        viewModelScope,
        activities,
        id,
        activityValidator,
        placeholder = Activity(""),
        create = {
            val now = clock.now().toLocalDateTime(zone.get())
            activities.new(date ?: now.date, LocalTime(now.hour, now.minute), today = now.date)
        },
        clean = { loaded, value -> value.cleaned(loaded) },
    )

    val editor: EditorState<Activity> = form.editor

    /** Aktivitetstyperna och symptomen ur Listor (AKT-1, AKT-6) – följer listorna medan formuläret är öppet. */
    val activityOptions: StateFlow<List<Option>> = options.choices(OptionKind.ACTIVITY, viewModelScope)
    val symptomOptions: StateFlow<List<Option>> = options.choices(OptionKind.SYMPTOM, viewModelScope)

    @AssistedFactory
    interface Factory {
        fun create(id: String?, date: LocalDate?): ActivityEditViewModel
    }
}

@Composable
fun ActivityEditRoute(id: String?, date: LocalDate?, onClose: () -> Unit) {
    val viewModel = hiltViewModel<ActivityEditViewModel, ActivityEditViewModel.Factory> { it.create(id, date) }
    val activityOptions by viewModel.activityOptions.collectAsStateWithLifecycle()
    val symptomOptions by viewModel.symptomOptions.collectAsStateWithLifecycle()
    ActivityEditScreen(rememberEntryForm(viewModel.form), onClose, activityOptions, symptomOptions)
}

/**
 * Aktivitetsformuläret på `EntityEditScreen` (AKT-1–AKT-9, AKT-11, NFR-10–12): dag och tid, typen – stjärnmärkta
 * som chips och övriga under "Fler typer", med "Övrigt" sist och dess beskrivning (AKT-2) – Återhämtande och
 * Energitjuv (AKT-3), tidsåtgången (AKT-7), mätvärdena energi −10…+10 och stress 0–10 ihopfällda (AKT-4, AKT-5,
 * AKT-8), symptomen (AKT-6) och anteckningen (AKT-11). En befintlig kan raderas i menyn, efter bekräftelse som
 * namnger den lagrade posten (HIST-5); symptomval som arkiverats står kvar med namn.
 */
@Composable
fun ActivityEditScreen(
    form: EntryForm<Activity>,
    onClose: () -> Unit,
    activityOptions: List<Option> = emptyList(),
    symptomOptions: List<Option> = emptyList(),
) {
    val state = form.state
    val a = form.value
    val types = remember(activityOptions, a.optionId) { typeChoices(activityOptions, OptionKind.ACTIVITY, a.optionId, OTHER_ACTIVITY_ID) }
    val otherLabel = stringResource(R.string.activity_other)
    val messages = state.errors.mapValues { stringResource(it.value) }
    val error: (String) -> String? = messages::get
    EntityEditScreen(
        title = stringResource(if (form.isNew) R.string.activity_new else R.string.activity_edit),
        state = state,
        effects = form.effects,
        onSave = { form.onEvent(EntryEditEvent.Save) },
        onClose = onClose,
        delete = if (form.isNew) {
            null
        } else {
            val shown = form.stored ?: a
            val typeName = (activityOptions.firstOrNull { it.id == shown.optionId }?.name ?: otherLabel.takeIf { shown.optionId == OTHER_ACTIVITY_ID }).orEmpty()
            entryDeleteAction(R.string.diary_subject_activity, shown.customText.nonBlank() ?: typeName, shown.date, shown.time) { form.onEvent(EntryEditEvent.Delete) }
        },
        onRetry = { form.onEvent(EntryEditEvent.Retry) },
        formError = if (state.hasErrorOutside(SHOWN_ERRORS)) stringResource(R.string.form_not_savable) else null,
    ) {
        a.date?.let { date ->
            DateTimeRow(date, a.time ?: LocalTime(0, 0), { day -> form.change { it.copy(date = day) } }, { time -> form.change { it.copy(time = time) } })
        }
        TypeChoiceField(types, a.optionId, { id -> form.change(ActivityField.TYPE) { it.copy(optionId = id) } }, error = error(ActivityField.TYPE), otherLabel = otherLabel)
        if (a.optionId == OTHER_ACTIVITY_ID) {
            AppTextField(
                a.customText.orEmpty(),
                { text -> form.change(ActivityField.DESCRIPTION) { it.copy(customText = text.ifEmpty { null }) } },
                stringResource(R.string.activity_describe),
                error = error(ActivityField.DESCRIPTION),
            )
        }
        ChipRow {
            AppFilterChip(stringResource(R.string.activity_recovering), a.recovering, onClick = { form.change { it.copy(recovering = !it.recovering) } })
            AppFilterChip(stringResource(R.string.activity_drain), a.drain, onClick = { form.change { it.copy(drain = !it.drain) } })
        }
        DurationRow(a.minutes ?: 0, { minutes -> form.change { it.copy(minutes = minutes) } })
        MetricsCard(a) { change -> form.change(transform = change) }
        val symptoms = remember(symptomOptions, form.stored) { symptomChoices(symptomOptions, form.stored?.symptoms.orEmpty()) }
        SymptomLogCard(symptoms, a.symptoms, { scores -> form.change { it.copy(symptoms = scores) } }, otherOptionId = OTHER_SYMPTOM_ID)
        AppCard {
            NoteField(a.note.orEmpty(), { note -> form.change(ActivityField.NOTE) { it.copy(note = note.ifEmpty { null }) } }, error = error(ActivityField.NOTE))
        }
    }
}

/** Mätvärdena (AKT-4, AKT-5, AKT-8): ihopfällda, med värdena i stängt läge ("Energi +3 · Stress 2"). */
@Composable
private fun MetricsCard(a: Activity, change: ((Activity) -> Activity) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val energy = stringResource(R.string.energy)
    val stress = stringResource(R.string.stress)
    val summary = stringResource(
        R.string.activity_metrics_summary_format,
        energy,
        scaleValueText(a.energy, DocumentRules.ACTIVITY_ENERGY),
        stress,
        scaleValueText(a.stress, DocumentRules.SCORE),
    )
    AppCard {
        Foldout(stringResource(R.string.activity_metrics), expanded, { expanded = !expanded }, summary = summary) {
            ValueSlider(energy, a.energy, { value -> change { it.copy(energy = value) } }, valueRange = DocumentRules.ACTIVITY_ENERGY)
            ValueSlider(stress, a.stress, { value -> change { it.copy(stress = value) } }, valueRange = DocumentRules.SCORE, higherIsBetter = false)
        }
    }
}
