package se.kodhast.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.drop
import se.kodhast.dagboken.R
import se.kodhast.dagboken.core.model.Feeling
import se.kodhast.dagboken.core.model.Option
import se.kodhast.dagboken.core.model.SymptomScore
import se.kodhast.dagboken.core.model.somatic
import se.kodhast.dagboken.ui.theme.AppTypography
import se.kodhast.ui.components.StepIndicator
import se.kodhast.ui.components.StepNavigation
import se.kodhast.ui.theme.Spacing

/**
 * Mående i steg (HEM-5, SCR-1, SCR-2, SCR-7): känslor → energi → stress → symptom, ett steg i taget med "Steg 1 av 4",
 * stegprickar och Föregående/Nästa; sista steget har Spara. Stegen är en lista ([ScreeningStep], ur
 * [rememberStepwiseScreeningState]): symptomsteget finns bara när det finns [symptomOptions]; formuläret börjar alltid på
 * första steget, känslorna. Används i arket bakom "Logga nu"; reglagen är [ValueSlider] (energi:
 * högre är bättre, stress: högre är sämre), känslorna [FeelingPicker] (inget val är ett giltigt svar) och symptomen
 * [SymptomLogCard]. Stegen går också att svepa mellan. Ett stegbyte släpper fokus (tangentbordet stängs) och visar det
 * nya steget från toppen, med "Steg X av N" synligt (#453).
 *
 * [navigation] = false: knapparna ritas inte här utan av `StepNavigation` med samma [state]`.pager` – i
 * arkets fasta knapprad (`AppBottomSheet(footer)`, NFR-21), så att de syns också när symptomen är utfällda.
 */
@Composable
fun StepwiseScreeningForm(
    energy: Int,
    onEnergyChange: (Int) -> Unit,
    feelings: List<Feeling>,
    onFeelingsChange: (List<Feeling>) -> Unit,
    stress: Int,
    onStressChange: (Int) -> Unit,
    symptomOptions: List<Option>,
    symptoms: List<SymptomScore>,
    onSymptomsChange: (List<SymptomScore>) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = true,
    saving: Boolean = false,
    otherOptionId: String? = null,
    /** Summan som visas under symptomen ([SymptomLogCard]); standard summan av [symptoms]. */
    somatic: Int = symptoms.somatic,
    state: StepwiseScreeningState = rememberStepwiseScreeningState(symptomOptions),
    navigation: Boolean = true,
    /** Styrka och kommentar per vald känsla i känslosteget (SCR-9, SCR-10); standard ingen av dem. */
    feelingExtras: FeelingExtras = FeelingExtras.None
) {
    val steps = state.steps
    val pager = state.pager
    val focus = LocalFocusManager.current
    val top = remember { BringIntoViewRequester() }
    // Ett stegbyte (knapp eller svep) släpper fokus – tangentbordet stängs – och visar det nya steget från toppen i
    // föräldern som rullar (arket), också när föregående steg var utrullat långt ner (#453).
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect {
            focus.clearFocus()
            top.bringIntoView()
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            stringResource(R.string.step_format, pager.currentPage + 1, steps.size),
            Modifier.bringIntoViewRequester(top),
            style = AppTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = Spacing.l, verticalAlignment = Alignment.Top) { page ->
            when (steps.getOrNull(page)) {
                ScreeningStep.ENERGY -> ValueSlider(stringResource(R.string.energy), energy, onEnergyChange)
                ScreeningStep.FEELINGS -> FeelingPicker(feelings, onFeelingsChange, extras = feelingExtras)
                ScreeningStep.STRESS -> ValueSlider(stringResource(R.string.stress), stress, onStressChange, higherIsBetter = false)
                ScreeningStep.SYMPTOMS -> SymptomLogCard(symptomOptions, symptoms, onSymptomsChange, otherOptionId = otherOptionId, initiallyExpanded = true, somatic = somatic)
                null -> Unit
            }
        }
        StepIndicator(steps.size, pager.currentPage)
        if (navigation) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                StepNavigation(pager, onSave, saveEnabled = saveEnabled, saving = saving)
            }
        }
    }
}

/** Ett steg i [StepwiseScreeningForm], i formulärets ordning. */
enum class ScreeningStep { FEELINGS, ENERGY, STRESS, SYMPTOMS }

/** Stegen i [StepwiseScreeningForm]: sidan och steglistan, delat mellan formuläret och dess knapprad. */
@Stable
class StepwiseScreeningState internal constructor(internal val pager: PagerState, private val list: () -> List<ScreeningStep>) {
    /** Stegen som visas, i ordning. */
    val steps: List<ScreeningStep> get() = list()
}

/** Stegen för ett formulär [withSymptoms] eller utan symptomsteget – känslor, energi, stress och sist symptomen. */
internal fun screeningSteps(withSymptoms: Boolean): List<ScreeningStep> = ScreeningStep.entries.filter { withSymptoms || it != ScreeningStep.SYMPTOMS }

/**
 * Steglistan läses om vid varje komposition: laddas symptomlistan sent (3 → 4 steg) står formuläret kvar på steget
 * användaren är på.
 */
@Composable
internal fun rememberStepwiseScreeningState(symptomOptions: List<Option>): StepwiseScreeningState {
    val steps by rememberUpdatedState(screeningSteps(symptomOptions.isNotEmpty()))
    val pager = rememberPagerState(pageCount = { steps.size })
    return remember(pager) { StepwiseScreeningState(pager) { steps } }
}
