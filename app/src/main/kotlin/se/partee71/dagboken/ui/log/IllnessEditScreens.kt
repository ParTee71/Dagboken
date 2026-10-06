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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.OTHER_SYMPTOM_ID
import se.partee71.dagboken.core.engine.illnessDay
import se.partee71.dagboken.core.engine.symptomChoices
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.schema.CheckinCodec
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.IllnessEpisodeCodec
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.creatingStore
import se.partee71.dagboken.ui.common.nowInMinutes
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EntryEditEvent
import se.partee71.dagboken.ui.common.EntryEditor
import se.partee71.dagboken.ui.common.EntryForm
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.dagboken.ui.common.Validator
import se.partee71.dagboken.ui.common.choices
import se.partee71.dagboken.ui.common.entryDeleteAction
import se.partee71.dagboken.ui.common.hasErrorOutside
import se.partee71.dagboken.ui.common.nonBlank
import se.partee71.dagboken.ui.common.rememberEntryForm
import se.partee71.dagboken.ui.common.rulesValidator
import se.partee71.dagboken.ui.common.title
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppTextField
import se.partee71.dagboken.ui.components.DateField
import se.partee71.dagboken.ui.components.DateTimeRow
import se.partee71.dagboken.ui.components.EntityEditScreen
import se.partee71.dagboken.ui.components.InfoPill
import se.partee71.dagboken.ui.components.ItemRow
import se.partee71.dagboken.ui.components.NoteField
import se.partee71.dagboken.ui.components.SymptomLogCard
import se.partee71.dagboken.ui.components.ValueSlider
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.Tone

// Sjukdom från plusknappen och Idag (SJ-1, SJ-2, SJ-3, SJ-8, SJ-11): en ny episod med sin första incheckning, och
// incheckningen under en episod som finns.

/** Fälten i formulären – codecarnas namn, så att rules-felen ([rulesValidator]) hamnar på rätt fält. */
object IllnessField {
    const val TYPE = "type"
    const val NOTE = "note"
}

/** En ny episod och dess första incheckning – det formuläret "Ny sjukdomsepisod" sparar (SJ-1, SJ-2, som 3.x). */
data class EpisodeStart(val episode: IllnessEpisode, val checkin: Checkin)

/** SJ-1: typen krävs; allt annat – i episoden och incheckningen – som rules. */
val episodeStartValidator = Validator<EpisodeStart> { start ->
    val episode = rulesValidator(CollectionNames.ILLNESS_EPISODES, IllnessEpisodeCodec) { e ->
        if (e.type.isBlank()) mapOf(IllnessField.TYPE to R.string.illness_type_missing) else emptyMap()
    }
    episode.validate(start.episode) + checkinValidator.validate(start.checkin)
}

/** SJ-2: som rules (svårighetsgraden 0–10, symptomen, anteckningens tak). */
val checkinValidator = rulesValidator(CollectionNames.CHECKINS, CheckinCodec)

/** Det som sparas: trimmad typ och en tom anteckning som ingen. */
internal fun EpisodeStart.cleaned(): EpisodeStart = copy(episode = episode.copy(type = episode.type.trim(), note = episode.note?.trim().nonBlank()))

internal fun Checkin.cleaned(): Checkin = copy(note = note?.trim().nonBlank())

/** Fälten som formulären visar ett fel vid; ett fel på något annat visas överst (`EntityEditScreen(formError)`). */
private val SHOWN_ERRORS = setOf(IllnessField.TYPE, IllnessField.NOTE)

/**
 * Ny sjukdomsepisod (SJ-1, SJ-2, SJ-3, SJ-8, NAV-10) som börjar [date] (den dag Idag visar, `null` = idag): typen,
 * startdatumet, svårighetsgraden (förval 5) och symptomen för den första incheckningen – på startdagen kl. nu, som
 * 3.x – och episodens anteckning. Sparad med `IllnessRepository.startEpisode` (episoden först).
 */
