package se.partee71.dagboken.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.data.datastore.SymptomOption

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SymptomLogCard(
    symptomOptions: List<SymptomOption>,
    scores: Map<String, Int>,
    onScoresChange: (Map<String, Int>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(symptomOptions) {
        symptomOptions.sortedWith(
            compareByDescending<SymptomOption> { it.isFavorite }.thenBy { it.name }
        )
    }

    var foldedOut by remember { mutableStateOf(false) }
    var expanded  by remember { mutableStateOf(false) }
    var pending   by remember { mutableStateOf<String?>(null) }
    var strength  by remember { mutableIntStateOf(1) }

    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(foldedOut) {
        if (foldedOut) {
            delay(300) // let the expand animation finish
            bringIntoView.bringIntoView()
        }
    }

    // Exclude already-logged symptoms unless they are the current edit target
    val available = remember(sorted, scores, pending) {
        sorted.filter { opt -> opt.name !in scores.keys || opt.name == pending }
    }

    DagbokenCard(
        modifier       = modifier.bringIntoViewRequester(bringIntoView),
        contentPadding = PaddingValues(0.dp),
    ) {
        Foldout(
            title    = stringResource(R.string.label_symptom),
            expanded = foldedOut,
            onToggle = { foldedOut = !foldedOut },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    ExposedDropdownMenuBox(
                        expanded         = expanded,
                        onExpandedChange = { expanded = it },
                        modifier         = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value         = pending ?: "",
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text(stringResource(R.string.symptom_pick_label)) },
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier      = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            if (available.isEmpty()) {
                                DropdownMenuItem(
                                    text    = { Text(stringResource(R.string.symptom_all_logged)) },
                                    onClick = { expanded = false },
                                    enabled = false,
                                )
                            } else {
                                available.forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment     = Alignment.CenterVertically,
                                            ) {
                                                if (opt.isFavorite) {
                                                    Icon(
                                                        Icons.Filled.Star, null,
                                                        tint     = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                                Text(opt.name)
                                            }
                                        },
                                        onClick = {
                                            pending  = opt.name
                                            strength = 1
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            pending?.let { name ->
                                onScoresChange(scores + (name to strength))
                                pending  = null
                                strength = 1
                            }
                        },
                        enabled = pending != null && strength > 0,
                    ) {
                        Text(stringResource(R.string.add))
                    }
                }

                GradientSliderRow(
                    label         = pending ?: "",
                    value         = strength.toFloat(),
                    onValueChange = { strength = it.toInt() },
                    valueRange    = 0f..10f,
                    steps         = 9,
                    reverseColors = true,
                    enabled       = pending != null,
                )

                if (scores.isNotEmpty()) {
                    HorizontalDivider()
                    val favoriteNames = remember(sorted) { sorted.filter { it.isFavorite }.map { it.name }.toSet() }
                    val order = sorted.map { it.name }
                    scores.entries
                        .sortedBy { (name, _) ->
                            order.indexOf(name).takeIf { it >= 0 } ?: Int.MAX_VALUE
                        }
                        .forEach { (name, score) ->
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick  = { onToggleFavorite(name) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = stringResource(R.string.symptom_favorite),
                                        tint     = if (name in favoriteNames) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    text     = name,
                                    style    = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text  = "$score/10",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                IconButton(
                                    onClick  = { pending = name; strength = score },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.edit),
                                        modifier           = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(
                                    onClick  = {
                                        if (pending == name) { pending = null; strength = 0 }
                                        onScoresChange(scores - name)
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.delete),
                                        modifier           = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                }
                // bottom padding inside foldout
            }
        }
    }
}
