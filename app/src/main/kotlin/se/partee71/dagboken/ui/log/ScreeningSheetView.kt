package se.partee71.dagboken.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.OTHER_SYMPTOM_ID
import se.partee71.dagboken.core.engine.symptomChoices
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.EditorSheetState
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.components.AppBottomSheet
import se.partee71.dagboken.ui.components.StepwiseScreeningForm

/**
 * Måendearket (HEM-5, HEM-8b, SCR-1, SCR-2): `StepwiseScreeningForm` i `AppBottomSheet` med tillfällets namn – och
 * dagen när det inte är idag – som rubrik, tagen när arket öppnades ([ScreeningSheetInfo]), så att arket står sig
 * också när skärmen bakom laddar om. Symptomvalen följer [symptomOptions] medan arket är öppet – listans aktiva och
 * de arkiverade som loggen redan har; "Övrigt" får fritext. Osparade ändringar – eller en ny logg vars sparning
 * misslyckats – frågar "Släng ändringar?" innan arket stängs, och medan det sparas går det inte att stänga
 * (`canDismiss` läser formulärets aktuella läge, NFR-10); ett skrivfel visas i arket. Sparat → arket döljs
 * animerat och stängs. Ett enda ark för Idag, plusknappen och Dagbok ([LogSheets], händelserna till `LogViewModel`).
 */
@Composable
fun ScreeningSheetView(sheet: EditorSheetState<Screening, ScreeningSheetInfo>, symptomOptions: List<Option>, onEvent: (LogEvent) -> Unit) {
    val state by sheet.editor.state.collectAsStateWithLifecycle()
    val error by sheet.error.collectAsStateWithLifecycle()
    val unsaved by sheet.unsaved.collectAsStateWithLifecycle()
    val closing by sheet.closing.collectAsStateWithLifecycle()
    val edited = state.value
    val info = sheet.context
    val name = info.occasion?.let { stringResource(it.label()) } ?: stringResource(R.string.log_mood)
    val title = info.date?.let { stringResource(R.string.today_screening_title_day_format, name, DateFormat.display(it)) } ?: name
    val choices = remember(symptomOptions, sheet.loaded) { symptomChoices(symptomOptions, sheet.loaded?.symptoms.orEmpty()) }
    AppBottomSheet(
        title,
        onDismiss = { onEvent(LogEvent.CloseScreening) },
        error = error,
        dirty = unsaved,
        canDismiss = sheet::canDismiss,
        hide = closing,
    ) {
        StepwiseScreeningForm(
            energy = edited.energy,
            onEnergyChange = { onEvent(LogEvent.ChangeEnergy(it)) },
            stress = edited.stress,
            onStressChange = { onEvent(LogEvent.ChangeStress(it)) },
            symptomOptions = choices,
            symptoms = edited.symptoms,
            onSymptomsChange = { onEvent(LogEvent.ChangeSymptoms(it)) },
            onSave = { onEvent(LogEvent.SaveScreening) },
            saveEnabled = state.canSave,
            saving = state.saving,
            otherOptionId = OTHER_SYMPTOM_ID,
            somatic = edited.somatic,
        )
    }
}