@HiltViewModel(assistedFactory = EpisodeNewViewModel.Factory::class)
class EpisodeNewViewModel @AssistedInject constructor(
    illnesses: IllnessRepository,
    options: OptionsRepository,
    clock: Clock,
    zone: Provider<TimeZone>,
    @Assisted date: LocalDate?,
) : ViewModel() {
    val form = EntryEditor(
        viewModelScope,
        creatingStore<EpisodeStart> { illnesses.startEpisode(it.episode, it.checkin) },
        id = null,
        episodeStartValidator,
        placeholder = EpisodeStart(IllnessEpisode(""), Checkin("")),
        create = {
            val now = clock.nowInMinutes(zone.get())
            val day = date ?: now.date
            val episode = illnesses.newEpisode(day)
            EpisodeStart(episode, illnesses.newCheckin(episode.id, day, now.time))
        },
        clean = { _, value -> value.cleaned() },
    )

    val editor: EditorState<EpisodeStart> = form.editor

    /** Symptomen ur Listor (SJ-3) – följer listan medan formuläret är öppet. */
    val symptomOptions: StateFlow<List<Option>> = options.choices(OptionKind.SYMPTOM, viewModelScope)

    @AssistedFactory
    interface Factory {
        fun create(date: LocalDate?): EpisodeNewViewModel
    }
}

@Composable
fun EpisodeNewRoute(date: LocalDate?, onClose: () -> Unit) {
    val viewModel = hiltViewModel<EpisodeNewViewModel, EpisodeNewViewModel.Factory> { it.create(date) }
    val symptomOptions by viewModel.symptomOptions.collectAsStateWithLifecycle()
    EpisodeNewScreen(rememberEntryForm(viewModel.form), onClose, symptomOptions)
}

/** "Ny sjukdomsepisod" på `EntityEditScreen` (SJ-1, SJ-2, SJ-3, SJ-8, NFR-10–12). */
@Composable
fun EpisodeNewScreen(form: EntryForm<EpisodeStart>, onClose: () -> Unit, symptomOptions: List<Option> = emptyList()) {
    val state = form.state
    val start = form.value
    val messages = state.errors.mapValues { stringResource(it.value) }
    EntityEditScreen(
        title = stringResource(R.string.illness_new),
        state = state,
        effects = form.effects,
        onSave = { form.onEvent(EntryEditEvent.Save) },
        onClose = onClose,
        onRetry = { form.onEvent(EntryEditEvent.Retry) },
        formError = if (state.hasErrorOutside(SHOWN_ERRORS)) stringResource(R.string.form_not_savable) else null,
    ) {
        AppTextField(
            start.episode.type,
            { type -> form.change(IllnessField.TYPE) { it.copy(episode = it.episode.copy(type = type)) } },
            stringResource(R.string.entry_type),
            error = messages[IllnessField.TYPE],
            helper = stringResource(R.string.illness_type_help),
        )
        start.episode.start?.let { day ->
            // Den första incheckningen gäller startdagen (som 3.x).
            DateField(stringResource(R.string.prescription_start), day, { date -> form.change { it.copy(episode = it.episode.copy(start = date), checkin = it.checkin.copy(date = date)) } })
        }
        CheckinValues(start.checkin, symptomOptions, stored = emptyList()) { change -> form.change { it.copy(checkin = change(it.checkin)) } }
        AppCard {
            NoteField(
                start.episode.note.orEmpty(),
                { note -> form.change(IllnessField.NOTE) { it.copy(episode = it.episode.copy(note = note.ifEmpty { null })) } },
                error = messages[IllnessField.NOTE],
            )
        }
    }
}

/**
 * Ny incheckning ([id] = `null`) under episoden [episodeId] mot [date] (`null` = idag) kl. nu med svårighetsgraden 5
 * (SJ-2) – bara under en episod som finns (annars läsfel, och rules kräver den) – eller en befintlig (SJ-11): dag,
 * klockslag, svårighetsgrad, symptom och anteckning. Episoden visas överst ([episode]).
 */
@HiltViewModel(assistedFactory = CheckinEditViewModel.Factory::class)
class CheckinEditViewModel @AssistedInject constructor(
    illnesses: IllnessRepository,
    options: OptionsRepository,
    clock: Clock,
    zone: Provider<TimeZone>,
    @Assisted("episodeId") episodeId: String,
    @Assisted("id") id: String?,
    @Assisted date: LocalDate?,
) : ViewModel() {
    val form = EntryEditor(
        viewModelScope,
        illnesses.checkins(episodeId),
        id,
        checkinValidator,
        placeholder = Checkin(""),
        create = {
            illnesses.getEpisode(episodeId).getOrThrow() ?: throw DataError.NotFound
            val now = clock.nowInMinutes(zone.get())
            illnesses.newCheckin(episodeId, date ?: now.date, now.time)
        },
        clean = { _, value -> value.cleaned() },
    )

    val editor: EditorState<Checkin> = form.editor

    /** Episoden incheckningen hör till (typ och dag N) – följer den medan formuläret är öppet. */
    val episode: StateFlow<IllnessEpisode?> = illnesses.observeEpisodes().withFallback(emptyList())
        .map { episodes -> episodes.firstOrNull { it.id == episodeId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Symptomen ur Listor (SJ-3) – följer listan medan formuläret är öppet. */
    val symptomOptions: StateFlow<List<Option>> = options.choices(OptionKind.SYMPTOM, viewModelScope)

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("episodeId") episodeId: String, @Assisted("id") id: String?, date: LocalDate?): CheckinEditViewModel
    }
}

