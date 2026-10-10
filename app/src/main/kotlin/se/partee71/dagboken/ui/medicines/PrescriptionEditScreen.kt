package se.partee71.dagboken.ui.medicines

import se.partee71.dagboken.ui.components.MEDICINE_UNITS
import se.partee71.dagboken.ui.components.UnitChoice
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.MIN_INTERVAL_DAYS
import se.partee71.dagboken.core.engine.PeriodChoice
import se.partee71.dagboken.core.engine.PrescriptionError
import se.partee71.dagboken.core.engine.PrescriptionProblem
import se.partee71.dagboken.core.engine.RepeatChoice
import se.partee71.dagboken.core.engine.choice
import se.partee71.dagboken.core.engine.chosenDays
import se.partee71.dagboken.core.engine.extendedFrom
import se.partee71.dagboken.core.engine.hasExpiredOn
import se.partee71.dagboken.core.engine.intervalDaysFrom
import se.partee71.dagboken.core.engine.lengthDays
import se.partee71.dagboken.core.engine.nextBoostDefaults
import se.partee71.dagboken.core.engine.problem
import se.partee71.dagboken.core.engine.totalWith
import se.partee71.dagboken.core.engine.withChoice
import se.partee71.dagboken.core.engine.withDays
import se.partee71.dagboken.core.engine.withFormStart
import se.partee71.dagboken.core.engine.withLength
import se.partee71.dagboken.core.engine.withStart
import se.partee71.dagboken.core.medicine.MedicineEntry
import se.partee71.dagboken.core.medicine.MedicineMatch
import se.partee71.dagboken.core.medicine.filledFrom
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.MedicineForm
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.medicines.MedicineRepository
import se.partee71.dagboken.di.DefaultDispatcher
import se.partee71.dagboken.data.repository.PrescriptionRepository
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.EditorLoader
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.Validator
import se.partee71.dagboken.ui.common.datesText
import se.partee71.dagboken.ui.common.doseText
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.common.repeatText
import se.partee71.dagboken.ui.common.weekdaysText
import se.partee71.dagboken.ui.components.AppButton
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppFilterChip
import se.partee71.dagboken.ui.components.AppIconButton
import se.partee71.dagboken.ui.components.AppSegmentedChoice
import se.partee71.dagboken.ui.components.AppTextField
import se.partee71.dagboken.ui.components.ButtonVariant
import se.partee71.dagboken.ui.components.ChipRow
import se.partee71.dagboken.ui.components.DateField
import se.partee71.dagboken.ui.components.EntityEditScreen
import se.partee71.dagboken.ui.components.FieldError
import se.partee71.dagboken.ui.components.LabeledGroup
import se.partee71.dagboken.ui.components.NoteField
import se.partee71.dagboken.ui.components.QuantityStepper
import se.partee71.dagboken.ui.components.SwitchRow
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/** Fälten i receptformuläret – där ett fel visas. */
object PrescriptionField {
    const val NAME = "name"
    const val DOSE = "dose"
    const val SLOTS = "slots"
    const val DAYS = "days"
    const val PERIOD_END = "periodEnd"

    /** Dosen på höjningen på plats [index] i `boosts`. */
    fun boostDose(index: Int) = "boost:$index:dose"

    /** Dagarna (start och slut) på höjningen på plats [index] i `boosts`. */
    fun boostDates(index: Int) = "boost:$index:dates"
}

/** Ett nytt recept (REC-1, REC-4, REC-7): mg, Morgon, varje dag och tills vidare från [today] – alltid med start. */
fun newPrescription(today: LocalDate) =
    Prescription(id = "", unit = MEDICINE_UNITS.first(), slots = listOf(Slot.MORNING), schedule = Schedule.Repeating(), period = Period(today))

/**
 * Namn och minst en tidpunkt krävs (REC-1, REC-6), Veckodagar kräver minst en dag (REC-3), och
 * perioden och höjningarna följer `:core`s regler i deras ordning – det första felet visas under fältet
 * det gäller (REC-7, REC-9).
 */
