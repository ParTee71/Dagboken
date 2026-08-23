package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.usecase.SymptomUtils
import se.partee71.dagboken.ui.theme.energyColor
import se.partee71.dagboken.ui.theme.energyLabel

/**
 * Postkort för en loggad aktivitet eller screening (AKT-10). Följer kortstandarden
 * via [DagbokenEntryCard] (NFR-15/NFR-16): tryck redigerar posten, chevron-knappen
 * fäller ut symptomen, långtryck och `⋮` ger samma meny och svep vänster raderar.
 * Energifärgen används som statusaccent.
 */
@Composable
fun AktivitetCard(
    aktivitet: Aktivitet,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    noteText: String = "",
) {
    val symptoms = remember(aktivitet.symptom) { SymptomUtils.decode(aktivitet.symptom) }
    val cs = MaterialTheme.colorScheme
    val eLabel = energyLabel(aktivitet.energy)

    val symptomDetails: (@Composable ColumnScope.() -> Unit)? =
        if (symptoms.isEmpty()) {
            null
        } else {
            {
                symptoms.entries.forEach { (name, score) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text     = name,
                            style    = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text  = score.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.primary,
                        )
                    }
                }
            }
        }

    DagbokenEntryCard(
        title           = aktivitet.aktivitet,
        onClick         = onEdit,
        modifier        = modifier,
        subtitle        = "${aktivitet.tid}  •  ⚡ $eLabel  •  😰 ${aktivitet.stress}",
        accentColor     = energyColor(aktivitet.energy, cs),
        noteText        = noteText,
        expandedContent = symptomDetails,
        actions         = listOf(
            EntryAction(
                label   = stringResource(R.string.edit),
                icon    = Icons.Default.Edit,
                onClick = onEdit,
            ),
        ),
        onDelete        = onDelete,
    )
}
