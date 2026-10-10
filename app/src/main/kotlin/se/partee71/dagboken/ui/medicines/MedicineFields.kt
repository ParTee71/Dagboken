package se.partee71.dagboken.ui.medicines

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import se.partee71.dagboken.R
import se.partee71.dagboken.core.medicine.MedicineCatalog
import se.partee71.dagboken.core.medicine.MedicineEntry
import se.partee71.dagboken.core.medicine.MedicineMatch
import se.partee71.dagboken.core.model.MedicineForm
import se.partee71.dagboken.data.medicines.MedicineRepository
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.components.AppTextField
import se.partee71.dagboken.ui.components.ChoiceChips
import se.partee71.dagboken.ui.components.LabeledGroup
import se.partee71.dagboken.ui.components.Suggestion
import se.partee71.dagboken.ui.components.SuggestionField

/**
 * Förslagen på namnfältet i ett nytt recept eller en ny vid behov-medicin (REC-14), gemensamma för båda
 * formulären. [matches] följer [name] när [enabled] (nytt läge), tom annars; efter ett val ([pick]) visas
 * inga förslag förrän namnet ändras. Listan läses lokalt ur [medicines] – inget nätverk, inget loggas (NFR-13). Sökningen körs på [computation].
 */
class MedicineSuggestions(
    medicines: MedicineRepository,
    enabled: Boolean,
    name: Flow<String>,
    scope: CoroutineScope,
    computation: CoroutineDispatcher,
) {
    private val picked = MutableStateFlow<String?>(null)

    val matches: StateFlow<List<MedicineMatch>> = if (!enabled) {
        MutableStateFlow(emptyList())
    } else {
        combine(name.distinctUntilChanged(), picked, flow { emit(medicines.catalog()) }) { typed, pickedName, catalog: MedicineCatalog ->
            if (typed == pickedName) emptyList() else withContext(computation) { catalog.search(typed) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    /** Anropas före formuläret fylls i, så att förslagen stängs i samma stund namnet byts. */
    fun pick(entry: MedicineEntry) {
        picked.value = entry.name
    }
}

/**
 * Namn, Styrka och Form överst i recept- och vid behov-formuläret (REC-1, FAV-1, REC-14). Med [suggest] (nytt
 * läge) är namnet ett [SuggestionField] med [matches]; annars ett vanligt fält. Ingen form är vald när
 * [form] är `null` (ej angiven, eller en okänd form från en nyare app som då lämnas orörd); ett tryck på den
 * valda formen avmarkerar den.
 */
@Composable
fun MedicineFields(
    name: String,
    strength: String,
    form: MedicineForm?,
    suggest: Boolean,
    matches: List<MedicineMatch>,
    nameError: String?,
    onName: (String) -> Unit,
    onPick: (MedicineEntry) -> Unit,
    onStrength: (String) -> Unit,
    onForm: (MedicineForm?) -> Unit,
) {
    val nameLabel = stringResource(R.string.option_name)
    if (suggest) {
        SuggestionField(
            name,
            onName,
            nameLabel,
            suggestions = matches.map { Suggestion(it.entry.title, it.entry.formText, it.start until it.start + it.length) },
            onPick = { onPick(matches[it].entry) },
            footer = stringResource(R.string.medicine_lookup_source),
            error = nameError,
        )
    } else {
        AppTextField(name, onName, nameLabel, error = nameError)
    }
    AppTextField(strength, onStrength, stringResource(R.string.medicine_strength))
    LabeledGroup(stringResource(R.string.medicine_form)) {
        ChoiceChips<MedicineForm?>(
            MedicineForm.entries,
            form,
            onForm,
            label = { f -> f?.let { stringResource(it.label()) }.orEmpty() },
            onClear = { onForm(null) },
        )
    }
}
