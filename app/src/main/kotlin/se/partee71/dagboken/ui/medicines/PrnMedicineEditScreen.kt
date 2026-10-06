package se.partee71.dagboken.ui.medicines

import se.partee71.dagboken.ui.components.MEDICINE_UNITS
import se.partee71.dagboken.ui.components.UnitChoice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.PrnMedicineRepository
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.EditorLoader
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.Validator
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppTextField
import se.partee71.dagboken.ui.components.DeleteAction
import se.partee71.dagboken.ui.components.EntityEditScreen
import se.partee71.dagboken.ui.components.LabeledGroup
import se.partee71.dagboken.ui.components.NoteField
import se.partee71.dagboken.ui.components.QuantityStepper

/** Fälten i formuläret. */
object PrnField {
    const val NAME = "name"
    const val DOSE = "dose"
}

/** Spannen för stegarna, som 3.x reglage (0–24 h, 0–10 per dag); ett större lagrat värde ryms alltid. */
private const val MAX_HOURS = 24
private const val MAX_PER_DAY = 10

/** En ny vid behov-medicin: mg, minst 4 h mellan doser och ingen dagsgräns – som 3.x. */
fun newPrnMedicine() = PrnMedicine(id = "", unit = MEDICINE_UNITS.first())

/** Namn och dos måste finnas (FAV-1). */
val prnValidator = Validator<PrnMedicine> { m ->
    buildMap {
        if (m.name.isBlank()) put(PrnField.NAME, R.string.option_name_missing)
        if (m.dose.isBlank()) put(PrnField.DOSE, R.string.prn_dose_missing)
    }
}

sealed interface PrnEditEvent {
    data class Changed(val field: String?, val change: (PrnMedicine) -> PrnMedicine) : PrnEditEvent

    data object Save : PrnEditEvent

    data object Delete : PrnEditEvent

    data object Retry : PrnEditEvent
}

/**
 * Ny eller ändrad vid behov-medicin (FAV-1, FAV-4, FAV-5, MEDF-4; [id] = `null` för ny). Formuläret
 * redigerar modellen själv, så stjärnan (FAV-2), tidpunkten och dispenseringstiden (FAV-7) följer med
 * oförändrade – och vid sparning skrivs bara de fält som ändrats (`PrnMedicineRepository.save`).
 */
@HiltViewModel(assistedFactory = PrnMedicineEditViewModel.Factory::class)
class PrnMedicineEditViewModel @AssistedInject constructor(
    private val medicines: PrnMedicineRepository,
    @Assisted private val id: String?,
) : ViewModel() {
    val editor: EditorState<PrnMedicine> = EditorState(newPrnMedicine(), prnValidator, loading = id != null)

    private val loader = EditorLoader(editor, viewModelScope, read = id?.let { id -> { medicines.get(id) } })

    /** Medicinen som den är lagrad; `null` för en ny eller innan den lästs. */
    val stored: StateFlow<PrnMedicine?> = loader.stored

    val isNew: Boolean get() = id == null

    fun onEvent(event: PrnEditEvent) {
        when (event) {
            is PrnEditEvent.Changed -> if (event.field == null) editor.update(transform = event.change) else editor.update(event.field, transform = event.change)
            PrnEditEvent.Save -> viewModelScope.launch { editor.save(::save) }
            PrnEditEvent.Delete -> id?.let { id -> viewModelScope.launch { editor.run { medicines.delete(id) } } }
            PrnEditEvent.Retry -> loader.retry()
        }
    }

    /** Ny läggs till; en befintlig skriver bara det ändrade – aldrig en ny i stället för en som inte gick att läsa. */
    private suspend fun save(medicine: PrnMedicine): Result<Unit> {
        val trimmed = medicine.copy(name = medicine.name.trim(), dose = medicine.dose.trim(), note = medicine.note?.trim()?.takeIf { it.isNotEmpty() })
        if (id == null) return medicines.add(trimmed)
        val loaded = stored.value ?: return Result.failure(DataError.NotFound)
        return medicines.save(loaded, trimmed)
    }

    @AssistedFactory
    interface Factory {
        fun create(id: String?): PrnMedicineEditViewModel
    }
}

