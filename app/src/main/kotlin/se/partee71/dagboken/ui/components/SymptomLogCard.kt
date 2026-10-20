package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.model.somatic
import se.partee71.dagboken.ui.common.nonBlank
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.ui.components.AppCard
import se.partee71.ui.components.AppFilterChip
import se.partee71.ui.components.AppTextField
import se.partee71.ui.components.ChipRow
import se.partee71.ui.theme.Spacing

/**
 * Symptom med gradering 0–10 (AKT-6, SCR-2, SJ-3) – samma kort i aktivitet, mående och incheckning.
 * Ett kort med en [Foldout] (AKT-8): chips för symptomen ([options], stjärnmärkta först) med styrkan i
 * chipet, och ett [ValueSlider] (högre är sämre, grönt → rött) för det **aktiva** symptomet direkt under
 * chipsen: det nyss valda, eller det valda chip man tryckt på. Tryck på det aktiva avmarkerar det; då blir
 * det senast valda kvarvarande aktivt. Alternativet [otherOptionId] ("Övrigt") får ett textfält för egen
 * beskrivning (`customText`) ovanför reglaget när det är aktivt. Summan av poängen (den somatiska
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
    somatic: Int = scores.somatic
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded || scores.isNotEmpty()) }
    // Det aktiva symptomet – en befintlig logg öppnas med det första valda aktivt.
    var activeId by rememberSaveable { mutableStateOf(scores.firstOrNull()?.optionId) }
    val sorted = remember(options) { options.sortedWith(compareByDescending<Option> { it.favorite }.thenBy { it.sortOrder }) }
    val names = remember(options) { options.associate { it.id to it.name } }
    val sum = stringResource(R.string.symptom_sum_format, somatic)
    AppCard(modifier, contentColor = MaterialTheme.colorScheme.onSurface) {
        Foldout(stringResource(R.string.symptoms), expanded, { expanded = !expanded }, summary = sum.takeIf { scores.isNotEmpty() }) {
            // Ett reglage åt gången (SCR-2): det aktiva symptomets reglage står på fast plats under chipsen.
            val active = scores.firstOrNull { it.optionId == activeId } ?: scores.firstOrNull()
            ChipRow {
                sorted.forEach { option ->
                    val chosen = scores.firstOrNull { it.optionId == option.id }
                    val isActive = chosen != null && chosen.optionId == active?.optionId
                    AppFilterChip(
                        chosen?.customText.nonBlank() ?: option.name,
                        chosen != null,
                        onClick = {
                            when {
                                chosen == null -> {
                                    onScoresChange(scores + SymptomScore(option.id, score = START_SCORE))
                                    activeId = option.id
                                }

                                !isActive -> activeId = option.id

                                else -> {
                                    val remaining = scores.filterNot { it.optionId == option.id }
                                    onScoresChange(remaining)
                                    activeId = remaining.lastOrNull()?.optionId
                                }
                            }
                        },
                        // Utan poäng (från 3.x) visas 0 tills reglaget flyttas.
                        value = chosen?.let { (it.score ?: 0).toString() },
                        emphasized = isActive
                    )
                }
            }
            if (active == null) {
                Text(stringResource(R.string.symptom_none), style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val index = scores.indexOf(active)
                val update = { changed: SymptomScore -> onScoresChange(scores.toMutableList().also { it[index] = changed }) }
                key(active.optionId) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        if (active.optionId == otherOptionId) {
                            AppTextField(active.customText.orEmpty(), { update(active.copy(customText = it.ifEmpty { null })) }, stringResource(R.string.symptom_other_describe))
                        }
                        ValueSlider(
                            label = active.customText.nonBlank() ?: names[active.optionId].orEmpty(),
                            value = active.score ?: 0,
                            onValueChange = { update(active.copy(score = it)) },
                            higherIsBetter = false
                        )
                        Text(stringResource(R.string.symptom_tap_to_change), style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (scores.isNotEmpty()) Text(sum, style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ett nyvalt symptom börjar på 1 – valt betyder att det finns. */
private const val START_SCORE = 1