@Composable
fun CheckinEditRoute(episodeId: String, id: String?, date: LocalDate?, onClose: () -> Unit) {
    val viewModel = hiltViewModel<CheckinEditViewModel, CheckinEditViewModel.Factory> { it.create(episodeId, id, date) }
    val episode by viewModel.episode.collectAsStateWithLifecycle()
    val symptomOptions by viewModel.symptomOptions.collectAsStateWithLifecycle()
    CheckinEditScreen(rememberEntryForm(viewModel.form), onClose, episode, symptomOptions)
}

/**
 * Incheckningen på `EntityEditScreen` (SJ-2, SJ-3, SJ-8, SJ-11, NFR-10–12): episoden med "Dag N" (som Idags
 * pågående sjukdom, HEM-12), dag och klockslag, svårighetsgraden 0–10, symptomen och anteckningen. En befintlig kan
 * raderas i menyn, efter bekräftelse som namnger den lagrade posten (HIST-5).
 */
@Composable
fun CheckinEditScreen(form: EntryForm<Checkin>, onClose: () -> Unit, episode: IllnessEpisode?, symptomOptions: List<Option> = emptyList()) {
    val state = form.state
    val c = form.value
    val messages = state.errors.mapValues { stringResource(it.value) }
    val episodeTitle = episode?.title() ?: stringResource(R.string.log_illness)
    EntityEditScreen(
        title = stringResource(if (form.isNew) R.string.checkin_new else R.string.checkin_edit),
        state = state,
        effects = form.effects,
        onSave = { form.onEvent(EntryEditEvent.Save) },
        onClose = onClose,
        delete = if (form.isNew) {
            null
        } else {
            val shown = form.stored ?: c
            entryDeleteAction(R.string.diary_subject_checkin, episodeTitle, shown.date, shown.time) { form.onEvent(EntryEditEvent.Delete) }
        },
        onRetry = { form.onEvent(EntryEditEvent.Retry) },
        formError = if (state.hasErrorOutside(SHOWN_ERRORS)) stringResource(R.string.form_not_savable) else null,
    ) {
        AppCard {
            val day = c.date?.let { illnessDay(episode?.start, it) }
            ItemRow(
                episodeTitle,
                trailing = day?.let { { InfoPill(stringResource(R.string.today_illness_day_format, it), tone = Tone.Warning) } },
                accent = AppColors.tone(Tone.Warning).content,
            )
        }
        c.date?.let { date ->
            DateTimeRow(date, c.time ?: LocalTime(0, 0), { day -> form.change { it.copy(date = day) } }, { time -> form.change { it.copy(time = time) } })
        }
        CheckinValues(c, symptomOptions, form.stored?.symptoms.orEmpty()) { change -> form.change(transform = change) }
        AppCard {
            NoteField(c.note.orEmpty(), { note -> form.change(IllnessField.NOTE) { it.copy(note = note.ifEmpty { null }) } }, error = messages[IllnessField.NOTE])
        }
    }
}

/** Svårighetsgraden 0–10 (högre är värre) och symptomen (SJ-2, SJ-3) – en incheckning, ny eller den första i en episod. */
@Composable
private fun CheckinValues(checkin: Checkin, symptomOptions: List<Option>, stored: List<SymptomScore>, change: ((Checkin) -> Checkin) -> Unit) {
    AppCard {
        ValueSlider(
            stringResource(R.string.event_severity),
            checkin.severity,
            { value -> change { it.copy(severity = value) } },
            valueRange = DocumentRules.SCORE,
            higherIsBetter = false,
        )
    }
    val symptoms = remember(symptomOptions, stored) { symptomChoices(symptomOptions, stored) }
    SymptomLogCard(symptoms, checkin.symptoms, { scores -> change { it.copy(symptoms = scores) } }, otherOptionId = OTHER_SYMPTOM_ID)
}
