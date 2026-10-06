package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.TypeChoices

/**
 * Typval ur en alternativlista – typen i aktivitets- och händelseformuläret (AKT-1, SET-9), en gång för alla: stjärnmärkta typer som
 * [ChoiceChips] och övriga under "Fler typer" (samma fältyta som datum och tid, med listan som meny). [choices]
 * räknas i `:core` (`typeChoices`); [otherLabel] är namnet på "Övrigt" när det inte finns i listan
 * ([TypeChoices.other]). Ett chip och ett val i menyn sätter samma typ ([selected]); [error] står under (typ krävs).
 */
@Composable
fun TypeChoiceField(
    choices: TypeChoices,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    otherLabel: String? = null,
) {
    val more = choices.more.map { it.id to it.name } + listOfNotNull(choices.other?.let { it to otherLabel.orEmpty() })
    val favoriteNames = choices.favorites.associate { it.id to it.name }
    LabeledGroup(
        stringResource(R.string.entry_type),
        modifier,
        helper = if (choices.isEmpty) stringResource(R.string.entry_type_none) else null,
        error = error,
    ) {
        if (favoriteNames.isNotEmpty()) ChoiceChips(favoriteNames.keys.toList(), selected, onSelect, label = { favoriteNames[it].orEmpty() })
        if (more.isNotEmpty()) {
            var open by rememberSaveable { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                PickerField(
                    label = stringResource(R.string.entry_more_types),
                    shown = more.firstOrNull { it.first == selected }?.second.orEmpty(),
                    pickLabel = stringResource(R.string.entry_pick_type),
                    icon = R.drawable.ic_expand_more,
                    onPick = { open = true },
                )
                AppMenuPopup(more.map { (id, name) -> AppMenuItem(name, { onSelect(id) }) }, expanded = open, onDismiss = { open = false })
            }
        }
    }
}