val prescriptionValidator = Validator<Prescription> { p ->
    buildMap {
        if (p.name.isBlank()) put(PrescriptionField.NAME, R.string.option_name_missing)
        // Tidpunkter som inte kan visas går inte att ändra här, och hindrar därför aldrig att receptet sparas.
        if (!p.hasUnknownSlots && p.slots.none { it in Slot.SCHEDULED }) put(PrescriptionField.SLOTS, R.string.prescription_slots_missing)
        val schedule = p.schedule
        if (schedule is Schedule.Repeating && schedule.repeat == Repeat.CUSTOM && schedule.days.isEmpty()) {
            put(PrescriptionField.DAYS, R.string.prescription_days_missing)
        }
        p.problem()?.let { put(it.field(), it.error.message()) }
    }
}

/** Fältet där [PrescriptionProblem] visas: perioden, grunddosen eller höjningen det gäller. */
private fun PrescriptionProblem.field(): String {
    val index = boostIndex
    return when {
        index == null && error == PrescriptionError.BASE_DOSE_NOT_NUMERIC -> PrescriptionField.DOSE
        index == null -> PrescriptionField.PERIOD_END
        error == PrescriptionError.BOOST_WITHOUT_DOSE || error == PrescriptionError.BOOST_NOT_POSITIVE -> PrescriptionField.boostDose(index)
        else -> PrescriptionField.boostDates(index)
    }
}

@StringRes
private fun PrescriptionError.message(): Int = when (this) {
    PrescriptionError.END_BEFORE_START -> R.string.prescription_error_end_before_start
    PrescriptionError.BOOST_WITHOUT_DOSE -> R.string.prescription_error_boost_without_dose
    PrescriptionError.BASE_DOSE_NOT_NUMERIC -> R.string.prescription_error_base_dose
    PrescriptionError.BOOST_NOT_POSITIVE -> R.string.prescription_error_boost_not_positive
    PrescriptionError.BOOST_OUTSIDE_PERIOD -> R.string.prescription_error_boost_outside
    PrescriptionError.BOOST_END_BEFORE_START -> R.string.prescription_error_boost_end_before_start
    PrescriptionError.BOOSTS_OVERLAP -> R.string.prescription_error_boosts_overlap
}

sealed interface PrescriptionEditEvent {
    data class NameChanged(val name: String) : PrescriptionEditEvent

    data class StrengthChanged(val strength: String) : PrescriptionEditEvent

    data class FormChosen(val form: MedicineForm?) : PrescriptionEditEvent

    /** Ett val bland namnförslagen (REC-14): fyller i namn, styrka, form och enhet, men inte antalet. */
    data class MedicineChosen(val entry: MedicineEntry) : PrescriptionEditEvent

    data class DoseChanged(val dose: String) : PrescriptionEditEvent

    data class UnitChanged(val unit: String) : PrescriptionEditEvent

    data class SlotToggled(val slot: Slot) : PrescriptionEditEvent

    data class RepeatChosen(val choice: RepeatChoice) : PrescriptionEditEvent

    data class DayToggled(val day: DayOfWeek) : PrescriptionEditEvent

    data class IntervalChanged(val days: Int) : PrescriptionEditEvent

    data class StartChanged(val date: LocalDate) : PrescriptionEditEvent

    data class PeriodChosen(val choice: PeriodChoice) : PrescriptionEditEvent

    data class LengthChanged(val days: Int) : PrescriptionEditEvent

    data class EndChanged(val date: LocalDate) : PrescriptionEditEvent

    data object BoostAdded : PrescriptionEditEvent

    data class BoostRemoved(val index: Int) : PrescriptionEditEvent

    data class BoostDoseChanged(val index: Int, val dose: String) : PrescriptionEditEvent

    data class BoostStartChanged(val index: Int, val date: LocalDate) : PrescriptionEditEvent

