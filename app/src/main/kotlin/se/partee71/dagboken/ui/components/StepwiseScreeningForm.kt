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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.BodySensationCatalog
import se.partee71.dagboken.core.model.ContextTagCatalog
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.model.TagCatalog
import se.partee71.dagboken.core.model.somatic
import se.partee71.dagboken.ui.common.tagName
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Mående i steg (HEM-5, SCR-1, SCR-2, SCR-7, SCR-9): energi → känslor → kropp och sammanhang → stress → symptom, ett steg i
 * taget med "Steg 1 av 5", stegprickar och Föregående/Nästa; sista steget har Spara. Stegen är en lista ([ScreeningStep], ur
 * [rememberStepwiseScreeningState]): symptomsteget finns bara när det finns [symptomOptions], och formuläret kan börja
 * på ett annat steg än energin (`initialStep`). Används i arket bakom "Logga nu"; reglagen är [ValueSlider] (energi:
 * högre är bättre, stress: högre är sämre), känslorna [FeelingPicker] (inget val är ett giltigt svar) och symptomen
 * [SymptomLogCard]. Kropp och sammanhang är två [MultiChoiceChips] över [BodySensationCatalog] och [ContextTagCatalog]
 * med hjälptexten "Frivilligt – Nästa går alltid"; vad ett tryck väljer bestämmer katalogens [TagCatalog.toggle] (en
 * oförändrad lista – ingen ändring – anropar inget). Stegen går också att svepa mellan.
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
    body: List<String>,
    onBodyChange: (List<String>) -> Unit,
    context: List<String>,
    onContextChange: (List<String>) -> Unit,
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
    navigation: Boolean = true
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
                ScreeningStep.FEELINGS -> FeelingPicker(feelings, onFeelingsChange)
                ScreeningStep.BODY_CONTEXT -> BodyContextStep(body, onBodyChange, context, onContextChange)
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
enum class ScreeningStep { ENERGY, FEELINGS, BODY_CONTEXT, STRESS, SYMPTOMS }

/** Stegen i [StepwiseScreeningForm]: sidan och steglistan, delat mellan formuläret och dess knapprad. */
@Stable
class StepwiseScreeningState internal constructor(internal val pager: PagerState, private val list: () -> List<ScreeningStep>) {
    /** Stegen som visas, i ordning. */
    val steps: List<ScreeningStep> get() = list()
}

/** Stegen för ett formulär [withSymptoms] eller utan symptomsteget – energi, känslor, kropp och sammanhang, stress och sist symptomen. */
internal fun screeningSteps(withSymptoms: Boolean): List<ScreeningStep> = ScreeningStep.entries.filter { withSymptoms || it != ScreeningStep.SYMPTOMS }

/**
 * Steglistan läses om vid varje komposition: laddas symptomlistan sent (4 → 5 steg) står formuläret kvar på steget
 * användaren är på. Formuläret börjar på [initialStep] (finns det inte – symptomen utan alternativ – på första steget).
 */
@Composable
internal fun rememberStepwiseScreeningState(
    symptomOptions: List<Option>,
    initialStep: ScreeningStep = ScreeningStep.ENERGY
): StepwiseScreeningState {
    val steps by rememberUpdatedState(screeningSteps(symptomOptions.isNotEmpty()))
    val pager = rememberPagerState(initialPage = steps.indexOf(initialStep).coerceAtLeast(0), pageCount = { steps.size })
    return remember(pager) { StepwiseScreeningState(pager) { steps } }
}

/** Föregående/Nästa – och Spara på sista steget – för [StepwiseScreeningForm], t.ex. i `AppBottomSheet(footer)`. */
@Composable
internal fun RowScope.StepwiseScreeningNavigation(state: StepwiseScreeningState, onSave: () -> Unit, saveEnabled: Boolean = true, saving: Boolean = false) {
    val pager = state.pager
    val scope = rememberCoroutineScope()
    val go = { page: Int ->
        scope.launch { pager.animateScrollToPage(page) }
        Unit
    }
    if (pager.currentPage > 0) AppButton(stringResource(R.string.previous), { go(pager.currentPage - 1) }, variant = ButtonVariant.Text)
    Spacer(Modifier.weight(1f))
    if (pager.currentPage < state.steps.lastIndex) {
        AppButton(stringResource(R.string.next), { go(pager.currentPage + 1) }, variant = ButtonVariant.Secondary)
    } else {
        AppButton(stringResource(R.string.save), onSave, enabled = saveEnabled, loading = saving)
    }
}

/** Kropp och sammanhang (SCR-9): två frivilliga flerval; växlingen – med "Ingenting särskilt" – är katalogens. */
@Composable
private fun BodyContextStep(body: List<String>, onBodyChange: (List<String>) -> Unit, context: List<String>, onContextChange: (List<String>) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        HelperText(stringResource(R.string.body_context_optional))
        TagGroup(stringResource(R.string.body_title), BodySensationCatalog, body, onBodyChange)
        TagGroup(stringResource(R.string.context_title), ContextTagCatalog, context, onContextChange)
    }
}

@Composable
private fun TagGroup(title: String, catalog: TagCatalog, selected: List<String>, onChange: (List<String>) -> Unit) {
    LabeledGroup(title) {
        MultiChoiceChips(
            catalog.tokens,
            selected,
            onToggle = { token -> catalog.toggle(selected, token).takeIf { it != selected }?.let(onChange) },
            label = { token -> tagName(catalog, token)?.let { stringResource(it) } ?: token }
        )
    }
}

/** Stegprickarna: den aktuella fjädrar ut till ett streck (DSN-4); läses inte – texten ovanför säger samma sak. */
@Composable
private fun StepDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally)) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) DOT_ACTIVE else DOT, MaterialTheme.motionScheme.fastSpatialSpec(), label = "stegprick")
            Box(Modifier.size(width, DOT).background(if (active) MaterialTheme.colorScheme.primary else AppColors.extended.track, AppShapes.pill))
        }
    }
}

private val DOT = 8.dp
private val DOT_ACTIVE = 20.dp
