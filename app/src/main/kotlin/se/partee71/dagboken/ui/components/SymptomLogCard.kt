package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.nonBlank
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.model.somatic
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Symptom med gradering 0–10 (AKT-6, SCR-2, SJ-3) – samma kort i aktivitet, mående och incheckning.
 * Ett kort med en [Foldout] (AKT-8): chips för symptomen ([options], stjärnmärkta först), och för
 * varje valt symptom ett [ValueSlider] där högre är sämre (grönt → rött). Alternativet [otherOptionId]
 * ("Övrigt") får ett textfält för egen beskrivning (`customText`). Summan av poängen (den somatiska
 * summan, beräknad i `:core`) står under. Ordningen i [scores] är den användaren valde i (DAT-6).
 */
@Composable
fun SymptomLogCard(
    options: List<Option>,
    scores: List<SymptomScore>,
    onScoresChange: (List<SymptomScore>) -> Unit,
    modifier: Modifier = Modifier,
    otherOptionId: String? = null,
    initiallyExpanded: Boolean = false,
    /** Summan som visas: postens `somatic` (med ett bevarat 3.x-värde, DAT-6); standard summan av [scores]. */
    somatic: Int = scores.somatic,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded || scores.isNotEmpty()) }
    val sorted = remember(options) { options.sortedWith(compareByDescending<Option> { it.favorite }.thenBy { it.sortOrder }) }
    val names = remember(options) { options.associate { it.id to it.name } }
    val sum = stringResource(R.string.symptom_sum_format, somatic)
    AppCard(modifier) {
        Foldout(stringResource(R.string.symptoms), expanded, { expanded = !expanded }, summary = sum.takeIf { scores.isNotEmpty() }) {
            ChipRow {
                sorted.forEach { option ->
                    val chosen = scores.any { it.optionId == option.id }
                    AppFilterChip(option.name, chosen, onClick = {
                        onScoresChange(if (chosen) scores.filterNot { it.optionId == option.id } else scores + SymptomScore(option.id, score = START_SCORE))
                    })
                }
            }
            if (scores.isEmpty()) {
                Text(stringResource(R.string.symptom_none), style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            scores.forEachIndexed { index, symptom ->
                val update = { changed: SymptomScore -> onScoresChange(scores.toMutableList().also { it[index] = changed }) }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    if (symptom.optionId == otherOptionId) {
                        AppTextField(symptom.customText.orEmpty(), { update(symptom.copy(customText = it.ifEmpty { null })) }, stringResource(R.string.symptom_other_describe))
                    }
                    ValueSlider(
                        label = symptom.customText.nonBlank() ?: names[symptom.optionId].orEmpty(),
                        // Utan poäng (från 3.x) visas 0 tills reglaget flyttas.
                        value = symptom.score ?: 0,
                        onValueChange = { update(symptom.copy(score = it)) },
                        higherIsBetter = false,
                    )
                }
            }
            if (scores.isNotEmpty()) Text(sum, style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ett nyvalt symptom börjar på 1 – valt betyder att det finns. */
private const val START_SCORE = 1
