package se.partee71.dagboken.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.model.somatic
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Mående i steg (HEM-5, SCR-1, SCR-2): energi → stress → symptom, ett steg i taget med "Steg 1 av 3",
 * stegprickar och Föregående/Nästa; sista steget har Spara. Symptomsteget finns bara när det finns
 * [symptomOptions]. Används i arket bakom "Logga nu"; reglagen är [ValueSlider] (energi: högre är
 * bättre, stress: högre är sämre) och symptomen [SymptomLogCard]. Stegen går också att svepa mellan.
 */
@Composable
fun StepwiseScreeningForm(
    energy: Int,
    onEnergyChange: (Int) -> Unit,
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
) {
    val steps = if (symptomOptions.isEmpty()) 2 else 3
    // Antalet sidor läses om vid varje komposition: laddas symptomlistan sent (2 → 3 steg) står
    // formuläret kvar på steget användaren är på.
    val pager = rememberPagerState(pageCount = { steps })
    val scope = rememberCoroutineScope()
    val go = { page: Int -> scope.launch { pager.animateScrollToPage(page) }; Unit }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            stringResource(R.string.step_format, pager.currentPage + 1, steps),
            style = AppTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = Spacing.l, verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> ValueSlider(stringResource(R.string.energy), energy, onEnergyChange)
                1 -> ValueSlider(stringResource(R.string.stress), stress, onStressChange, higherIsBetter = false)
                else -> SymptomLogCard(symptomOptions, symptoms, onSymptomsChange, otherOptionId = otherOptionId, initiallyExpanded = true, somatic = somatic)
            }
        }
        StepDots(steps, pager.currentPage)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (pager.currentPage > 0) AppButton(stringResource(R.string.previous), { go(pager.currentPage - 1) }, variant = ButtonVariant.Text)
            Spacer(Modifier.weight(1f))
            if (pager.currentPage < steps - 1) {
                AppButton(stringResource(R.string.next), { go(pager.currentPage + 1) }, variant = ButtonVariant.Secondary)
            } else {
                AppButton(stringResource(R.string.save), onSave, enabled = saveEnabled, loading = saving)
            }
        }
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