    /** [date] `null` = till periodens slut (REC-9). */
    data class BoostEndChanged(val index: Int, val date: LocalDate?) : PrescriptionEditEvent

    data class NoteChanged(val note: String) : PrescriptionEditEvent

    data class ActiveChanged(val active: Boolean) : PrescriptionEditEvent

    data object Save : PrescriptionEditEvent

    data object Retry : PrescriptionEditEvent
}

/**
 * Nytt eller ändrat recept (REC-1…REC-10; [id] = `null` för nytt). Med [extend] ("Förläng och
 * aktivera", MEDF-5) öppnas ett avslutat recept med perioden förlängd från idag och Aktiv på – som en
 * ändring, så att det sparas som vanligt. Hur upprepningen och perioden blir val, förlängningen, reglerna
 * och förvalen för en höjning kommer från `:core/engine`; sparningen skriver bara det ändrade och synkar
 * sedan doserna (`PrescriptionRepository.save`).
 */
@HiltViewModel(assistedFactory = PrescriptionEditViewModel.Factory::class)
class PrescriptionEditViewModel @AssistedInject constructor(
    private val prescriptions: PrescriptionRepository,
    medicines: MedicineRepository,
    @DefaultDispatcher computation: CoroutineDispatcher,
    clock: Clock,
    zone: Provider<TimeZone>,
    @Assisted private val id: String?,
    @Assisted private val extend: Boolean,
) : ViewModel() {
    /** Dagen formuläret räknar förval från – idag när det öppnades. */
    private val today: LocalDate = clock.todayIn(zone.get())

    /** Ett nytt recepts id, från samlingen: samma vid ett nytt försök efter ett fel. */
    private val newId: String? = if (id == null) prescriptions.newId() else null

    /** Ett nytt recepts `createdAt`, satt en gång – ett nytt försök efter ett fel skriver inte om den. */
    private val createdAt: Instant? = if (id == null) clock.now() else null

    val editor: EditorState<Prescription> = EditorState(newPrescription(today), prescriptionValidator, loading = id != null)

    /** Namnförslag ur Läkemedelsverkets lista, bara i ett nytt recept (REC-14). */
    val suggestions = MedicineSuggestions(medicines, enabled = id == null, name = editor.state.map { it.value.name }, scope = viewModelScope, computation = computation)

    private val _periodChoice = MutableStateFlow(PeriodChoice.UNTIL_FURTHER_NOTICE)

    /** Hur perioden visas – Längd och T.o.m. är samma period, så valet är bara formulärets (REC-7). */
    val periodChoice: StateFlow<PeriodChoice> = _periodChoice.asStateFlow()

    // Ett lagrat fel (t.ex. överlappande höjningar från en import) visas direkt, så att det går att rätta.
    private val loader = EditorLoader(
        editor,
        viewModelScope,
        read = id?.let { id -> { prescriptions.get(id) } },
        prepare = { stored -> stored.forForm().also { _periodChoice.value = it.period.choice() } },
        showInvalid = true,
        // Förlängningen görs efter varje läsning, också efter "Försök igen", som en ändring som kan sparas.
        afterLoad = if (extend) ({ extendFromToday() }) else null,
    )

    /** Receptet som det är lagrat; `null` för ett nytt eller innan det lästs. */
    val stored: StateFlow<Prescription?> = loader.stored

    val isNew: Boolean get() = id == null

    /**
     * MEDF-5: förlängningen görs bara på ett recept som fortfarande är avslutat (perioden passerad, REC-8) –
     * har det förlängts under tiden (en annan enhet, eller länken var gammal) öppnas det som vanligt.
     */
    private fun extendFromToday() {
        if (!editor.value.hasExpiredOn(today)) return
        _periodChoice.value = PeriodChoice.LENGTH
        rules { it.extendedFrom(today) }
    }

    /** REC-9: om "Lägg till doshöjning" har en plats att föreslå. */
    fun canAddBoost(prescription: Prescription): Boolean = prescription.nextBoostDefaults("", today) != null

    fun onEvent(event: PrescriptionEditEvent) {
        when (event) {
            is PrescriptionEditEvent.NameChanged -> editor.update(PrescriptionField.NAME) { it.copy(name = event.name) }
            is PrescriptionEditEvent.StrengthChanged -> editor.update { it.copy(strength = event.strength) }
            // En okänd form från en nyare app ersätts först när användaren väljer en.
            is PrescriptionEditEvent.FormChosen -> editor.update { it.copy(form = event.form, unknownForm = null) }
            is PrescriptionEditEvent.MedicineChosen -> {
                suggestions.pick(event.entry)
                editor.update(PrescriptionField.NAME) { it.filledFrom(event.entry) }
            }
            is PrescriptionEditEvent.DoseChanged -> rules { it.copy(dose = event.dose) }
            // Höjningarna anges alltid i receptets enhet (REC-9).
            is PrescriptionEditEvent.UnitChanged -> editor.update { p -> p.copy(unit = event.unit, boosts = p.boosts.map { it.copy(unit = event.unit) }) }
            // Okända tidpunkter (eller Vid behov) visas inte och ändras aldrig härifrån – som en okänd upprepning.
            is PrescriptionEditEvent.SlotToggled -> editor.update(PrescriptionField.SLOTS) { p ->
                if (p.hasUnknownSlots) p else p.copy(slots = if (event.slot in p.slots) p.slots - event.slot else p.slots + event.slot)
            }
            is PrescriptionEditEvent.RepeatChosen -> schedule { it.withChoice(event.choice) }
            is PrescriptionEditEvent.DayToggled -> schedule { s ->
                val days = s.chosenDays()
                s.withDays(if (event.day in days) days - event.day else days + event.day)
            }
            is PrescriptionEditEvent.IntervalChanged -> schedule { it.copy(intervalDays = event.days.coerceAtLeast(MIN_INTERVAL_DAYS)) }
            is PrescriptionEditEvent.StartChanged -> rules { p ->
                p.copy(period = p.period.withStart(event.date, keepLength = _periodChoice.value == PeriodChoice.LENGTH))
            }
            is PrescriptionEditEvent.PeriodChosen -> {
                _periodChoice.value = event.choice
                rules { it.copy(period = it.period.withChoice(event.choice)) }
            }
            is PrescriptionEditEvent.LengthChanged -> rules { it.copy(period = it.period.withLength(event.days)) }
            is PrescriptionEditEvent.EndChanged -> rules { it.copy(period = it.period.copy(end = event.date)) }
            PrescriptionEditEvent.BoostAdded -> rules { p -> p.nextBoostDefaults(prescriptions.newId(), today)?.let { p.copy(boosts = p.boosts + it) } ?: p }
            is PrescriptionEditEvent.BoostRemoved -> rules { p -> p.copy(boosts = p.boosts.filterIndexed { i, _ -> i != event.index }) }
            is PrescriptionEditEvent.BoostDoseChanged -> boost(event.index) { it.copy(dose = event.dose) }
            is PrescriptionEditEvent.BoostStartChanged -> boost(event.index) { it.copy(start = event.date) }
            is PrescriptionEditEvent.BoostEndChanged -> boost(event.index) { it.copy(end = event.date) }
            is PrescriptionEditEvent.NoteChanged -> editor.update { it.copy(note = event.note.ifEmpty { null }) }
            is PrescriptionEditEvent.ActiveChanged -> editor.update { it.copy(active = event.active) }
            PrescriptionEditEvent.Save -> viewModelScope.launch { editor.save(::save) }
            PrescriptionEditEvent.Retry -> loader.retry()
        }
    }

    /**
     * En ändring som reglerna (REC-7, REC-9) prövar: grunddosen, perioden och alla höjningar räknas som
     * rörda, eftersom en ändring på ett ställe kan ge ett fel på ett annat (en höjning som hamnar utanför
     * en kortare period) – annars skulle "Spara" vara inaktiv utan att felet syntes.
     */
    private fun rules(transform: (Prescription) -> Prescription) {
        val boosts = (0..editor.value.boosts.size).flatMap { listOf(PrescriptionField.boostDose(it), PrescriptionField.boostDates(it)) }
        editor.update(PrescriptionField.DOSE, PrescriptionField.PERIOD_END, *boosts.toTypedArray(), transform = transform)
    }

    private fun boost(index: Int, transform: (Boost) -> Boost) =
        rules { p -> p.copy(boosts = p.boosts.mapIndexed { i, b -> if (i == index) transform(b) else b }) }

    /** En okänd upprepning ([Schedule.Unknown]) har inga kontroller och ändras aldrig härifrån. */
    private fun schedule(transform: (Schedule.Repeating) -> Schedule.Repeating) =
        editor.update(PrescriptionField.DAYS) { p -> (p.schedule as? Schedule.Repeating)?.let { p.copy(schedule = transform(it)) } ?: p }

    /**
     * Ett lagrat recept som formuläret visar det: alltid ett startdatum (REC-4), och inga tidpunkter är
     * Morgon (MED-4) – utom när tidpunkterna inte kan visas ([Prescription.hasUnknownSlots]); då står de kvar.
     */
    private fun Prescription.forForm(): Prescription =
        withFormStart(today).let { if (it.hasUnknownSlots) it else it.copy(slots = slots.ifEmpty { listOf(Slot.MORNING) }) }

    /** Nytt läggs till med sitt id; ett befintligt skriver bara det ändrade – aldrig ett nytt i stället för ett som inte gick att läsa. */
    private suspend fun save(prescription: Prescription): Result<Unit> {
        val trimmed = prescription.copy(
            name = prescription.name.trim(),
            strength = prescription.strength.trim(),
            dose = prescription.dose.trim(),
            note = prescription.note?.trim()?.takeIf { it.isNotEmpty() },
            boosts = prescription.boosts.map { it.copy(dose = it.dose.trim()) },
        )
        if (newId != null) return prescriptions.save(null, trimmed.copy(id = newId, createdAt = createdAt))
        val loaded = stored.value ?: return Result.failure(DataError.NotFound)
        // Jämförs mot receptet som formuläret visade det: förvalen (start, Morgon) är inga ändringar,
        // så ett migrerat recept utan start får ingen start av ett namnbyte.
        return prescriptions.save(loaded.forForm(), trimmed, extended = extend)
    }

    @AssistedFactory
    interface Factory {
        fun create(id: String?, extend: Boolean): PrescriptionEditViewModel
    }
}

