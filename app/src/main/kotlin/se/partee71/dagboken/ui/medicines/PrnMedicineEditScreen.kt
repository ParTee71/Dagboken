package se.partee71.dagboken.ui.medicines

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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.map
import se.partee71.dagboken.R
import se.partee71.dagboken.core.medicine.MedicineEntry
import se.partee71.dagboken.core.medicine.MedicineMatch
import se.partee71.dagboken.core.medicine.filledFrom
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.data.medicines.MedicineRepository
import se.partee71.dagboken.data.repository.EntryStore
import se.partee71.dagboken.data.repository.PrnMedicineRepository
import se.partee71.dagboken.di.DefaultDispatcher
import se.partee71.dagboken.ui.common.EntryEditEvent
import se.partee71.dagboken.ui.common.EntryEditScreen
import se.partee71.dagboken.ui.common.EntryEditor
import se.partee71.dagboken.ui.common.EntryForm
import se.partee71.dagboken.ui.common.nonBlank
import se.partee71.dagboken.ui.common.rememberEntryForm
import se.partee71.dagboken.ui.components.MEDICINE_UNITS
import se.partee71.dagboken.ui.components.NoteCard
import se.partee71.dagboken.ui.components.UnitChoice
import se.partee71.ui.common.Validator
import se.partee71.ui.components.AppTextField
import se.partee71.ui.components.DeleteAction
import se.partee71.ui.components.LabeledGroup
import se.partee71.ui.components.QuantityStepper
import se.partee71.ui.components.QuantityStepperDefaults

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

/** Det som sparas: trimmad text och en tom anteckning som ingen. */
internal fun PrnMedicine.cleaned(): PrnMedicine = copy(name = name.trim(), strength = strength.trim(), dose = dose.trim(), note = note?.trim().nonBlank())

/** Fälten som formuläret visar ett fel vid – alla som [prnValidator] ger. */
private val SHOWN_ERRORS = setOf(PrnField.NAME, PrnField.DOSE)

/**
 * Ny eller ändrad vid behov-medicin (FAV-1, FAV-4, FAV-5, MEDF-4; [id] = `null` för ny) på det delade
 * postformuläret ([form]). Formuläret redigerar modellen själv, så stjärnan (FAV-2), tidpunkten och
 * dispenseringstiden (FAV-7) följer med oförändrade – och vid sparning skrivs bara de fält som ändrats
 * (`PrnMedicineRepository.save`); en ny läggs till med ett nytt id (`add`).
 */
@HiltViewModel(assistedFactory = PrnMedicineEditViewModel.Factory::class)
class PrnMedicineEditViewModel @AssistedInject constructor(
    medicines: PrnMedicineRepository,
    catalog: MedicineRepository,
    @DefaultDispatcher computation: CoroutineDispatcher,
    @Assisted id: String?
) : ViewModel() {
    val form = EntryEditor(
        viewModelScope,
        medicines.entryStore(),
        id,
        prnValidator,
        placeholder = newPrnMedicine(),
        create = { newPrnMedicine() },
        clean = { _, value -> value.cleaned() },
        // Valideringen speglar inte rules: ett lagrat fel visas först när fältet ändrats.
        showInvalid = false
    )

    /** Namnförslag ur Läkemedelsverkets lista, bara i en ny medicin (REC-14). */
    val suggestions = MedicineSuggestions(catalog, enabled = id == null, name = form.editor.state.map { it.value.name }, scope = viewModelScope, computation = computation)

    /** Ett val bland namnförslagen (REC-14): fyller i namn, styrka, form och enhet, men inte dosen. */
    fun chooseMedicine(entry: MedicineEntry) {
        suggestions.pick(entry)
        form.onEvent(EntryEditEvent.Changed(PrnField.NAME) { it.filledFrom(entry) })
    }

    @AssistedFactory
    interface Factory {
        fun create(id: String?): PrnMedicineEditViewModel
    }
}

/** Vid behov-medicinerna som formulärets [EntryStore]: en ny läggs till, en befintlig skriver bara det ändrade. */
private fun PrnMedicineRepository.entryStore(): EntryStore<PrnMedicine> = object : EntryStore<PrnMedicine> {
    override suspend fun get(id: String): Result<PrnMedicine?> = this@entryStore.get(id)

    override suspend fun save(loaded: PrnMedicine?, edited: PrnMedicine): Result<Unit> = if (loaded == null) add(edited) else this@entryStore.save(loaded, edited)

    override suspend fun delete(id: String): Result<Unit> = this@entryStore.delete(id)
}

