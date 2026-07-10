package se.partee71.dagboken.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.data.datastore.SymptomOption

/**
 * Stegvis screeningformulär: energi → stress → (symptom) som svepbara steg-kort
 * med progression, i stället för ett långt scrollformulär. Symptomsteget visas
 * bara när det finns konfigurerade symptom. Delad komponent (regel 4) som
 * återanvänds av Idag-skärmens inline-screening; själva slidrarna och
 * symptomkortet är de befintliga [GradientSliderRow]/[SymptomLogCard].
 */
@Composable
fun StepwiseScreeningForm(
    energy: Int,
    onEnergyChange: (Int) -> Unit,
    stress: Int,
    onStressChange: (Int) -> Unit,
    symptomOptions: List<SymptomOption>,
    symptomScores: Map<String, Int>,
    onScoresChange: (Map<String, Int>) -> Unit,
    onToggleSymptomFavorite: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val hasSymptom = symptomOptions.isNotEmpty()
    val stepCount = if (hasSymptom) 3 else 2
    // Re-key on stepCount so a late-loading symptomlista (2 → 3 steg) rebuilds the
    // pager with the correct pageCount instead of a stale captured value.
    val pagerState = key(stepCount) { rememberPagerState(pageCount = { stepCount }) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text  = stringResource(R.string.format_screening_step, pagerState.currentPage + 1, stepCount),
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
        )

        HorizontalPager(
            state    = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .testTag("screening_step_pager"),
        ) { page ->
            Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                when (page) {
                    0 -> Box(Modifier.testTag("screening_step_energy")) {
                        GradientSliderRow(
                            label         = stringResource(R.string.label_energy),
                            emoji         = "⚡",
                            value         = energy.coerceIn(0, 10).toFloat(),
                            onValueChange = { onEnergyChange(it.toInt()) },
                            valueRange    = 0f..10f,
                            steps         = 9,
                            startLabel    = "0  😴",
                            endLabel      = "😊  10",
                        )
                    }
                    1 -> Box(Modifier.testTag("screening_step_stress")) {
                        GradientSliderRow(
                            label         = stringResource(R.string.label_stress),
                            emoji         = "😰",
                            value         = stress.toFloat(),
                            onValueChange = { onStressChange(it.toInt()) },
                            valueRange    = 0f..10f,
                            steps         = 9,
                            startLabel    = "0  😌",
                            endLabel      = "😰  10",
                            reverseColors = true,
                        )
                    }
                    else -> Box(Modifier.testTag("screening_step_symptom")) {
                        SymptomLogCard(
                            symptomOptions   = symptomOptions,
                            scores           = symptomScores,
                            onScoresChange   = onScoresChange,
                            onToggleFavorite = onToggleSymptomFavorite,
                        )
                    }
                }
            }
        }

        // Stegindikator-prickar
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(stepCount) { i ->
                val selected = i == pagerState.currentPage
                val width by animateDpAsState(if (selected) 20.dp else 8.dp, label = "step_dot_$i")
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (selected) cs.primary else cs.surfaceVariant),
                )
            }
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            if (pagerState.currentPage > 0) {
                OutlinedButton(
                    onClick  = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    modifier = Modifier.testTag("screening_prev"),
                ) { Text(stringResource(R.string.screening_step_prev)) }
            }
            Spacer(Modifier.weight(1f))
            if (pagerState.currentPage < stepCount - 1) {
                Button(
                    onClick  = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.testTag("screening_next"),
                ) { Text(stringResource(R.string.screening_step_next)) }
            } else {
                SaveButton(
                    enabled      = saveEnabled,
                    onClick      = onSave,
                    modifier     = Modifier.testTag("screening_save"),
                    fillMaxWidth = false,
                )
            }
        }
    }
}
