package se.partee71.dagboken.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.OTHER_SYMPTOM_ID
import se.partee71.dagboken.core.engine.symptomChoices
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.EditorSheetState
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.components.FeelingExtras
import se.partee71.dagboken.ui.components.SAVED_CONFIRMATION_MILLIS
import se.partee71.dagboken.ui.components.StepwiseScreeningForm
import se.partee71.dagboken.ui.components.StepwiseScreeningNavigation
import se.partee71.dagboken.ui.components.rememberStepwiseScreeningState
import se.partee71.ui.components.AppBottomSheet

/**
 * Måendearket (HEM-5, HEM-8b, SCR-1, SCR-2, SCR-7): `StepwiseScreeningForm` i `AppBottomSheet` med tillfällets namn – och
 * dagen när det inte är idag – som rubrik, tagen när arket öppnades ([ScreeningSheetInfo]), så att arket står sig
 * också när skärmen bakom laddar om. Symptomvalen följer [symptomOptions] medan arket är öppet – listans aktiva och
 * de arkiverade som loggen redan har; "Övrigt" får fritext. Osparade ändringar – eller en ny logg vars sparning
 * misslyckats – frågar "Släng ändringar?" innan arket stängs, och medan det sparas går det inte att stänga
 * (`canDismiss` läser formulärets aktuella läge, NFR-10); ett skrivfel visas i arket. Sparat → klart-läget ("Sparat" i knappraden, SCR-3) en kort stund, sedan döljs arket
 * animerat och stängs. Stegknapparna står i arkets fasta knapprad (NFR-21), så att de syns också med utfällda symptom. Ett enda ark för Idag, plusknappen och Dagbok ([LogSheets], händelserna till `LogViewModel`).
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
    val steps = rememberStepwiseScreeningState(choices)
    // Sparat: klart-läget en kort stund (NFR-21), sedan döljs arket animerat och stängs.
    var hide by remember(sheet) { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            delay(SAVED_CONFIRMATION_MILLIS)
            hide = true
        }
    }
    val savedText = stringResource(R.string.today_screening_saved)
    AppBottomSheet(
        title,
        onDismiss = { onEvent(LogEvent.CloseScreening) },
        error = error,
        dirty = unsaved,
        canDismiss = sheet::canDismiss,
        hide = hide,
        footer = { StepwiseScreeningNavigation(steps, { onEvent(LogEvent.SaveScreening) }, state.canSave, state.saving, done = closing, doneAnnouncement = savedText) }
    ) {
        StepwiseScreeningForm(
            energy = edited.energy,
            onEnergyChange = { onEvent(LogEvent.ChangeEnergy(it)) },
            feelings = edited.feelings,
            onFeelingsChange = { onEvent(LogEvent.ChangeFeelings(it)) },
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
            state = steps,
            navigation = false,
            feelingExtras = FeelingExtras(
                strengthOf = edited::strengthOf,
                onStronger = { onEvent(LogEvent.StrongerFeeling(it)) },
                noteOf = edited::noteOf,
                onNoteChange = { feeling, note -> onEvent(LogEvent.ChangeFeelingNote(feeling, note)) }
            )
        )
    }
}