@Composable
fun PrescriptionEditRoute(id: String?, extend: Boolean, onClose: () -> Unit) {
    val viewModel = hiltViewModel<PrescriptionEditViewModel, PrescriptionEditViewModel.Factory> { it.create(id, extend) }
    val state by viewModel.editor.state.collectAsStateWithLifecycle()
    val periodChoice by viewModel.periodChoice.collectAsStateWithLifecycle()
    val matches by viewModel.suggestions.matches.collectAsStateWithLifecycle()
    PrescriptionEditScreen(viewModel.isNew, state, periodChoice, viewModel.canAddBoost(state.value), viewModel.editor.effects, viewModel::onEvent, onClose, matches)
}

/**
 * Receptformuläret på `EntityEditScreen` (NFR-10), fälten i mockupens ordning: namn (med förslag ur
 * läkemedelslistan i ett nytt recept, [matches], REC-14), styrka, form, antal per dos, enhet,
 * tidpunkter, upprepning, period, doshöjningar, anteckning och Aktiv. Upprepningen och perioden är
 * ett val var, och bara det valda lägets kontroll syns.
 */
@Composable
fun PrescriptionEditScreen(
    isNew: Boolean,
    state: EditorUiState<Prescription>,
    periodChoice: PeriodChoice,
    canAddBoost: Boolean,
    effects: Flow<EditorEffect>,
    onEvent: (PrescriptionEditEvent) -> Unit,
    onClose: () -> Unit,
    matches: List<MedicineMatch> = emptyList(),
) {
    val p = state.value
    val errors: Map<String, String> = state.errors.mapValues { (_, message) -> stringResource(message) }
    val error: (String) -> String? = { field -> errors[field] }
    EntityEditScreen(
        title = stringResource(if (isNew) R.string.medicines_new_prescription else R.string.prescription_edit),
        state = state,
        effects = effects,
        onSave = { onEvent(PrescriptionEditEvent.Save) },
        onClose = onClose,
        onRetry = { onEvent(PrescriptionEditEvent.Retry) },
    ) {
        MedicineFields(
            name = p.name,
            strength = p.strength,
            form = p.form,
            suggest = isNew,
            matches = matches,
            nameError = error(PrescriptionField.NAME),
            onName = { onEvent(PrescriptionEditEvent.NameChanged(it)) },
            onPick = { onEvent(PrescriptionEditEvent.MedicineChosen(it)) },
            onStrength = { onEvent(PrescriptionEditEvent.StrengthChanged(it)) },
            onForm = { onEvent(PrescriptionEditEvent.FormChosen(it)) },
        )
        AppTextField(p.dose, { onEvent(PrescriptionEditEvent.DoseChanged(it)) }, stringResource(R.string.medicine_dose_label), error = error(PrescriptionField.DOSE))
        UnitChoice(p.unit, { onEvent(PrescriptionEditEvent.UnitChanged(it)) })
        SlotsSection(p, error(PrescriptionField.SLOTS), onEvent)
        RepeatSection(p, error(PrescriptionField.DAYS), onEvent)
        PeriodSection(p.period, periodChoice, error(PrescriptionField.PERIOD_END), onEvent)
        BoostsSection(p, canAddBoost, { index -> error(PrescriptionField.boostDose(index)) to error(PrescriptionField.boostDates(index)) }, onEvent)
        AppCard { NoteField(p.note.orEmpty(), { onEvent(PrescriptionEditEvent.NoteChanged(it)) }) }
        AppCard {
            SwitchRow(
                stringResource(R.string.prescription_active),
                p.active,
                { onEvent(PrescriptionEditEvent.ActiveChanged(it)) },
                subtitle = stringResource(R.string.prescription_active_help),
            )
        }
    }
}

