package se.kodhast.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.kodhast.dagboken.R
import se.kodhast.dagboken.core.engine.TypeChoices
import se.kodhast.ui.components.ChoiceChips
import se.kodhast.ui.components.LabeledGroup

/**
 * Typval ur en alternativlista – typen i aktivitets- och händelseformuläret (AKT-1, SET-9), en gång för alla: bara
 * [TypeChoices] (domänen, räknad i `:core` av `typeChoices`) på android-ui:s `ChoiceChips(more)` – stjärnmärkta typer
 * som chips och övriga under "Fler typer". [otherLabel] är namnet på "Övrigt" när det inte finns i listan
 * ([TypeChoices.other]). Ett chip och ett val i menyn sätter samma typ ([selected]); [error] står under (typ krävs).
 */
@Composable
fun TypeChoiceField(
    choices: TypeChoices,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    otherLabel: String? = null
) {
    val more = choices.more.map { it.id } + listOfNotNull(choices.other)
    val names = (choices.favorites + choices.more).associate { it.id to it.name } + listOfNotNull(choices.other?.let { it to otherLabel.orEmpty() })
    LabeledGroup(
        stringResource(R.string.entry_type),
        modifier,
        helper = if (choices.isEmpty) stringResource(R.string.entry_type_none) else null,
        error = error
    ) {
        // Utan några typer finns bara hjälptexten – en tom chipsrad skulle lägga till luft.
        if (!choices.isEmpty) {
            ChoiceChips(
                options = choices.favorites.map { it.id },
                selected = selected,
                onSelect = onSelect,
                label = { names[it].orEmpty() },
                more = more,
                moreLabel = stringResource(R.string.entry_more_types),
                morePickLabel = stringResource(R.string.entry_pick_type)
            )
        }
    }
}