@Composable
fun PrnMedicineEditRoute(id: String?, onClose: () -> Unit) {
    val viewModel = hiltViewModel<PrnMedicineEditViewModel, PrnMedicineEditViewModel.Factory> { it.create(id) }
    val matches by viewModel.suggestions.matches.collectAsStateWithLifecycle()
    PrnMedicineEditScreen(rememberEntryForm(viewModel.form), onClose, matches, viewModel::chooseMedicine)
}

/**
 * Vid behov-formuläret på `EntityEditScreen` (NFR-10): namn (med förslag ur läkemedelslistan i en ny medicin,
 * [matches], REC-14 – ett val går till [onPick]), styrka, form, dos, enhet som val (en lagrad enhet utanför
 * listan står kvar som val), minsta tid mellan doser och högsta antal per dag med stegare (0 = ingen
 * spärr respektive obegränsat) och anteckningen. En befintlig kan tas bort i menyn, efter bekräftelse.
 */
@Composable
fun PrnMedicineEditScreen(
    form: EntryForm<PrnMedicine>,
    onClose: () -> Unit,
    matches: List<MedicineMatch> = emptyList(),
    onPick: (MedicineEntry) -> Unit = {}
) {
    val m = form.value
    val change: (String?, (PrnMedicine) -> PrnMedicine) -> Unit = { field, transform -> form.change(field, transform) }
    EntryEditScreen(
        form,
        title = stringResource(if (form.isNew) R.string.medicines_new_prn else R.string.prn_edit),
        onClose = onClose,
        shownErrors = SHOWN_ERRORS,
        // Namnet som det står i formuläret, som förut.
        delete = { _, onConfirm -> DeleteAction(stringResource(R.string.delete_named_title, m.displayName), stringResource(R.string.prn_delete_message), onConfirm) }
    ) { error ->
        MedicineFields(
            name = m.name,
            strength = m.strength,
            form = m.form,
            suggest = form.isNew,
            matches = matches,
            nameError = error(PrnField.NAME),
            onName = { name -> change(PrnField.NAME) { it.copy(name = name) } },
            onPick = onPick,
            onStrength = { strength -> change(null) { it.copy(strength = strength) } },
            // En okänd form från en nyare app ersätts först när användaren väljer en.
            onForm = { form -> change(null) { it.copy(form = form, unknownForm = null) } }
        )
        AppTextField(
            m.dose,
            { dose -> change(PrnField.DOSE) { it.copy(dose = dose) } },
            stringResource(R.string.medicine_dose_label),
            error = error(PrnField.DOSE)
        )
        UnitChoice(m.unit, { unit -> change(null) { it.copy(unit = unit) } })
        LabeledGroup(stringResource(R.string.prn_min_hours), helper = stringResource(R.string.prn_min_hours_help)) {
            QuantityStepper(
                m.minHoursBetween,
                { hours -> change(null) { it.copy(minHoursBetween = hours) } },
                stringResource(R.string.prn_min_hours_label),
                range = 0..maxOf(MAX_HOURS, m.minHoursBetween),
                valueText = { if (it <= 0) stringResource(R.string.prn_no_cooldown) else stringResource(R.string.prn_hours_value, it) },
                buttonSize = QuantityStepperDefaults.TouchTargetButtonSize
            )
        }
        LabeledGroup(stringResource(R.string.prn_max_per_day), helper = stringResource(R.string.prn_max_per_day_help)) {
            QuantityStepper(
                m.maxPerDay,
                { max -> change(null) { it.copy(maxPerDay = max) } },
                stringResource(R.string.prn_max_per_day_label),
                range = 0..maxOf(MAX_PER_DAY, m.maxPerDay),
                valueText = { if (it <= 0) stringResource(R.string.prn_unlimited) else it.toString() },
                buttonSize = QuantityStepperDefaults.TouchTargetButtonSize
            )
        }
        NoteCard(m.note, { note -> change(null) { it.copy(note = note) } })
    }
}