/** REC-1, REC-6: tidpunkterna som flerval; tidpunkter som inte kan visas ([Prescription.hasUnknownSlots]) bara som text, som en okänd upprepning. */
@Composable
private fun SlotsSection(p: Prescription, slotsError: String?, onEvent: (PrescriptionEditEvent) -> Unit) {
    val label = stringResource(R.string.prescription_slots)
    if (p.hasUnknownSlots) {
        LabeledGroup(label) { CannotShow(stringResource(R.string.prescription_slots_unknown)) }
        return
    }
    LabeledGroup(label, helper = stringResource(R.string.prescription_slots_help), error = slotsError) {
        ChipRow {
            Slot.SCHEDULED.forEach { slot ->
                AppFilterChip(stringResource(slot.label()), selected = slot in p.slots, onClick = { onEvent(PrescriptionEditEvent.SlotToggled(slot)) })
            }
        }
    }
}

/** Ett värde formuläret inte kan visa eller ändra – det sparas som det är (DAT-10). */
@Composable
private fun CannotShow(text: String) {
    // Samma indrag som gruppens hjälptext.
    Text(text, Modifier.padding(horizontal = Spacing.l), style = AppTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** REC-2…REC-4: Varje dag · Veckodagar · Var X:e dag; en okänd upprepning visas bara som text. */
@Composable
private fun RepeatSection(p: Prescription, daysError: String?, onEvent: (PrescriptionEditEvent) -> Unit) {
    val label = stringResource(R.string.prescription_repeat)
    val schedule = p.schedule
    if (schedule !is Schedule.Repeating) {
        LabeledGroup(label) { CannotShow(stringResource(R.string.prescription_repeat_unknown)) }
        return
    }
    val choice = schedule.choice()
    val start = p.period.start
    val helper = when (choice) {
        RepeatChoice.EVERY_DAY -> stringResource(R.string.prescription_repeat_every_day_help)
        RepeatChoice.WEEKDAYS -> schedule.chosenDays().let { days ->
            if (days.isEmpty()) stringResource(R.string.prescription_days_help) else stringResource(R.string.prescription_days_chosen_help, weekdaysText(days))
        }
        RepeatChoice.INTERVAL -> start?.let {
            stringResource(R.string.prescription_interval_help, DateFormat.short(it), datesText(intervalDaysFrom(it, schedule.intervalDays)))
        }
    }
    val choices = RepeatChoice.entries
    val labels = listOf(R.string.prescription_repeat_every_day, R.string.prescription_repeat_weekdays, R.string.prescription_repeat_interval).map { stringResource(it) }
    LabeledGroup(label, helper = helper, error = daysError.takeIf { choice == RepeatChoice.WEEKDAYS }) {
        AppSegmentedChoice(labels, choices.indexOf(choice), { onEvent(PrescriptionEditEvent.RepeatChosen(choices[it])) })
        when (choice) {
            RepeatChoice.EVERY_DAY -> Unit
            RepeatChoice.WEEKDAYS -> ChipRow {
                val chosen = schedule.chosenDays()
                DayOfWeek.entries.forEach { day ->
                    val name = DateFormat.weekdayLong(day)
                    AppFilterChip(
                        DateFormat.weekdayShort(day).replaceFirstChar { it.titlecase() },
                        selected = day in chosen,
                        onClick = { onEvent(PrescriptionEditEvent.DayToggled(day)) },
                        // TalkBack läser hela namnet ("måndag"), inte förkortningen.
                        modifier = Modifier.semantics { contentDescription = name },
                    )
                }
            }
            RepeatChoice.INTERVAL -> QuantityStepper(
                schedule.intervalDays,
                { onEvent(PrescriptionEditEvent.IntervalChanged(it)) },
                stringResource(R.string.prescription_interval_label),
                range = MIN_INTERVAL_DAYS..maxOf(MAX_INTERVAL_DAYS, schedule.intervalDays),
                valueText = { days -> repeatText(Schedule.Repeating(Repeat.INTERVAL, intervalDays = days)).replaceFirstChar { it.titlecase() } },
            )
        }
    }
}

/** REC-7: startdatum och Tills vidare · Längd · T.o.m., med perioden som hjälptext. */
@Composable
private fun PeriodSection(period: Period, choice: PeriodChoice, endError: String?, onEvent: (PrescriptionEditEvent) -> Unit) {
    val end = period.end
    val length = period.lengthDays()
    val helper = when {
        choice == PeriodChoice.UNTIL_FURTHER_NOTICE || end == null -> stringResource(R.string.prescription_period_until_help)
        length != null && length >= 1 -> stringResource(R.string.prescription_period_summary, DateFormat.short(end), pluralStringResource(R.plurals.prescription_days_count, length, length))
        else -> null
    }
    val choices = PeriodChoice.entries
    val labels = listOf(R.string.prescription_period_until, R.string.prescription_period_length, R.string.prescription_period_end).map { stringResource(it) }
    // Slutet före start visas under Slutdatum under T.o.m., annars under perioden.
    LabeledGroup(stringResource(R.string.prescription_period), helper = helper, error = endError.takeIf { choice != PeriodChoice.END_DATE }) {
        DateField(stringResource(R.string.prescription_start), period.start, { onEvent(PrescriptionEditEvent.StartChanged(it)) })
        AppSegmentedChoice(labels, choices.indexOf(choice), { onEvent(PrescriptionEditEvent.PeriodChosen(choices[it])) })
        when (choice) {
            PeriodChoice.UNTIL_FURTHER_NOTICE -> Unit
            PeriodChoice.LENGTH -> {
                val days = (length ?: 1).coerceAtLeast(1)
                QuantityStepper(
                    days,
                    { onEvent(PrescriptionEditEvent.LengthChanged(it)) },
                    stringResource(R.string.prescription_length_label),
                    range = 1..maxOf(MAX_PERIOD_DAYS, days),
                    valueText = { pluralStringResource(R.plurals.prescription_days_count, it, it) },
                )
            }
            PeriodChoice.END_DATE -> DateField(stringResource(R.string.prescription_end), end, { onEvent(PrescriptionEditEvent.EndChanged(it)) }, error = endError)
        }
    }
}

/** REC-9: varje höjning som ett kort, och "Lägg till doshöjning" när det finns plats. */
@Composable
private fun BoostsSection(p: Prescription, canAdd: Boolean, errorAt: (Int) -> Pair<String?, String?>, onEvent: (PrescriptionEditEvent) -> Unit) {
    LabeledGroup(stringResource(R.string.medicines_boosts), helper = stringResource(R.string.prescription_boosts_help)) {
        p.boosts.forEachIndexed { index, boost ->
            val (doseError, datesError) = errorAt(index)
            BoostCard(p, index, boost, doseError, datesError, onEvent)
        }
        AppButton(
            stringResource(R.string.prescription_boost_add),
            { onEvent(PrescriptionEditEvent.BoostAdded) },
            variant = ButtonVariant.Text,
            icon = R.drawable.ic_add,
            enabled = canAdd,
        )
    }
}

/**
 * En höjning: dosfel under Höjning, dagfel under datumen. Varje kort har unika namn för TalkBack
 * ("Ta bort doshöjning 2", "Startdatum, doshöjning 2"), så att flera höjningar går att skilja åt.
 */
@Composable
private fun BoostCard(p: Prescription, index: Int, boost: Boost, doseError: String?, datesError: String?, onEvent: (PrescriptionEditEvent) -> Unit) {
    val which = stringResource(R.string.prescription_boost_number, index + 1)
    val startLabel = stringResource(R.string.prescription_start)
    val endLabel = stringResource(R.string.prescription_end)
    val doseLabel = stringResource(R.string.prescription_boost)
    val doseDescription = stringResource(R.string.field_in_context_format, doseLabel, which)
    val untilPeriodEnd = stringResource(R.string.prescription_boost_until_period_end)
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                AppTextField(
                    boost.dose,
                    { onEvent(PrescriptionEditEvent.BoostDoseChanged(index, it)) },
                    doseLabel,
                    Modifier.weight(1f).semantics { contentDescription = doseDescription },
                    error = doseError,
                    suffix = p.unit.ifBlank { null },
                )
                AppIconButton(R.drawable.ic_delete, stringResource(R.string.prescription_boost_remove_format, index + 1), { onEvent(PrescriptionEditEvent.BoostRemoved(index)) })
            }
            p.totalWith(boost)?.let { total ->
                Text(stringResource(R.string.prescription_boost_total, doseText(total, p.unit)), style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Under varandra: datumfältets "tis 29 sep 2026" får inte plats två i bredd på en telefon.
            DateField(startLabel, boost.start, { onEvent(PrescriptionEditEvent.BoostStartChanged(index, it)) }, context = which)
            DateField(
                endLabel,
                boost.end,
                { onEvent(PrescriptionEditEvent.BoostEndChanged(index, it)) },
                emptyLabel = untilPeriodEnd,
                onClear = { onEvent(PrescriptionEditEvent.BoostEndChanged(index, null)) },
                context = which,
            )
            datesError?.let { FieldError(it) }
        }
    }
}

/** Spannet för stegarna; ett större lagrat värde ryms alltid. */
private const val MAX_INTERVAL_DAYS = 90
private const val MAX_PERIOD_DAYS = 365