@Composable
fun PrnMedicineEditRoute(id: String?, onClose: () -> Unit) {
    val viewModel = hiltViewModel<PrnMedicineEditViewModel, PrnMedicineEditViewModel.Factory> { it.create(id) }
    val state by viewModel.editor.state.collectAsStateWithLifecycle()
    PrnMedicineEditScreen(viewModel.isNew, state, viewModel.editor.effects, viewModel::onEvent, onClose)
}

/**
 * Vid behov-formuläret på `EntityEditScreen` (NFR-10): namn, dos, enhet som val (en lagrad enhet utanför
 * listan står kvar som val), minsta tid mellan doser och högsta antal per dag med stegare (0 = ingen
 * spärr respektive obegränsat) och anteckningen. En befintlig kan tas bort i menyn, efter bekräftelse.
 */
@Composable
fun PrnMedicineEditScreen(
    isNew: Boolean,
    state: EditorUiState<PrnMedicine>,
    effects: Flow<EditorEffect>,
    onEvent: (PrnEditEvent) -> Unit,
    onClose: () -> Unit,
) {
    val m = state.value
    val change: (String?, (PrnMedicine) -> PrnMedicine) -> Unit = { field, transform -> onEvent(PrnEditEvent.Changed(field, transform)) }
    EntityEditScreen(
        title = stringResource(if (isNew) R.string.medicines_new_prn else R.string.prn_edit),
        state = state,
        effects = effects,
        onSave = { onEvent(PrnEditEvent.Save) },
        onClose = onClose,
        delete = if (isNew) {
            null
        } else {
            DeleteAction(stringResource(R.string.delete_named_title, m.name), stringResource(R.string.prn_delete_message)) { onEvent(PrnEditEvent.Delete) }
        },
        onRetry = { onEvent(PrnEditEvent.Retry) },
    ) {
        AppTextField(
            m.name,
            { name -> change(PrnField.NAME) { it.copy(name = name) } },
            stringResource(R.string.option_name),
            error = state.errorFor(PrnField.NAME)?.let { stringResource(it) },
        )
        AppTextField(
            m.dose,
            { dose -> change(PrnField.DOSE) { it.copy(dose = dose) } },
            stringResource(R.string.dose_label),
            error = state.errorFor(PrnField.DOSE)?.let { stringResource(it) },
        )
        UnitChoice(m.unit, { unit -> change(null) { it.copy(unit = unit) } })
        LabeledGroup(stringResource(R.string.prn_min_hours), helper = stringResource(R.string.prn_min_hours_help)) {
            QuantityStepper(
                m.minHoursBetween,
                { hours -> change(null) { it.copy(minHoursBetween = hours) } },
                stringResource(R.string.prn_min_hours_label),
                range = 0..maxOf(MAX_HOURS, m.minHoursBetween),
                valueText = { if (it <= 0) stringResource(R.string.prn_no_cooldown) else stringResource(R.string.prn_hours_value, it) },
            )
        }
        LabeledGroup(stringResource(R.string.prn_max_per_day), helper = stringResource(R.string.prn_max_per_day_help)) {
            QuantityStepper(
                m.maxPerDay,
                { max -> change(null) { it.copy(maxPerDay = max) } },
                stringResource(R.string.prn_max_per_day_label),
                range = 0..maxOf(MAX_PER_DAY, m.maxPerDay),
                valueText = { if (it <= 0) stringResource(R.string.prn_unlimited) else it.toString() },
            )
        }
        AppCard { NoteField(m.note.orEmpty(), { note -> change(null) { it.copy(note = note.ifEmpty { null }) } }) }
    }
}
