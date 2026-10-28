package se.partee71.dagboken.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.model.somatic
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.TRACK_HEIGHT
import se.partee71.ui.components.AppButton
import se.partee71.ui.components.ButtonVariant
import se.partee71.ui.theme.Spacing

/**
 * Mående i steg (HEM-5, SCR-1, SCR-2, SCR-7): känslor → energi → stress → symptom, ett steg i taget med "Steg 1 av 4",
 * stegprickar och Föregående/Nästa; sista steget har Spara. Stegen är en lista ([ScreeningStep], ur
 * [rememberStepwiseScreeningState]): symptomsteget finns bara när det finns [symptomOptions]; formuläret börjar alltid på
 * första steget, känslorna. Används i arket bakom "Logga nu"; reglagen är [ValueSlider] (energi:
 * högre är bättre, stress: högre är sämre), känslorna [FeelingPicker] (inget val är ett giltigt svar) och symptomen
 * [SymptomLogCard]. Stegen går också att svepa mellan.
 *
 * [navigation] = false: knapparna ritas inte här utan av [StepwiseScreeningNavigation] med samma [state] – i
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            stringResource(R.string.step_format, pager.currentPage + 1, steps.size),
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
        StepDots(steps.size, pager.currentPage)
        if (navigation) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                StepwiseScreeningNavigation(state, onSave, saveEnabled, saving)
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

/**
 * Föregående/Nästa – och Spara på sista steget – för [StepwiseScreeningForm], t.ex. i `AppBottomSheet(footer)`.
 * [done] = klart-läget (NFR-21): Spara byts mot en fylld knapp med bock och [doneText], Föregående är nedtonad, och
 * [doneAnnouncement] läses upp av skärmläsare (live region). Default `false` = vanliga knappraden.
 */
@Composable
internal fun RowScope.StepwiseScreeningNavigation(
    state: StepwiseScreeningState,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    saving: Boolean = false,
    done: Boolean = false,
    doneText: String = stringResource(R.string.saved),
    doneAnnouncement: String = doneText
) {
    val pager = state.pager
    val scope = rememberCoroutineScope()
    val go = { page: Int ->
        scope.launch { pager.animateScrollToPage(page) }
        Unit
    }
    if (pager.currentPage > 0) AppButton(stringResource(R.string.previous), { go(pager.currentPage - 1) }, variant = ButtonVariant.Text, enabled = !done)
    Spacer(Modifier.weight(1f))
    // En och samma live region över lägena: i klart-läget bär den "Sparat" (text) och bekräftelsen (stateDescription),
    // och knappen under är ren status – ingen knapp för skärmläsare.
    Box(
        Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            if (done) {
                text = AnnotatedString(doneText)
                stateDescription = doneAnnouncement
            }
        }
    ) {
        if (done) {
            AppButton(doneText, {}, Modifier.clearAndSetSemantics {}, icon = R.drawable.ic_check)
        } else if (pager.currentPage < state.steps.lastIndex) {
            AppButton(stringResource(R.string.next), { go(pager.currentPage + 1) }, variant = ButtonVariant.Secondary)
        } else {
            AppButton(stringResource(R.string.save), onSave, enabled = saveEnabled, loading = saving)
        }
    }
}

/** Stegprickarna: den aktuella fjädrar ut till ett streck (DSN-4); läses inte – texten ovanför säger samma sak. */
@Composable
private fun StepDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally)) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) DOT_ACTIVE else TRACK_HEIGHT, MaterialTheme.motionScheme.fastSpatialSpec(), label = "stegprick")
            Box(Modifier.size(width, TRACK_HEIGHT).background(if (active) MaterialTheme.colorScheme.primary else AppColors.extended.track, AppShapes.pill))
        }
    }
}

private val DOT_ACTIVE = 20.dp
